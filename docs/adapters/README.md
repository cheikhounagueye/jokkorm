# Adapters SaeMasterPort

JokkoRM se branche sur un maître (GED/SAE) via une interface unique, `com.jokkorm.domain.master.SaeMasterPort`. Chaque maître supporté a son adapter concret.

## Adapters livrés

| Nom | Package | Statut | Doc |
|---|---|---|---|
| **noop** | `adapter.out.master.noop` | ✅ phase 2 | [noop.md](noop.md) *(à venir)* |
| **maarch** | `adapter.out.master.maarch` | 🚧 phase 3 | [maarch.md](maarch.md) *(à venir)* |
| **generic** | `adapter.out.master.generic` | 🚧 phase 3 | [generic.md](generic.md) *(à venir)* |

## Backlog

- `alfresco` — REST CMIS + webhook
- `nuxeo` — Nuxeo REST + Kafka event bus
- `sharepoint` — Graph API + webhooks
- `maarchcourrier` — distinct de maarch-rm (modèle différent)

## Écrire un nouvel adapter

1. Créer un package `com.jokkorm.adapter.out.master.<nom>/`.
2. Implémenter `SaeMasterPort`.
3. Déclarer un bean conditionnel : `@ConditionalOnProperty(name = "jokkorm.master.kind", havingValue = "<nom>")`.
4. Écrire un test de contrat : `extends SaeMasterPortContractTest` (livré en phase 2).
5. Documenter dans `docs/adapters/<nom>.md` : auth, endpoints, événements, mapping, cas d'erreur.
6. Ajouter une entrée au tableau ci-dessus.
