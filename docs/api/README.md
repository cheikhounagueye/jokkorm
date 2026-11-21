# API publique JokkoRM v1

## Statut

**v1.draft** — le contrat sera figé à la phase 2 (fin S3). D'ici là, tout évolue librement.

## Spécification

Le fichier de référence sera `openapi.yaml` à partir de la phase 2. Il décrira :

- **Lectures** — `/api/v1/records`, `/api/v1/records/{cote}`, `/api/v1/records/{cote}/consolidated`
- **Écritures physiques** — mouvement, prêt/retour, récolement, destruction
- **Événements entrants maître** — `/api/events/master/cote-assigned`, `/legal-hold`, `/disposal-ordered`
- **Admin** — adapters maître, politique quorum, agents service

Entre temps, l'exploration se fait via Swagger UI en runtime : http://localhost:8090/swagger-ui.html

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
