# JokkoRM — Operations

Runbook opérationnel. Mis à jour à chaque jalon.

## Environnements

| Env | URL backend | URL frontend | Notes |
|---|---|---|---|
| dev local | http://localhost:8090 | http://localhost:3100 | `make dev` |
| demo | https://rm.jokkofile.com (à venir) | idem | Coolify, sous-domaine jokkofile |
| prod client | fixé par déploiement | idem | compose.prod.yml |

## Démarrer en local

```bash
make dev                              # stack Postgres + Keycloak + Mailpit
./gradlew :apps:rm-service:bootRun    # backend :8090
cd apps/web && npm run dev            # frontend :3100
```

## Health checks

- Backend : `GET /actuator/health` (liveness + readiness)
- Postgres : `pg_isready -h localhost -p 5490 -U jokkorm`
- Keycloak : `curl http://localhost:8085/health/ready`

## Logs

Format JSON structuré (Logstash encoder). Champs standards :
- `@timestamp`, `level`, `logger`, `message`
- `tenant_id` injecté par `TenantContextLoggingFilter` (phase 2)
- `trace_id`, `span_id` via OpenTelemetry

## Métriques Prometheus

Exposées sur `/actuator/prometheus`. Scrapper avec un `prometheus.yml` qui pointe vers `rm-service:8090/actuator/prometheus`.

Métriques clés à surveiller (phase 2+) :
- `jokkorm_master_call_duration_seconds` — latence appels sortants vers le maître
- `jokkorm_master_call_errors_total` — échecs par kind d'adapter
- `jokkorm_physical_movement_total` — volumes de mouvements par verbe

## Backup

Phase ultérieure. Pour l'instant : `pg_dump -h localhost -p 5490 -U jokkorm jokkorm > backup.sql`.

## Rotation secrets

- `JOKKORM_DB_PASSWORD` : via variable d'env, rotation à documenter en phase 2.
- Secret HMAC webhook maître : rotation 90 j, procédure en phase 3.
