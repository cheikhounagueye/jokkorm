# JokkoRM

> Module d'archivage physique qui se branche sur votre GED/SAE.
> Pattern **« une seule archive, deux gardiens »**. Licence AGPL-3.0.

JokkoRM est un **module de Records Management physique** : magasins, boîtes, emplacements, mouvements, prêts, récolement, exécution de la destruction. Il ne stocke pas les documents numériques natifs — ceux-ci restent dans la GED/SAE maître (Maarch RM, Alfresco, Nuxeo, ou un SI maison).

**Positionnement** : là où la GED décide (plan de classement, DUA, scellement, versement, destruction), JokkoRM range, trace, exécute. La règle d'or est simple : **une donnée, un seul propriétaire**.

Lire d'abord : [docs/pattern/README.md](docs/pattern/README.md) — les 5 pages qui cadrent tout le reste.

## Qui tire parti de JokkoRM

- **Intégrateurs Maarch RM** qui ont besoin d'un module physique moderne (scan douchette, codes-barres, fantômes PDF+QR, récolement industriel).
- **Archives publiques et administrations francophones** qui doivent gérer papier et numérique dans un dispositif cohérent.
- **DSI ayant déjà une GED** (Alfresco, Nuxeo, SharePoint, SI maison) et à qui on ne va pas vendre un remplacement.
- **Déploiement standalone** (petit volume, pas de SAE maître) : mode `noop` disponible, JokkoRM tourne seul.

## Statut

**Phase 0 — scaffold.** Le squelette hexagonal, la baseline Liquibase, la convention Gradle et les premiers ADR sont posés. Pas encore d'écrans fonctionnels, pas encore d'adapter Maarch.

Voir [Roadmap](#roadmap) pour les 6 semaines à venir.

## Stack

- **Backend** — Java 21 · Spring Boot 3.5 · Gradle Kotlin DSL · PostgreSQL 16 · Liquibase · Keycloak OIDC
- **Frontend** — Next.js 15 · App Router · TanStack Query · Sonner
- **Tests** — JUnit 5 · AssertJ · Testcontainers · Playwright
- **Observabilité** — Prometheus · OpenTelemetry · Logs JSON (Logstash encoder)

## Démarrage rapide

```bash
# 1. Lever la stack de dev (Postgres + Keycloak + Mailpit)
make dev

# 2. Lancer le backend
./gradlew :apps:rm-service:bootRun

# 3. Lancer le frontend
cd apps/web && npm install && npm run dev

# 4. Smoke test
curl http://localhost:8091/actuator/health
open http://localhost:3100
```

## Architecture

Lire dans l'ordre :

1. [ADR-0001 — Monolithe modulaire + hexagonal strict](docs/adr/0001-modular-monolith-hexagonal.md)
2. [ADR-0002 — Multi-tenant row-level](docs/adr/0002-multi-tenant-row-level.md)
3. [ADR-0003 — SaeMasterPort : un contrat, N adapters](docs/adr/0003-sae-master-port.md)
4. [ADR-0004 — La cote comme identifiant partagé](docs/adr/0004-cote-as-shared-identifier.md)

## Adapters maître disponibles

| Adapter | Statut | Mode |
|---|---|---|
| `noop` | ✅ Phase 2 | Standalone — JokkoRM tourne seul, pas de maître |
| `maarch` | 🚧 Phase 3 | REST Maarch + script-event |
| `generic` | 🚧 Phase 3 | REST ouvert + webhook HMAC |
| `alfresco` | ⏳ Backlog | Après v1.0 |
| `nuxeo` | ⏳ Backlog | Après v1.0 |

Chaque adapter a sa doc dans [docs/adapters/](docs/adapters/).

## Roadmap

| Phase | Semaine | Livrable |
|---|---|---|
| **P0** Scaffold | S1 | Repo, CI, compose dev, boot + Next.js qui répondent |
| **P1** Extraction domaine | S2 | Versants, holding, mouvements, prêts, récolement, destruction extraits de JokkoFile + tests verts |
| **P2** API v1 + SaeMasterPort | S3 | OpenAPI figée, NoopAdapter, REST smoke-testable |
| **P3** Adapter Maarch + UI MVP | S4-S5 | Vrai Maarch, pages magasin/prêts/récolement |
| **P4** Publication + démo | S6 | `rm.jokkofile.com` live, annonce, premier prospect |

## Licence

AGPL-3.0 (voir [LICENSE](LICENSE)). Si vous intégrez JokkoRM dans un service accessible en ligne, vous devez publier vos modifications sous la même licence. Pour un usage où ce n'est pas possible, contactez-nous pour une licence commerciale (dual-licensing — modèle Odoo/GitLab).

## Contribuer

Voir [CONTRIBUTING.md](CONTRIBUTING.md). Le pattern « une seule archive, deux gardiens » (docs/pattern/) est la constitution du projet — toute proposition qui le viole est rejetée d'office.

## Contact

Mainteneur : Cheikh A. Bamba — [github.com/cheikhabamba](https://github.com/cheikhabamba)
