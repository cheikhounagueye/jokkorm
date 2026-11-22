# Le pattern « une seule archive, deux gardiens »

JokkoRM n'invente pas un modèle fonctionnel : il applique un pattern déjà présent dans les projets d'archivage mixte (papier + numérique). Il lui donne un nom, une forme, une règle d'or, et un contrat d'API. Les 5 pages qui suivent sont la **spécification de référence** à laquelle tout adapter (Maarch, Alfresco, Nuxeo, SI maison) doit se conformer.

| Page | Lit en 5 min |
|---|---|
| [01 — Une archive, deux gardiens](01-une-archive-deux-gardiens.md) | Vue d'ensemble : qui fait quoi, ce qui est partagé |
| [02 — Règle n°1 : un seul propriétaire](02-regle-un-seul-proprietaire.md) | La règle d'or qui empêche les contradictions |
| [03 — Qui détient quoi](03-qui-detient-quoi.md) | Tableau de répartition — spec d'acceptance |
| [04 — Le dossier hybride](04-dossier-hybride.md) | Une cote, deux supports, une seule vue |
| [05 — Cycle de vie d'une archive papier](05-cycle-vie-papier.md) | Les 5 étapes + 3 sorts finaux, qui pilote à chaque étape |
| [06 — Provisionnement masse + retrouvage universel](06-provisioning-masse-boites.md) | Lot, zone tampon, PDF Avery, les 10 chemins de retrouvage, 6 règles d'or |
| [07 — Demandes utilisateurs + workflow 4-eyes + lien Maarch](07-demandes-utilisateurs-multi-types.md) | 3 types (prêt/extraction/numérisation), rôles, 4-eyes, DIGITIZE sans stockage binaire |
| [08 — Recherche métier côté user, barcode côté archiviste](08-recherche-metier-vs-barcode-technique.md) | Formulaire critères métier, pas de barcode côté user (confidentialité), résolution cote→container en coulisse |

Ces pages sont destinées à un lecteur archiviste ou DSI, pas à un développeur. Pour la vue API, voir `docs/api/`. Pour les adapters, voir `docs/adapters/`.
