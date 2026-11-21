# ADR 0001 — Monolithe modulaire et architecture hexagonale stricte

- **Statut** : Accepté (2026-11-21)
- **Auteur** : Cheikh A. Bamba + Claude (Opus 4.7)
- **Décideurs** : Cheikh A. Bamba
- **Références** : JokkoFile `docs/adr/` (patrimoine de décisions ré-appliquées au nouveau périmètre)

## Contexte

JokkoRM est un **module** d'archivage physique qui se branche sur une GED/SAE maître (Maarch RM, Alfresco, Nuxeo, ou SI maison). Il ne cherche pas à être un nouvel ECM généraliste. Son périmètre est délimité par le pattern **« une seule archive, deux gardiens »** (voir `docs/pattern/`) : il détient la topographie physique, les contenants, les mouvements, le récolement, l'exécution de la destruction. Il ne détient **pas** le plan de classement, les DUA, les sorts finaux, le scellement, le gel contentieux — ceux-ci viennent du maître.

Deux risques à cadrer dès le départ :

1. **Hyperfragmentation** : découper trop tôt en microservices quand le produit n'a ni équipe ni trafic qui le justifie. Coût opérationnel disproportionné pour zéro bénéfice.
2. **Spaghetti framework** : laisser Spring/JPA coloniser le code métier. On ne peut alors plus tester sans container, on ne peut plus changer d'ORM ni de web framework sans tout réécrire.

## Décision

**Monolithe modulaire, architecture hexagonale stricte.**

### 1. Un seul exécutable `rm-service` au départ

Un module Gradle `apps/rm-service` produit un unique `boot.jar` Spring Boot 3.5 / Java 21. Pas de microservices. Si un composant doit se décoller plus tard (indexation, sync maître en backpressure), on l'extrait dans `apps/<nom>-service` : le découpage interne en packages prépare déjà cette coupe.

### 2. Couches hexagonales dans `com.jokkorm`

```
com.jokkorm
├── domain/           ← objets métier purs, 0 import Spring/Jakarta Persistence
│   ├── <bounded-context>/
│   │   ├── <Entity>.java
│   │   ├── <Port>.java            (interface — le domaine déclare ce dont il a besoin)
│   │   └── <DomainService>.java
├── application/      ← use cases — orchestrent domain + ports
│   └── <feature>/<UseCase>.java   (classe fine, @Transactional autorisée ici)
├── adapter/
│   ├── in/
│   │   ├── web/                   (controllers REST, DTOs, mappers)
│   │   └── event/                 (webhooks entrants depuis le maître)
│   └── out/
│       ├── persistence/           (entités JPA + repositories Spring Data)
│       ├── master/                (implémentations de SaeMasterPort — Maarch, generic, noop)
│       ├── sso/                   (Keycloak admin)
│       └── notification/          (SSE + webhooks sortants)
├── boot/             ← Application.java + beans config
└── config/           ← SecurityConfig, JacksonConfig, etc.
```

### 3. Règles d'or appliquées à chaque PR

- **Domain pur** : aucun import `org.springframework.*` ni `jakarta.persistence.*` dans `domain/`. Vérifié par ArchUnit dès la phase 1.
- **Ports dans domain** : une interface `SaeMasterPort` vit dans `domain/master/`, ses implémentations dans `adapter/out/master/{maarch,generic,noop}/`. Le domaine ne connaît que l'interface.
- **Tests domain sans container** : chaque entité, chaque use case a un test unitaire pur (JUnit + AssertJ, pas de Testcontainers). Testcontainers seulement pour les adapters.
- **Une entité JPA ≠ une entité domaine**. L'entité JPA (`adapter/out/persistence/`) est un DTO de mapping ; `fromDomain` / `toDomain` à chaque frontière. Évite le leak Hibernate dans le métier.

## Conséquences

### Positives
- **Démarrage rapide** : un seul service à déployer, debugger, instrumenter.
- **Réutilisation** : le pattern est identique à celui de JokkoFile, donc l'équipe l'applique sans ré-apprentissage.
- **Extraction préparée** : la découpe en packages est déjà celle qu'aurait un microservice ; le jour où ça vaut le coup, le move est mécanique.
- **Testabilité** : le domaine est testable en < 100 ms sans Docker ni base.

### Négatives
- **Discipline requise** : sans règle ArchUnit en CI, la frontière domaine ↔ adapter se dégrade vite. Règle `NoSpringImportsInDomain` à ajouter en phase 1.
- **Boilerplate de mapping** : `fromDomain/toDomain` entre entités JPA et entités domaine. On accepte. Alternative (Lombok + annotations JPA sur le domaine) rejetée : coût caché bien plus élevé à 3 ans.

## Notes

Pattern identique à l'ADR 0007 d'eversuite-modern (capture-desktop) et à la structure de `apps/core-service/src/main/java/com/eversuite/core/`. Le changement est le package racine (`com.jokkorm`), pas les règles.
