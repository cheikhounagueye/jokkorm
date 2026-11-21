# 03 — Qui détient quoi

> Extrait du tableau de répartition. Chaque ligne est un test d'acceptance à la livraison.

Ce tableau **engage celui qui construira le module** — équipe interne ou prestataire — et chaque ligne sera testée à la recette. Si une information nouvelle n'y figure pas, c'est qu'aucun des deux outils n'en est propriétaire : **on ne la crée nulle part** tant que le comité n'a pas dit lequel la porte. Sinon les deux la stockent, et elles finissent par afficher deux valeurs différentes.

| Information ou fonction | Maître | Précision |
|---|---|---|
| Plan de classement | **Maître** | L'arborescence qui organise le fonds. JokkoRM en reçoit une copie en lecture seule, rafraîchie quotidiennement via `fetchClassificationPlan`. |
| Cote de l'archive | **Maître** | L'identifiant unique du dossier, fixé à la clôture. Porté par le champ `originatorArchiveId` côté Maarch, repris tel quel côté JokkoRM. Voir ADR-0004. |
| DUA et sort final | **Maître** | Affichés dans JokkoRM à titre informatif, jamais modifiables. Reçus via `fetchByCote` au moment du versement physique. |
| Scellement et preuve d'intégrité | **Maître** | Porte sur le numérique. Un original papier ne se scelle pas : sa preuve, c'est sa chaîne de garde (voir mouvements). |
| Bordereau d'élimination | **Maître** | Instruit, visé et scellé côté maître. JokkoRM y joint l'attestation d'exécution via `notifyDestructionExecuted`. |
| Gel contentieux (legal hold) | **Maître** | Décidé côté maître, propagé immédiatement via l'événement entrant `legal-hold`. JokkoRM bloque alors toute sortie physique. |
| **Topographie des locaux** | **JokkoRM** | Sites, magasins, travées, tablettes. Le maître n'en a pas connaissance. |
| **Contenants et emplacements** | **JokkoRM** | Boîtes, codes-barres, adresse sur cinq niveaux. Renvoyée au maître pour affichage seulement via `notifyPhysicalLocation`. |
| **Mouvements, prêts, fantômes** | **JokkoRM** | Chaque geste horodaté et attribué à un agent nommé. Chaîne d'audit HMAC vérifiable. Renvoi au maître via `notifyMovement`. |
| **Récolement et écarts** | **JokkoRM** | Campagnes d'inventaire (scan douchette), constats d'écart, plan de résorption. |
| **Numérisation à la demande** | **JokkoRM** | Copie de consultation rattachée au dossier. L'original papier reste en magasin. |
| Identité, rôles, recherche, journal | **Les deux** | Un annuaire unique en SSO (Keycloak realm partagé), une seule barre de recherche (recherche fédérée), une vue consolidée des opérations (journal croisé). |

## Comment ce tableau est testé à la recette

À chaque livraison, le script `make acceptance-test` exécute une suite Playwright qui tente, pour chaque ligne, les cas suivants :

- **Ligne « Maître »** : essaie de modifier le champ côté JokkoRM → doit retourner `403 master-owned-field`.
- **Ligne « JokkoRM »** : essaie de modifier le champ côté maître → doit retourner l'équivalent 403 côté maître (Maarch : `resource.locked.by.physical.module`).
- **Ligne « Les deux »** : vérifie que la modification propagée par le SSO est visible des deux côtés dans les 30 secondes.

Un échec sur une ligne bloque la livraison. C'est la règle.
