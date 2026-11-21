# 01 — Une seule archive, deux gardiens

> Chaque information a un maître, et un seul.

Deux applications gèrent la même archive. La GED/SAE (le **maître** : Maarch RM, Alfresco, Nuxeo, ou un SI maison) et JokkoRM (le **module physique**). Pour ne pas se contredire, elles se partagent les responsabilités selon une règle unique : **une donnée, un seul propriétaire**.

## Les trois colonnes

| Côté maître (existant) | Partagé | Côté JokkoRM (module physique) |
|---|---|---|
| Plan de classement — arborescence de référence | **La cote** — identifiant unique du dossier, portée par le maître et reprise telle quelle par JokkoRM | Magasins et emplacements — site, magasin, travée, tablette, position |
| Durées de conservation et sort final — DUA, point de départ, décision | Le dossier hybride — une pièce papier + une pièce numérique sous la même cote | Contenants et cotation — boîtes, étiquettes, codes-barres, dimensions |
| Scellement et preuve — empreinte, horodatage des documents numériques | L'identité des agents — un seul compte, un seul mot de passe (SSO Keycloak) | Versements physiques — réception des lots papier, contrôle, prise en charge |
| Versement aux Archives nationales — bordereaux SEDA | La recherche — une seule barre, les deux fonds interrogés | Récolement — inventaire périodique, détection des écarts, plan de résorption |
| Décision de destruction — bordereau d'élimination opposable | Le gel contentieux — prononcé côté maître, bloque la sortie côté JokkoRM | Mouvements et communication — prêts, retours, déplacements, fantômes PDF+QR, relances |
| Communication des pièces numériques — consultation en ligne | Le journal des actes — chaque opération tracée, horodatée, attribuée | Numérisation à la demande — copie de consultation, l'original reste en magasin |

## Les cinq points à retenir

1. **Le maître décide** — plan de classement, DUA, scellement, versement aux Archives nationales, destruction. Rien de tout cela ne se décide ailleurs.
2. **JokkoRM range** — magasins, boîtes, étiquettes, réception des versements papier, récolement, prêts et retours.
3. **La cote fait le lien** — identifiant unique du dossier (type `DMG/MP/2024/047`). Le maître la porte, JokkoRM la reprend telle quelle. Sans elle, impossible de relier le dossier numérique au carton.
4. **Ce qui est partagé** — le dossier hybride, l'identité des agents, la recherche, le gel contentieux et le journal des actes.
5. **En cas d'écart** — l'outil qui n'est pas maître affiche la donnée sans pouvoir la modifier. C'est la valeur du maître qui fait foi.

## Règle d'or

> Aucune information n'est saisie deux fois. Si le maître détient la donnée, JokkoRM la lit sans jamais la modifier. Si JokkoRM la détient, le maître la reçoit en retour. Le jour où les deux outils divergent, c'est **le maître désigné qui fait foi**.

Voir [02 — Règle n°1 : un seul propriétaire](02-regle-un-seul-proprietaire.md) pour les trois conséquences concrètes à vérifier à la recette.
