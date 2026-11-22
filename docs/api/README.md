# API publique JokkoRM v1

## Statut

**v1.0-draft** (phase 2). Le fichier `openapi.yaml` ci-contre est la source de vérité. Il sera gelé en `v1.0.0` à la phase 4 ; après ça, toute rupture rétro-incompatible = `v2`.

## Spécification

Le fichier de référence est [`openapi.yaml`](openapi.yaml). Il décrit les endpoints livrés en phase 2 :

- **Lecture** — `GET /api/v1/records` (liste paginée), `GET /api/v1/records/by-cote?cote=X`
- **Événements entrants maître** — `POST /api/v1/events/master/cote-assigned`, `POST /api/v1/events/master/legal-hold`

Endpoints prévus en phases ultérieures (hors scope v1.0-draft) :

- **Écritures physiques** — mouvement, prêt/retour, récolement, destruction (phase 3)
- **Vue consolidée** — `/records/{cote}/consolidated` (fusion numérique maître + physique local, phase 3)
- **Événements entrants additionnels** — `/events/master/disposal-ordered` (phase 3)
- **Admin** — adapters maître, politique quorum, rôles service (phase 3-4)

Exploration en runtime : http://localhost:8091/swagger-ui.html

## Versioning

- SemVer strict. `v1` tient toute la famille 1.x.
- Bump majeur = `v2`, path distinct. v1 reste disponible 12 mois après publication de v2.
- Un changement rétro-incompatible non annoncé est un bug, pas une fonctionnalité.

## Authentification

OIDC Bearer (Keycloak). Flow `client_credentials` pour les intégrations machine-to-machine (plateforme IA, intégrateur maître). Scopes requis documentés par endpoint dans l'OpenAPI.

## Multi-tenant

Chaque requête porte le tenant soit via :
- claim JWT `tenant` (recommandé),
- header `X-Tenant-Id` (pour les scénarios admin d'un opérateur multi-tenant).

Absence de tenant résolu = `401 problem+json tenant-unresolved`.
