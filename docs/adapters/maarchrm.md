# Adapter `maarchrm` — Maarch RM 2.x

Maître = Maarch RM (framework laabs), lu par son API JSON. Vérifié contre Maarch RM 2.7.

## Configuration

| Variable | Rôle | Défaut |
|---|---|---|
| `JOKKORM_MAARCHRM_BASE_URL` | URL racine. Sans elle, l'adapter n'existe pas. | — |
| `JOKKORM_MAARCHRM_SERVICE_CLIENT_TOKEN` | Valeur `SERVICE_CLIENT_TOKEN` du vhost Maarch (`data/maarchRM/conf/vhost.conf`). | `service` |
| `JOKKORM_MAARCHRM_AUTH_MODE` | `basic` (utilisateur) ou `service-token` (compte de service). | `basic` |
| `JOKKORM_MAARCHRM_LOGIN` / `_PASSWORD` | Mode basic. | — |
| `JOKKORM_MAARCHRM_SERVICE_TOKEN` | Mode service-token : jeton du compte de service, envoyé en cookie `LAABS-AUTH`. | — |
| `JOKKORM_MAARCHRM_TIMEOUT_MS` | Délai max d'un appel. | `10000` |

Puis router un tenant vers ce maître : `JOKKORM_MASTER_TENANTS_<TENANT>=maarchrm`.

## Pièges Maarch RM

- **Le User-Agent choisit l'API.** Maarch ne sert son API JSON que si le User-Agent contient le `SERVICE_CLIENT_TOKEN`. Sinon il répond **200 avec la page HTML** de son interface. L'adapter traite cette réponse comme une panne (`MasterUnreachableException`), jamais comme « cote inconnue ».
- **Visibilité par rattachement.** Maarch filtre les archives selon la position du compte dans les organisations. Un compte sans position, comme `superadmin` en démo, voit 0 archive. Au démarrage, l'adapter compte les archives visibles et prévient si c'est 0.
- **Texte cherché concaténé.** Maarch insère `archiveName` dans sa requête sans paramètre lié. L'adapter retire `' " * \ % ;` et les caractères de contrôle.
- **Erreur déguisée en succès.** Une exception laabs sans code HTTP repart en **200 avec `{}`**, et le vrai message est dans l'en-tête `X-Laabs-Exception`. Exemple : un compte créé sans `emailAddress`, colonne obligatoire. L'adapter lit cet en-tête à chaque appel. En écriture, il en fait un refus durable avec le message ; en lecture, une panne, jamais « cote inconnue ».
- **Ne pas régénérer le jeton des comptes de service existants** (`Systeme`…) : les traitements de Maarch peuvent en dépendre. Créer un compte dédié JokkoRM.

## Correspondances

Cote partagée (ADR-0004) = `archiverArchiveId`, l'identifiant attribué par le service d'archives.

| JokkoRM | Maarch RM |
|---|---|
| `title` | `archiveName` |
| `classificationPath` | `originatorOrgRegNumber/archivalProfileReference` (ex. `DIP/DOSIP`) |
| `duaYears` | `retentionDuration` ISO 8601 (`P90Y` → 90) |
| `finalFate` | `finalDisposition` : `destruction` → DESTRUCTION, `preservation` → CONSERVATION (pas d'équivalent du versement) |
| `legalHold` | `status == "frozen"` |
| recherche : texte, service, typologie, dates, cote | `archiveName`, `originatorOrgRegNumber`, `profileReference`, `originatingStartDate/EndDate`, `archiverArchiveId` |

Endpoints utilisés : `GET /recordsManagement/archives` (recherche, par cote) et `GET /recordsManagement/archives/Count` (contrôle au démarrage).

## Entrée des archives dans JokkoRM

Maarch RM ne prévient pas JokkoRM quand il attribue une cote (pas de webhook). L'archiviste cherche dans Maarch depuis la page **Archives**, puis importe celles qui ont du papier : `POST /api/v1/master/import`, idempotent et audité. Une archive inconnue de Maarch donne 404, et Maarch injoignable donne 502.

## Tranche 2a — l'état physique affiché dans Maarch RM (livrée)

Pattern 03 : l'emplacement et les mouvements appartiennent à JokkoRM ; Maarch les **affiche**.

- **Quand** : à chaque geste physique (rangement d'un dossier, rangement ou déplacement d'une
  boîte — toutes ses archives —, sortie et retour de prêt, mouvement enregistré), JokkoRM met
  la cote dans une file d'envoi (`master_sync`), dans la même transaction que le geste.
- **Envoi** : un travailleur (toutes les 5 s, `JOKKORM_MASTER_SYNC_WORKER_ENABLED`) recalcule
  l'état **actuel** et l'écrit dans Maarch. Panne → relances (30 s, 2 min, 10 min, 30 min, 2 h,
  puis 6 h) ; refus durable → « refusé » avec le message Maarch, jusqu'au prochain geste.
  Une panne de Maarch ne bloque jamais un rangement.
- **Ce qui est écrit** : une ligne dans le champ de description `localisation_physique`
  (`JOKKORM_MAARCHRM_LOCATION_FIELD`), ex. « Salle d'archives A (SALLE-A) · Épi E1 · rang 3 ·
  face A · Travée T03 · Tablette 2 · Emplacement 01 — en magasin (JokkoRM, 23/11/2026 14:02) ».
  **Jamais de code-barres** (pattern 08 : tout utilisateur Maarch qui voit l'archive voit ce texte).
- **Comment** : relecture de l'archive juste avant l'écriture, puis `PUT
  /recordsManagement/archives/Metadata` en renvoyant cote, `originatorArchiveId`, titre, date et
  description **inchangés**, plus le seul champ de localisation. Maarch remplace tout ce qu'on lui
  envoie : une modification faite dans Maarch entre la relecture et l'écriture (quelques ms)
  serait perdue.
- **Suivi** : `GET /api/v1/master/sync?cote=` et la fiche de l'archive (« Affiché dans Maarch RM »).

### Préparation de Maarch RM (une fois)

Script : `~/maarchrm-test/jokkorm-setup-2a.sh` (sauvegarde de la base Maarch prise avant).

1. Champ de description `localisation_physique` (« Localisation physique (JokkoRM) »), type texte,
   **non immuable** (un champ immuable bloquerait aussi JokkoRM). Les profils `DOSIP` et `ETAC`
   acceptent les index ajoutés (`acceptUserIndex`), sinon l'ajouter au profil.
2. Compte de service `jokkorm`, rattaché au service d'archives GIC, avec 4 droits :
   `recordsManagement/archives/read`, `recordsManagement/archives/readCount`,
   `recordsManagement/archive/readMetadata*`, `recordsManagement/archives/updateMetadata`.
   Ses écritures apparaissent sous son nom dans le journal Maarch.
3. Jeton du compte → `JOKKORM_MAARCHRM_AUTH_MODE=service-token` +
   `JOKKORM_MAARCHRM_SERVICE_TOKEN`.

## Tranche 2b (à étudier)

- **Droits unifiés** (pattern 11) : Maarch n'offre pas d'appel « cet utilisateur peut-il voir
  cette archive ? » et agir au nom d'un utilisateur demande ses identifiants ou un vrai SSO.
  Étude préalable avant tout code.
- **Destruction** : attestation jointe à l'archive Maarch, avec la phase 5G (ordre exact
  attestation / élimination côté Maarch à fixer avec le bordereau d'élimination).
