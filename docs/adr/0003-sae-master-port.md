# ADR 0003 — SaeMasterPort : un seul contrat, N adapters

- **Statut** : Accepté (2026-11-21)
- **Auteur** : Cheikh A. Bamba + Claude (Opus 4.7)
- **Décideurs** : Cheikh A. Bamba
- **Références** : `docs/pattern/` (une seule archive, deux gardiens), ADR 0004 (cote identifiant partagé)

## Contexte

Le pattern « une seule archive, deux gardiens » (voir slides client, repris dans `docs/pattern/`) impose une règle d'or : **une donnée, un seul propriétaire**. Côté JokkoRM, ça veut dire :

- Le plan de classement, les DUA, les sorts finaux, le scellement, le gel contentieux, les bordereaux d'élimination sont **détenus par le maître** (Maarch RM, Alfresco, Nuxeo, SI maison, ou personne en mode standalone).
- La topographie physique, les contenants, les mouvements, le récolement, l'exécution de destruction sont **détenus par JokkoRM** et renvoyés au maître pour affichage.

La question architecturale : où branche-t-on le maître dans le code ? Deux pièges à éviter :

1. **Adapter spécifique Maarch partout** : un `MaarchClient` injecté dans le domaine. Impossible de brancher ensuite Alfresco sans réécrire la moitié de l'application.
2. **Pas d'abstraction** : chaque use case appelle REST Maarch à la main. Même piège, pire.

## Décision

Un **port unique dans le domaine** : `com.jokkorm.domain.master.SaeMasterPort`.

### 1. Le contrat (snapshot phase 2, figé en v1.0)

```java
public interface SaeMasterPort {

    // --- Lectures sortantes (JokkoRM demande au maître) ---

    /** Récupère la vérité maître sur une archive par sa cote. Null si inconnue. */
    MasterArchiveView fetchByCote(TenantId tenant, Cote cote);

    /** Résout l'identité d'un agent depuis le claim SSO. Un annuaire partagé. */
    AgentIdentity resolveAgent(TenantId tenant, String subject);

    // --- Événements sortants (JokkoRM notifie le maître d'un geste physique) ---

    /** Un emplacement vient d'être attribué à la boîte portant cette cote. */
    void notifyPhysicalLocation(TenantId tenant, Cote cote, PhysicalLocation location);

    /** Un mouvement vient d'être tracé (prêt, retour, déplacement...). */
    void notifyMovement(TenantId tenant, Cote cote, MovementEvent event);

    /** La destruction a été exécutée. On remonte l'attestation PDF scellée. */
    void notifyDestructionExecuted(TenantId tenant, Cote cote, DestructionAttestation attestation);
}
```

### 2. Trois adapters livrés, ouverts à N

- **`NoopSaeMasterAdapter`** (`adapter/out/master/noop/`) : mode standalone. JokkoRM tourne sans maître ; la cote est émise localement, pas de callback. Idéal pour prototype, démo, déploiement autonome.
- **`MaarchSaeMasterAdapter`** (`adapter/out/master/maarch/`) : REST Maarch (`/rest/resources/{cote}/...`) + script-event côté Maarch pour les hooks entrants. Livré en phase 3.
- **`GenericRestSaeMasterAdapter`** (`adapter/out/master/generic/`) : contrat REST ouvert avec webhook HMAC-signé. Pour les SI maison. Livré en phase 3.
- Prévu plus tard : `AlfrescoSaeMasterAdapter`, `NuxeoSaeMasterAdapter`.

### 3. Sélection runtime

Via `jokkorm.master.kind` (valeurs : `noop`, `maarch`, `generic`) dans `application.yml`. Un bean conditionnel par kind. Un seul adapter actif par instance. Pas de multi-master dans la même instance (règle d'or : *un* maître par archive).

### 4. Événements entrants (maître → JokkoRM)

Symétrique au port sortant : `adapter/in/event/` reçoit les hooks du maître :

- `/api/events/master/cote-assigned` — nouvelle cote émise par le maître, JokkoRM crée l'enveloppe physique.
- `/api/events/master/legal-hold` — gel contentieux prononcé, JokkoRM bloque toute sortie.
- `/api/events/master/disposal-ordered` — bordereau d'élimination visé, JokkoRM peut lancer l'exécution.

Sécurité : HMAC-SHA256 obligatoire sur le payload (secret partagé par tenant), rejet 401 sans signature valide.

## Conséquences

### Positives
- **Produit vendable à N écosystèmes** sans refactor. L'argument commercial n°1.
- **Testabilité** : le port est mockable, les use cases testables sans aucun client réseau.
- **Pattern lisible** : tout nouveau développeur comprend en lisant l'interface qui détient quoi.
- **Mode standalone gratuit** : `NoopAdapter` = démo / pilote / tenant captif sans infra maître.

### Négatives
- **Rigidité du contrat** : une fois `openapi.yaml` figé en v1.0, toute évolution = bump majeur côté maître. Mitigation : freeze le contrat par étapes (phase 2 = v1.draft, publié en v1.0 à la phase 4).
- **Risque d'abstraction fuyante** : un adapter maître pourrait avoir besoin d'une info que le port ne modélise pas. Règle : on étend le port, on ne contourne pas.

## Notes

Le port est volontairement **minimaliste**. Il modélise les 5-6 événements de la slide « cycle de vie d'une archive papier ». Tout le reste (recherche fédérée, consolidation UI) vit côté portail, pas côté port.
