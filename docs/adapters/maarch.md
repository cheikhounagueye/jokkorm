# Adapter `maarch` — Maarch RM REST

## Positionnement

Adapter concret pour **Maarch RM** (open source, PHP + Laminas, utilisé secteur public FR). JokkoRM se branche sur Maarch comme module physique : le plan de classement, les DUA, le scellement, le gel contentieux restent côté Maarch ; JokkoRM renvoie emplacements, mouvements et attestations de destruction.

## Statut

Phase 3B — **couverture minimale**. Couvre les 5 opérations du `SaeMasterPort` suffisantes pour un pilote. `resolveAgent` renvoie `empty()` : le SSO Keycloak partagé rend ce lookup optionnel pour un adapter unique. Phase 4+ l'ajoutera si un tenant le demande.

## Activation

```yaml
jokkorm:
  master:
    kind: maarch
    maarch:
      base-url: https://maarch.client.lan
      login: svc-jokkorm
      password: ${JOKKORM_MASTER_MAARCH_PASSWORD}  # jamais en clair en config
      timeout-ms: 10000
```

Variables d'env équivalentes : `JOKKORM_MASTER_KIND=maarch`, `JOKKORM_MASTER_MAARCH_BASE_URL`, etc.

## Compte technique côté Maarch

Créer un utilisateur dédié dans Maarch (ex. `svc-jokkorm`) et le mettre dans un groupe avec **uniquement** :

- `view_resource` (lecture)
- `edit_resource` (pour `/externalInfos` et `/notes`)
- `add_attachment` (pour l'attestation de destruction)

**Pas** de `delete_resource` ni de `admin_*`. JokkoRM ne doit jamais pouvoir supprimer une ressource Maarch, par principe de moindre privilège (ISO 27001 A.5.15).

Rotation du mot de passe : 90 jours, procédure à documenter côté runbook ops.

## Mapping des opérations

| `SaeMasterPort` | Appel Maarch | Détail |
|---|---|---|
| `fetchByCote(tenant, cote)` | `GET /rest/resources?search={cote}` | Extrait `subject` → title, `destination` → classificationPath, `retentionFinalDisposition` → finalFate, `retentionHold` → legalHold |
| `resolveAgent(tenant, subject)` | *non implémenté* | Retourne `empty()` ; SSO partagé rend optionnel |
| `notifyPhysicalLocation(…)` | `PUT /rest/resources/{resId}/externalInfos` | Champ libre `externalInfos.physical_location` = "site / magasin / travée / tablette / position" |
| `notifyMovement(…)` | `POST /rest/resources/{resId}/notes` | Note structurée `[JokkoRM] KIND · agent=X · at=ISO · motive` |
| `notifyDestructionExecuted(…)` | `POST /rest/resources/{resId}/attachments` | PDF scellé Base64, `type=destruction_attestation` |

Avant chaque notification, JokkoRM résout le `resId` numérique Maarch depuis la cote via `GET /rest/resources?search={cote}`. Si la cote est inconnue côté Maarch, la notification est loguée en WARN et abandonnée silencieusement — l'opération métier côté JokkoRM n'est pas bloquée (best-effort).

## Authentification

- `POST /rest/authenticate` avec `{login, password}` retourne un JWT.
- Le token est caché en mémoire 50 minutes (Maarch émet typiquement 1h).
- Un 401 force une ré-authentification + retry de la requête.

Pas de stockage persistant du token : relancer le process = nouveau token. Acceptable pour un service long-running.

## Limites assumées en phase 3B

- **Un seul Maarch adressé** (pas de config par tenant encore). Si plusieurs tenants pointent chacun vers un Maarch distinct, c'est phase 4 (promotion en `tenant_master_config` DB-driven).
- **Pas de HMAC entrant** sur `/api/v1/events/master/*` : si Maarch veut pousser des hooks (cote-assigned, legal-hold), c'est OIDC comme le reste. Phase 4 ajoutera un filtre HMAC optionnel.
- **Pas de cache long** sur `fetchByCote` : chaque appel va à Maarch. Phase ultérieure si pression en lecture.
- **Erreurs silencieuses** sur notify* : loggées en WARN, n'exceptent pas le use case métier. C'est intentionnel (le gel physique peut avoir eu lieu même si on ne l'a pas remonté) mais c'est à surveiller dans les logs structurés.

## Tests

- **Unitaires WireMock** (`MaarchSaeMasterAdapterTest`) : auth + retry 401, fetchByCote mapping, les 3 notify sur les bons endpoints, silence sur cote inconnue.
- **IT bout-en-bout** phase 4 : optionnel, dépend d'une instance Maarch démo disponible. Non livré en 3B.

## Évolution

Quand un vrai pilote Maarch est signé, prioriser :

1. **Config par tenant** (`tenant_master_config`) — permet 2+ tenants sur la même instance JokkoRM pointant chacun vers son Maarch.
2. **resolveAgent** si l'audit croisé (JokkoRM remonte vers Maarch avec un nom résolu) devient important.
3. **IT contre instance Maarch démo** (tenant `demo-maarch`) pour valider les mappings réels.
