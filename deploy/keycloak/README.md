# Keycloak — realm `jokkorm` (dev)

Importé automatiquement au `docker compose up` via le volume `./keycloak/realms:/opt/keycloak/data/import`.

## Ce que ça pose

- **Realm** `jokkorm` (display name « JokkoRM — Records Management »)
- **Rôles realm** :
  - `RM_VIEW` — lire
  - `RM_INGEST` — enregistrer versement, créer conteneur
  - `RM_MOVE` — déplacer un conteneur
  - `RM_APPROVE` — approuver quorum N-eyes
  - `RM_MANAGE` — administrer référentiel
  - `RM_MASTER_WEBHOOK` — réservé aux hooks maître (M2M)
- **Groupes** :
  - `jokkorm-admins` — tous les rôles sauf `RM_MASTER_WEBHOOK`
  - `jokkorm-operators` — `RM_VIEW` + `RM_INGEST` + `RM_MOVE`
  - `jokkorm-viewers` — `RM_VIEW` seul
- **Client scope `tenant`** (default) — mapper OIDC qui expose l'attribut user `tenant` comme claim JWT du même nom, consommé par `HttpTenantResolver`.
- **Client `jokkorm-web`** (public, PKCE) — frontend Next.js, redirects `http://localhost:3100/*` et `https://rm.jokkofile.com/*`.
- **Client `jokkorm-master-webhook`** (confidential, service accounts) — M2M pour qu'un maître (Maarch, SI custom) pose un `POST /api/v1/events/master/*` avec un token OIDC client_credentials. Rôles accordés : `RM_MASTER_WEBHOOK` + `RM_INGEST`.
- **Users démo** (mot de passe `JokkoRM2026!`, tenant `demo`) :
  - `demo.admin` ∈ admins
  - `demo.operator` ∈ operators

## URLs utiles (dev local)

- Console admin Keycloak : http://localhost:8085 — login `admin` / `admin`
- OpenID discovery : http://localhost:8085/realms/jokkorm/.well-known/openid-configuration
- JWKS : http://localhost:8085/realms/jokkorm/protocol/openid-connect/certs
- Issuer URI (consommé par le backend) : http://localhost:8085/realms/jokkorm

## Obtenir un token (dev smoke test)

### User interactif (password grant, désactivé en prod mais utile en dev)

Note : `directAccessGrantsEnabled: false` sur `jokkorm-web`, donc le password grant est bloqué via ce client. Pour un test rapide en ligne de commande, passer plutôt par le flow authorization code depuis le navigateur ou créer un client dédié dev.

### Service account (M2M, utilisé par un maître)

```bash
curl -s -X POST \
  "http://localhost:8085/realms/jokkorm/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=jokkorm-master-webhook" \
  -d "client_secret=change-me-in-prod-via-admin-cli" \
  | jq -r .access_token
```

Ce token porte le claim `tenant=demo` et les rôles `RM_MASTER_WEBHOOK, RM_INGEST`. L'utiliser contre les endpoints maître :

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8085/realms/jokkorm/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=jokkorm-master-webhook" \
  -d "client_secret=change-me-in-prod-via-admin-cli" | jq -r .access_token)

curl -s -X POST http://localhost:8090/api/v1/events/master/cote-assigned \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cote":"SMOKE/2026/001"}'
```

## Changer le secret M2M pour la prod

Le fichier `jokkorm-realm.json` porte `secret: change-me-in-prod-via-admin-cli` — valeur **sentinelle** qu'il faut rotater avant tout déploiement non-local. Procédure :

```bash
docker exec -it jokkorm-keycloak /opt/keycloak/bin/kcadm.sh \
  config credentials --server http://localhost:8080 \
  --realm master --user admin --password admin

docker exec -it jokkorm-keycloak /opt/keycloak/bin/kcadm.sh \
  update clients/<client-uuid-id>/client-secret --realm jokkorm
```

Récupérer ensuite le secret généré et le mettre dans le vault de l'intégrateur maître.

## Reconstruire le realm from scratch

Le realm est imported **au premier démarrage uniquement** (idempotent via Keycloak). Pour forcer un re-import :

```bash
docker compose -f deploy/compose.dev.yml down -v   # -v drop le volume Keycloak H2
docker compose -f deploy/compose.dev.yml up -d
```
