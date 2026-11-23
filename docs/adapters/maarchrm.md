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

## Tranche 2 (à venir)

Écritures vers Maarch : emplacement physique, mouvements, gel, destruction (Medona `archiveDestruction`). Il faut d'abord décider, selon les patterns 02 et 03, quels champs sont détenus par qui.
