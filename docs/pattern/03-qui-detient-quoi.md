# 03 — Qui détient quoi

> Extrait du tableau de répartition. Chaque ligne est un test d'acceptance à la livraison.

Ce tableau **engage celui qui construira le module** — équipe interne ou prestataire — et chaque ligne sera testée à la recette. Si une information nouvelle n'y figure pas, c'est qu'aucun des deux outils n'en est propriétaire : **on ne la crée nulle part** tant que le comité n'a pas dit lequel la porte. Sinon les deux la stockent, et elles finissent par afficher deux valeurs différentes.

| Information ou fonction | Maître | Précision |
|---|---|---|
| Plan de classement | **Maître** | L'arborescence qui organise le fonds. JokkoRM en reçoit une copie en lecture seule, rafraîchie quotidiennement via `fetchClassificationPlan`. |
| Cote de l'archive | **Maître** | L'identifiant unique du dossier, fixé à la clôture. Porté par le champ `archiverArchiveId` côté Maarch RM (cote du service d'archives, ADR-0004 amendé le 2026-11-23), repris tel quel côté JokkoRM. |
| DUA et sort final | **Maître** | Affichés dans JokkoRM à titre informatif, jamais modifiables. Reçus via `fetchByCote` au moment du versement physique. |
| Service producteur, échéance, classification | **Maître** | Servent à ranger (un service et un sort final par boîte). JokkoRM en garde une **copie en lecture seule** (`master_record_snapshot`, décision D3 du 2026-11-24) : prise à l'import, relue quand le veilleur voit l'archive changer et au plus tard chaque jour, jamais modifiée à la main, sans le titre ni la description. Sa date de lecture est gardée. **Maître muet = refus** : sans copie ni réponse du maître, fiche sans service, ou sort final illisible, la mise en boîte est refusée (règles R1 et R2, pattern 05). Une fiche **sans sort final** va dans une boîte « sort à fixer » de son service : JokkoRM n'invente jamais de sort (pattern 05, 2026-11-26). |
| Scellement et preuve d'intégrité | **Maître** | Porte sur le numérique. Un original papier ne se scelle pas : sa preuve, c'est sa chaîne de garde (voir mouvements). |
| Bordereau d'élimination | **Maître** | Instruit, visé et scellé côté maître. JokkoRM y joint l'attestation d'exécution via `notifyDestructionExecuted`. |
| Gel contentieux (legal hold) | **Maître** | Décidé côté maître, propagé immédiatement via l'événement entrant `legal-hold`. JokkoRM bloque alors toute sortie physique. |
| **Topographie des locaux** | **JokkoRM** | Sites, magasins, travées, tablettes. Le maître n'en a pas connaissance. |
| **Contenants et emplacements** | **JokkoRM** | Boîtes, codes-barres, adresse sur cinq niveaux. Renvoyée au maître pour affichage seulement via `notifyPhysicalLocation`. |
| **Mouvements, prêts, fantômes** | **JokkoRM** | Chaque geste horodaté et attribué à un agent nommé. Chaîne d'audit HMAC vérifiable. Renvoi au maître via `notifyMovement`. |
| **Récolement et écarts** | **JokkoRM** | Campagnes d'inventaire (scan douchette), constats d'écart, plan de résorption. |
| **Numérisation à la demande** | **JokkoRM** (le geste) | La demande et son exécution. Le **fichier** numérisé appartient au maître : il y est déposé et JokkoRM n'en garde que la référence (pattern 07, décision du 2026-11-25). L'original papier reste en magasin. |
| Identité, rôles, recherche, journal | **Les deux** | Un annuaire unique en SSO (Keycloak realm partagé), une seule barre de recherche (recherche fédérée), une vue consolidée des opérations (journal croisé). |

## Comment ce tableau est testé à la recette

À chaque livraison, le script `make acceptance-test` exécute une suite Playwright qui tente, pour chaque ligne, les cas suivants :

- **Ligne « Maître »** : essaie de modifier le champ côté JokkoRM → doit retourner `403 master-owned-field`.
- **Ligne « JokkoRM »** : ~~essaie de modifier le champ côté maître → doit retourner l'équivalent 403 côté maître~~.
  *Amendement retenu le 2026-11-23* : avec Maarch RM, un champ verrouillé (immuable) bloque aussi
  JokkoRM. Le champ d'affichage reste donc modifiable dans Maarch. La recette modifie ce champ à la
  main dans le maître et vérifie deux choses :
  - le **veilleur du journal** le rétablit en une minute environ, avec la valeur de JokkoRM, qui
    reste la seule source ;
  - un **signalement** nomme l'auteur et l'heure de la modification.
- **Ligne « Les deux »** : vérifie que la modification propagée par le SSO est visible des deux côtés dans les 30 secondes.

Un échec sur une ligne bloque la livraison. C'est la règle.
