# Adapters SaeMasterPort

JokkoRM se branche sur un maître (GED/SAE) via une interface unique, `com.jokkorm.domain.master.SaeMasterPort`. Chaque maître supporté a son adapter concret, identifié par son `kind`.

## Un maître par tenant

`RoutingSaeMasterPort` est le port injecté partout : il passe chaque appel à l'adapter du tenant.

```
JOKKORM_MASTER_KIND=demo                 # maître par défaut
JOKKORM_MASTER_TENANTS_ACME=maarchrm     # le tenant « acme » lit le vrai Maarch RM
```

Un tenant n'a jamais deux maîtres, et il n'y a pas de repli d'un maître sur un autre (pattern 02). Si la configuration désigne un maître indisponible, le démarrage est refusé.

## Adapters livrés

| kind | Package | Statut | Doc |
|---|---|---|---|
| **noop** | `adapter.out.master.noop` | ✅ phase 2 | [noop.md](noop.md) |
| **demo** | `adapter.out.master.demo` | ✅ données fictives, jamais en prod | — |
| **maarchrm** | `adapter.out.master.maarchrm` | ✅ lecture (tranche 1) | [maarchrm.md](maarchrm.md) |
| **maarch** | `adapter.out.master.maarch` | ⚠️ vise l'API **Maarch Courrier** | [maarch.md](maarch.md) |
| **generic** | `adapter.out.master.generic` | 🚧 | *(à venir)* |

## Backlog

- `alfresco` — REST CMIS + webhook
- `nuxeo` — Nuxeo REST + Kafka event bus
- `sharepoint` — Graph API + webhooks

## Écrire un nouvel adapter

1. Créer un package `com.jokkorm.adapter.out.master.<nom>/`.
2. Implémenter `MasterAdapter` (`SaeMasterPort` + `kind()` + `displayName()`).
3. Déclarer le bean : toujours présent s'il ne demande aucune configuration, sinon conditionné à sa configuration (ex. URL renseignée). Le routage par tenant décide qui l'utilise.
4. Une panne du maître lève `MasterUnreachableException` ; `Optional.empty()` veut dire uniquement « cote inconnue ».
5. Documenter dans `docs/adapters/<nom>.md` : auth, endpoints, mapping, cas d'erreur.
6. Ajouter une entrée au tableau ci-dessus.
