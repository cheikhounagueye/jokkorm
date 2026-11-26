# JokkoRM — Operations

Runbook opérationnel. Mis à jour à chaque jalon.

## Environnements

| Env | URL backend | URL frontend | Notes |
|---|---|---|---|
| dev local | http://localhost:8091 | http://localhost:3100 | `make dev` |
| demo | https://rm.jokkofile.com (à venir) | idem | Coolify, sous-domaine jokkofile |
| prod client | fixé par déploiement | idem | compose.prod.yml |

## Démarrer en local

```bash
make dev                              # stack Postgres + Keycloak + Mailpit
./gradlew :apps:rm-service:bootRun    # backend :8091
cd apps/web && npm run dev            # frontend :3100
```

## Mode magasin sur iPhone (réseau local)

La caméra d'un iPhone ne s'ouvre qu'en HTTPS avec un certificat de confiance (pattern 12).

1. Une fois par Mac : `apps/web/scripts/https-lan-setup.sh` (installe mkcert, crée l'autorité
   locale, puis un certificat pour `<nom-du-mac>.local` et l'adresse IP). Relancer si l'IP change.
2. Une fois par iPhone : AirDrop de `rootCA.pem` (jamais `rootCA-key.pem`), Réglages › Profil
   téléchargé › Installer, puis Réglages › Général › Informations › Réglages de confiance des
   certificats › activer « mkcert ». Sans ce dernier interrupteur, la caméra reste bloquée.
3. Backend joignable **seulement** depuis le Mac : ajouter `SERVER_ADDRESS=127.0.0.1` à la
   commande de démarrage. L'iPhone passe par le front, qui relaie `/api`.
4. Front en HTTPS : `cd apps/web && npm run dev:lan` (remplace `npm run dev`, même port 3100).
5. Sur l'iPhone, Safari : `https://<nom-du-mac>.local:3100/terrain`, puis Partager › Sur l'écran
   d'accueil. Régler une fois l'espace, le rôle et le nom (l'appli installée ne partage pas les
   réglages de Safari).

Tant que la connexion (OIDC) n'est pas branchée, le rôle est simulé : ne faire ce test que sur un
réseau de confiance, et arrêter `dev:lan` après.

## Stack de développement (`make dev`)

- Postgres et Mailpit repartent seuls après un redémarrage de Docker ou d'OrbStack
  (`restart: unless-stopped`) ; `make dev-down` les arrête pour de bon. Pour qu'ils repartent après
  un redémarrage du Mac, OrbStack doit lui-même démarrer à l'ouverture de session (réglage
  d'OrbStack). Le backend et le front, lancés à la main, ne repartent pas seuls.
- Postgres n'écoute que sur ce Mac (`127.0.0.1:5490`) : son mot de passe de développement n'est pas
  exposé au réseau local. Changer de port ou d'adresse recrée le conteneur ; les données restent dans
  le volume `deploy_jokkorm-pgdata` (sauvegarder avant).

## Health checks

- Backend : `GET /actuator/health` (liveness + readiness)
- Postgres : `pg_isready -h 127.0.0.1 -p 5490 -U jokkorm`
- Keycloak : `curl http://localhost:8085/health/ready`

## Logs

Format JSON structuré (Logstash encoder). Champs standards :
- `@timestamp`, `level`, `logger`, `message`
- `tenant_id` injecté par `TenantContextLoggingFilter` (phase 2)
- `trace_id`, `span_id` via OpenTelemetry

## Métriques Prometheus

Exposées sur `/actuator/prometheus`. Scrapper avec un `prometheus.yml` qui pointe vers `rm-service:8091/actuator/prometheus`.

Métriques clés à surveiller (phase 2+) :
- `jokkorm_master_call_duration_seconds` — latence appels sortants vers le maître
- `jokkorm_master_call_errors_total` — échecs par kind d'adapter
- `jokkorm_physical_movement_total` — volumes de mouvements par verbe

## Backup

Phase ultérieure. Pour l'instant : `docker exec jokkorm-postgres pg_dump -U jokkorm -Fc jokkorm > jokkorm.dump`.

## Rotation secrets

- `JOKKORM_DB_PASSWORD` : via variable d'env, rotation à documenter en phase 2.
- Secret HMAC webhook maître : rotation 90 j, procédure en phase 3.
