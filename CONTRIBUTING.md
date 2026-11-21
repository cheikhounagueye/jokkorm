# Contribuer à JokkoRM

Merci de votre intérêt. JokkoRM est un projet jeune, les règles sont volontairement légères — mais quelques-unes sont intangibles.

## Avant toute contribution

1. **Lire le pattern** : [docs/pattern/README.md](docs/pattern/README.md). Toute proposition qui le viole est rejetée d'office.
2. **Lire les ADR** : [docs/adr/](docs/adr/). Les décisions architecturales sont documentées ; un PR qui va à l'encontre d'un ADR doit s'accompagner d'un ADR de remplacement.
3. **Ouvrir une issue avant de coder** pour toute évolution non triviale. On évite ainsi les PR qu'on devra fermer.

## Règles d'or (non négociables)

- **Domain pur** : aucun import `org.springframework.*` ni `jakarta.persistence.*` dans `com.jokkorm.domain.*`. Vérifié par ArchUnit en CI.
- **Multi-tenant** : aucune requête SQL sans `tenant_id = ?`. Aucun port repository sans `TenantId` en paramètre.
- **SaeMasterPort** : toute dépendance à un système maître passe par ce port. Pas d'appel direct Maarch/Alfresco/etc. dans `domain` ou `application`.
- **Tests** : tout nouveau cas d'usage vient avec un test domain pur (sans Spring) + un test d'intégration (Testcontainers si DB).

## Setup local

Voir [README.md § Démarrage rapide](README.md).

## Workflow Git

- Branche depuis `main`.
- Commits atomiques, message clair (voir `git log --oneline` pour le style).
- Pull Request avec description : contexte, changement, tests ajoutés, captures d'écran si UI.
- Un reviewer minimum. La CI doit être verte.

## Licence des contributions

En soumettant un PR, vous acceptez que votre contribution soit publiée sous AGPL-3.0, et qu'un **CLA (Contributor License Agreement)** vous soit demandé si le projet passe en dual-license commerciale à terme.

## Code of Conduct

Soyez respectueux. Ignorer, bloquer ou dénigrer un contributeur est un motif d'exclusion du projet.
