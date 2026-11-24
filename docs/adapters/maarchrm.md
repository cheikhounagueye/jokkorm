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
| `JOKKORM_MAARCHRM_LOCATION_FIELD` | Champ de description qui affiche l'état physique (tranche 2a). | `localisation_physique` |
| `JOKKORM_MAARCHRM_PUBLIC_URL` | URL de Maarch vue par les navigateurs des agents (bouton « Ouvrir dans Maarch RM »). À préciser si JokkoRM joint Maarch par une adresse interne. | = `BASE_URL` |
| `JOKKORM_MAARCHRM_CONNECTOR_ACCOUNT` | Compte Maarch de JokkoRM, pour reconnaître ses propres écritures dans le journal. | login (basic) ou `jokkorm` |
| `JOKKORM_MASTER_WATCH_ENABLED` / `_DELAY_MS` | Veilleur du journal : actif, intervalle. | `true` / `60000` |
| `JOKKORM_MASTER_WATCH_INITIAL_LOOKBACK_HOURS` | Au tout premier passage, profondeur relue dans le journal. | `24` |

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

## Ce que JokkoRM garde, et le bouton « Ouvrir dans Maarch RM »

JokkoRM ne garde que la cote et la partie physique. Titre, plan de classement, DUA, sort final et
gel sont **lus à chaque ouverture** de la fiche, jamais stockés. La description (nom complet,
matricule, adresse…) n'est ni lue ni copiée : une seule version de chaque information, et pas de
copie de données personnelles.

Pour la voir, la fiche JokkoRM propose **« Ouvrir dans Maarch RM »**, qui ouvre dans un nouvel
onglet `{PUBLIC_URL}/recordsManagement/archiveDescription/{archiveId}`. C'est Maarch qui décide :

- **Vérifié le 2026-11-23** :
  - un agent de la DRH connecté à Maarch voit la fiche ;
  - un agent du Marketing voit « Accès refusé » ;
  - sans session Maarch, l'écran de connexion s'affiche, puis l'accueil : il faut recliquer sur
    le bouton.
- `archiveId` est l'identifiant interne de Maarch, encodé dans l'adresse. Le lien n'apparaît que
  s'il commence par `http(s)://`.

## Entrée des archives dans JokkoRM

Maarch RM ne prévient pas JokkoRM quand il attribue une cote (pas de webhook). Deux chemins :

- la page **À importer** : le veilleur y dépose chaque nouvelle archive de Maarch, à décider ou
  importée seule selon la règle de son profil (voir « Liste à importer » plus bas) ;
- la recherche de la page **Archives**, pour une archive plus ancienne : `POST /api/v1/master/import`,
  idempotent et audité. Une archive inconnue de Maarch donne 404, et Maarch injoignable donne 502.

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
- **Vérifié en réel le 2026-11-23** (Maarch RM 2.7, compte de service `jokkorm`) :
  - la boîte d'une archive DOSIP déplacée est envoyée 2 s plus tard, au premier essai ;
  - le champ s'affiche dans la description de l'archive côté Maarch ;
  - l'empreinte des autres champs est inchangée, `originatorArchiveId` et la date comprises ;
  - le journal Maarch attribue `updateMetadata` à `jokkorm`.

### Préparation de Maarch RM (une fois)

Script : `~/maarchrm-test/jokkorm-setup-2a.sh` (sauvegarde de la base Maarch prise avant).

1. Champ de description `localisation_physique` (« Localisation physique (JokkoRM) »), type texte,
   **non immuable** (un champ immuable bloquerait aussi JokkoRM). Les profils `DOSIP` et `ETAC`
   acceptent les index ajoutés (`acceptUserIndex`), sinon l'ajouter au profil.
2. Compte de service `jokkorm` (adresse e-mail obligatoire, ici technique), rattaché au service d'archives GIC, avec 4 droits :
   `recordsManagement/archives/read`, `recordsManagement/archives/readCount`,
   `recordsManagement/archive/readMetadata*`, `recordsManagement/archives/updateMetadata`.
   Ses écritures apparaissent sous son nom dans le journal Maarch.
3. Jeton du compte → `JOKKORM_MAARCHRM_AUTH_MODE=service-token` +
   `JOKKORM_MAARCHRM_SERVICE_TOKEN`.
4. Veilleur : `~/maarchrm-test/jokkorm-setup-veilleur.sh` ajoute un 5ᵉ droit,
   `lifeCycle/event/readSearch` (lire le journal).
   - Maarch **efface tous les droits** d'un compte avant de réécrire sa liste. Le script écrit donc
     la liste complète en clair, après une sauvegarde des comptes et des droits.
   - Il vérifie ensuite que le jeton marche toujours.

## Veilleur du journal (étape 1)

Maarch ne prévient personne : JokkoRM lit son journal du cycle de vie **chaque minute**
(`GET /lifeCycle/event/Search`, droit `lifeCycle/event/readSearch` donné par
`~/maarchrm-test/jokkorm-setup-veilleur.sh`). Écran : **Signalements**.

| Événement Maarch | Réaction de JokkoRM |
|---|---|
| `metadataModification` par un autre compte que JokkoRM | relit l'archive ; si l'emplacement affiché n'est plus celui envoyé (aux espaces près), il est **rétabli** et un signalement « Emplacement rétabli » est créé (qui, quand) |
| `metadataModification` par JokkoRM lui-même, valeur différente | signalement « à vérifier », **jamais de réécriture** (pas de boucle) |
| `freeze` | l'archive passe à « gelée » ; signalement « demander le retour » si la boîte est en prêt, « prévenir le demandeur » si un prêt est demandé |
| `unfreeze` | l'archive redevient « en magasin » ; signalement d'information |

- **Rien n'est perdu.** Le curseur est l'horodatage **Maarch** du dernier événement traité, et
  la lecture suivante repart 2 minutes avant. Maarch injoignable, ou droit manquant : le
  passage est marqué « en erreur » avec la raison, et le curseur ne bouge pas.
- **Rien n'est rejoué.** Un signalement est unique par événement Maarch et par type. C'est aussi
  le marqueur « déjà traité » : une relecture ne refait ni l'action, ni la ligne d'audit.
- **Traçabilité croisée.** Chaque action est inscrite dans l'audit de JokkoRM au nom de
  `veilleur`, avec l'identifiant de l'événement Maarch (`evenement-maitre=…`).
- **Données personnelles.** JokkoRM ne lit que le type, l'archive, l'auteur et la date de
  l'événement, jamais `eventInfo` ni `description`. Il ne garde pas non plus la valeur tapée
  dans Maarch, seulement « modifié » ou « effacé ».
- **Vérifié en réel le 2026-11-23**, en lecture seule (`MaarchRmLiveReadTest`, variables
  `MAARCHRM_LIVE_*`) :
  - la lecture par pages de 2 retrouve exactement les 5 modifications d'une seule grande page ;
  - sur les 4 modifications de l'archive `FR094080_2026_AE0002`, dont le renommage par un
    agent de la DRH, aucun faux écart.
- **Test d'acceptation réussi le 2026-11-24**, sur Maarch réel, compte de service `jokkorm` :
  - un agent de la DRH a modifié le champ à la main à 08:56:40 ;
  - JokkoRM l'a rétabli à 08:56:55, soit 15 s plus tard ;
  - un seul signalement a été créé, à son nom, et aucun faux « à vérifier » pour la réécriture
    de JokkoRM.
- **Pourquoi le champ n'est pas verrouillé** : le seul verrou de Maarch est l'option « immuable »
  du profil d'archivage. Elle grise le champ à l'écran, mais elle le bloque aussi côté serveur
  pour **tous** les comptes, JokkoRM compris (`archiveModificationTrait` : « Attempt to modify
  readonly field(s) »). Maarch afficherait alors un emplacement figé, donc faux. Le griser
  seulement pour les agents demanderait de modifier les écrans de Maarch.
- **Limites connues** :
  - le journal ne dit pas quel champ a changé, et JokkoRM lit l'état actuel de l'archive. Pour une
    même archive, seule la dernière modification de la minute est donc évaluée. Si deux personnes
    modifient la même archive dans la minute, le signalement nomme la dernière ;
  - un gel posé avant le début de la veille n'est pas signalé : le rapprochement hebdomadaire
    (étape 4) le couvrira ;
  - au-delà de 20 000 événements d'un même type à rattraper d'un coup, le veilleur s'arrête en
    erreur ;
  - un gel levé remet l'archive « en magasin » même si elle n'avait pas encore de boîte.

## Liste « à importer » et import automatique par profil (étape 2)

Règle n° 4 : JokkoRM ne copie pas le catalogue de Maarch. Une archive n'y entre que si elle a du
papier, et c'est une décision écrite.

- **Source** : le veilleur lit aussi les dépôts (`recordsManagement/deposit`). Chaque archive
  déposée donne **une seule ligne** (unicité tenant + archive Maarch) : relire le journal ou un
  doublon de dépôt ne crée rien de plus.
- **Ne deviennent jamais candidates** :
  - une **sous-archive** (`parentArchiveId` renseigné, ex. `FR094080_2026_AE0002_001` dans
    AE0002) : elle est dans son dossier et suit sa boîte ;
  - une archive **déjà suivie** par JokkoRM.
- **Dépôt illisible** : si Maarch ne donne pas la cote au compte `jokkorm` (archive d'un autre
  service, sans cote, ou supprimée), la ligne est gardée « illisible » avec son identifiant
  Maarch. Rien n'est perdu en silence. Après correction des droits, « Rattraper » la réévalue.
- **Décisions** (archiviste ou responsable) : « Importer » revérifie la cote dans Maarch puis
  crée la fiche physique ; « Pas de papier » l'écarte (réversible, « Remettre dans la liste »).
  Un import fait depuis la recherche de la page Archives solde aussi la ligne.
- **Règle par profil** (responsable des archives seulement) : « ce profil a toujours du papier ».
  Elle vaut pour les **prochains** dépôts ; les archives déjà en attente restent à décider, avec
  un bouton « Importer les N en attente ». Activation et retrait sont audités (qui, quand), et
  chaque import automatique porte dans l'audit le profil, qui a activé la règle, quand, et
  l'événement Maarch (`evenement-maitre=`).
- **Rattrapage** : « Rattraper les dépôts des 30 derniers jours » relit le journal sans toucher au
  curseur du veilleur. Rejouable sans risque.
- **Données personnelles** : seuls l'identifiant Maarch, la cote, le profil, qui et quand sont
  gardés. Le titre est lu en direct pour l'écran (30 au plus par affichage), jamais stocké.
- **Vérifié en réel le 2026-11-24** (rattrapage 30 jours, aucune règle) : 4 dépôts lus, 3 déjà
  suivis (AE0002 déposée deux fois, AE0003), 1 sous-archive (AE0002_001). Rien n'a été écrit.

**Test d'acceptation (à faire dans Maarch)** :

1. Créer une archive DOSIP de premier niveau : elle apparaît dans « À importer » en une minute
   environ.
2. Créer une sous-archive dedans : elle n'apparaît pas.
3. En responsable des archives, activer l'import automatique de DOSIP, puis créer une autre
   archive DOSIP : elle entre seule dans JokkoRM, « Enregistrée », à ranger.

## Montées de version de Maarch RM

**Aucun fichier du code de Maarch n'est modifié** : vérifié le 2026-11-23 (dossier `custom/`
vide, aucun écart de contenu avec le code livré). JokkoRM ajoute seulement deux éléments, par
l'API d'administration officielle et supprimables depuis l'interface de Maarch : le champ
`localisation_physique` et le compte de service `jokkorm`. Une nouvelle version de Maarch les
reprend comme toute autre donnée.

Ce qu'une nouvelle version peut casser, ce sont les comportements de Maarch sur lesquels
JokkoRM s'appuie. Du plus au moins grave :

1. **`PUT /recordsManagement/archives/Metadata` remplace toute la description.** Risque
   silencieux : si une version ajoute un champ que JokkoRM ne renvoie pas, il serait vidé.
2. **Particularités de laabs** : jeton dans le User-Agent, en-tête `X-Laabs-Exception`, cookie
   `LAABS-AUTH`. Une rupture serait visible : l'adapter la traite en panne ou en refus.
3. **Le journal**, lu par le veilleur. Ces comportements de Maarch 2.7 sont pris en compte
   un par un :
   - tous les paramètres sont obligatoires, même vides (sinon 400) ;
   - les dates sont comparées en UTC, le fuseau envoyé est ignoré ;
   - les résultats viennent du plus récent au plus ancien, et la limite garde les plus récents ;
   - `maxDate` reçoit 23 h 59 min 59 s de plus ;
   - une archive invisible pour le compte répond 200 + `accessDenied`.

   Une rupture serait visible : la pagination se contrôle elle-même, et le veilleur passe
   « en erreur » sans avancer.
4. **Les routes lues** (`archives`, `archives/Count`, `archive/Metadata/{id}`) et la page
   `archiveDescription/{archiveId}` du bouton (au pire : page d'erreur Maarch).

**Après chaque montée de version, sur une archive de test :**

1. Prendre l'empreinte de l'archive (champs de description et identifiants, sans données
   personnelles).
2. Faire un geste physique réel (déplacer sa boîte) et vérifier « À jour » sur la fiche JokkoRM.
3. Reprendre l'empreinte : elle doit être identique, seul `localisation_physique` change.
4. Cliquer sur « Ouvrir dans Maarch RM ».
5. Modifier l'emplacement à la main dans Maarch : il doit être rétabli en une minute environ,
   avec un signalement. Relancer `MaarchRmLiveReadTest` (lecture seule).

Les tests automatiques (`MasterSyncIT`, `MaarchRmSaeMasterAdapterTest`) tournent contre un faux
Maarch (WireMock) : ils ne détectent pas un changement de comportement d'une nouvelle version.

## Tranche 2b (à étudier)

- **Droits unifiés** (pattern 11) : Maarch n'offre pas d'appel « cet utilisateur peut-il voir
  cette archive ? » et agir au nom d'un utilisateur demande ses identifiants ou un vrai SSO.
  Étude préalable avant tout code.
- **Destruction** : attestation jointe à l'archive Maarch, avec la phase 5G (ordre exact
  attestation / élimination côté Maarch à fixer avec le bordereau d'élimination).
