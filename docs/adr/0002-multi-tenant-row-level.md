# ADR 0002 — Multi-tenant row-level, discriminant obligatoire sur chaque ligne

- **Statut** : Accepté (2026-11-21)
- **Auteur** : Cheikh A. Bamba + Claude (Opus 4.7)
- **Décideurs** : Cheikh A. Bamba
- **Références** : JokkoFile mémoire `feedback-security-standards.md`, `sae-s2-rbac-fin`, ISO 27001 A.5.15

## Contexte

JokkoRM est destiné à être **hébergé en mode SaaS mutualisé** aussi bien qu'en on-premise mono-tenant. Un client public sénégalais (laborex), un intégrateur Maarch français, et une banque ivoirienne doivent pouvoir cohabiter sur la même instance sans jamais voir un octet l'un de l'autre.

Trois modèles classiques sont sur la table :

1. **Schema-per-tenant** : un schéma Postgres par tenant. Isolation forte, provisioning lent, migrations difficiles à N schémas.
2. **Database-per-tenant** : encore plus isolé, infra encore plus lourde.
3. **Row-level, colonne `tenant_id` sur chaque table** : la plus légère, la plus flexible, mais dépend d'une discipline logicielle sans faille.

## Décision

**Row-level isolation.** Chaque table (sauf référentiel 100 % partagé type `migration_lock`) porte une colonne `tenant_id VARCHAR(64) NOT NULL`, et toute requête SQL contient un prédicat `tenant_id = :currentTenant`.

### 1. Le discriminant

- Un type valeur `TenantId` (record Java, slug `^[a-z0-9]([a-z0-9-]{0,62}[a-z0-9])?$`, 1–64 chars, DNS-safe).
- Une table racine `tenant(id PK, display_name, created_at, version)` seedée à `demo` au boot.
- Propagation via un `TenantContext` (ThreadLocal rempli par un filtre web à partir du header `X-Tenant-Id` ou du claim JWT `tenant`).

### 2. Garde-fou domaine

- Tout port repository prend `TenantId` en premier paramètre (`findById(TenantId, UUID)`). Pas de surcharge sans tenant.
- Tout agrégat racine porte `tenantId()` et la méthode `assertSameTenant()` est appelée dans chaque use case qui croise deux agrégats.

### 3. Garde-fou DB

- Index composite systématique `(tenant_id, <clé métier>)`.
- Contraintes d'unicité toujours composites avec `tenant_id` : `UNIQUE(tenant_id, code)` jamais `UNIQUE(code)`.
- Audit chain HMAC (ADR ultérieur) chaînée *par tenant*, jamais inter-tenants.

### 4. Garde-fou REST

- Un filtre `TenantContextFilter` rejette toute requête API sans tenant résolu (401 avec `problem+json` normalisé).
- Les contrôleurs ne reçoivent jamais `tenantId` en argument — il vient du contexte. Impossible d'altérer un enregistrement d'un autre tenant via query-string.

## Conséquences

### Positives
- Infra unique, provisioning instantané, backup/restore à grain fin via `WHERE tenant_id = ...`.
- Migrations Liquibase uniques, pas N variantes.
- Pattern connu de l'équipe (identique à JokkoFile core-service).

### Négatives
- **Discipline absolue** : un seul `SELECT` oubliant `tenant_id` = cross-tenant data leak. Mitigations :
  - revue code systématique sur toute nouvelle requête native ;
  - test IT dédié « cross-tenant leak » à ajouter dès qu'un premier bounded context arrive (phase 1) ;
  - Postgres RLS (Row Security Policies) envisagée en ADR ultérieur comme ceinture + bretelles.
- Index croît avec `tenant_id` en tête → quelques pct de stockage en plus. Acceptable.

## Notes

Le modèle est identique à celui d'eversuite-modern. Différence : JokkoRM ne traîne pas `TenantAwareEntity` avec audit timestamps dès le baseline — on les ajoutera contexte par contexte quand on en a besoin, pas par défaut (voir ADR futur `BaseEntity minimaliste`).
