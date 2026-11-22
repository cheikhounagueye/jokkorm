# 06 — Provisionnement en masse des boîtes et retrouvage universel

> L'archiviste ne commande pas les boîtes une par une. Il en reçoit 500 d'un coup.
> Et quelle que soit l'étape du cycle de vie d'une boîte — en transit, rangée, en prêt,
> perdue, détruite — on doit pouvoir la retrouver instantanément.

Cette fiche cadre deux problèmes liés :

1. **Provisionnement en masse** — créer et étiqueter N boîtes efficacement, sans incohérence.
2. **Retrouvage universel** — quelle que soit la question posée (« où est la boîte 19944 »,
   « combien de boîtes en transit », « historique de cette boîte », …), on doit avoir un chemin.

Les deux sont indissociables : si on crée 500 boîtes mal identifiées, on ne les retrouve
plus. Si notre pattern de retrouvage est faible, la création en masse amplifie le chaos.

---

## Partie 1 — Le pattern de retrouvage universel

### Les 10 questions qu'on doit savoir poser au système

| # | Question opérationnelle | Point d'entrée technique |
|---|---|---|
| 1 | **J'ai une boîte en main, qu'est-ce que c'est ?** | `GET /containers/by-barcode` (douchette) |
| 2 | Où est la boîte `BOX-261122-JS0MG4` ? | même endpoint — retourne `positionId` ou null (transit) |
| 3 | Combien de boîtes sont dans la **zone tampon** ? | `GET /containers?inTransit=true` |
| 4 | Liste toutes les boîtes d'un **lot** | `GET /containers/batch/{id}/containers` |
| 5 | Liste toutes les boîtes d'un **magasin entier** (hiérarchique) | `GET /containers?underPath=DKR/MAG-B` |
| 6 | Où est le **dossier** `DMG/MP/2024/047` physiquement ? | `GET /records/consolidated?cote=X` → donne le `containerId` → chaîner |
| 7 | Historique **de la boîte** (tous mouvements de son contenu) | `GET /containers/{id}/movements` (nouveau 5M) |
| 8 | Historique **du dossier** (ses mouvements à lui) | `GET /records/movements?cote=X` (existe en 5D) |
| 9 | Boîtes **jamais vues depuis 6 mois** (dormantes ou perdues) | `GET /containers?inactiveSince=2026-05-22` (nouveau 5M.4) |
| 10 | **Recherche libre** par libellé partiel (« Marchés 2024 ») | `GET /containers?q=marchés+2024` (phase ultérieure, nécessite index full-text) |

### Les 4 clés d'entrée (ordre de priorité)

**1. Le code-barres scanné** — souverain. Toute boîte a un et un seul barcode immuable. C'est le chemin canonique. L'archiviste en magasin l'utilise pour 90 % de ses actions.

**2. La cote du dossier** — raccourci pour « où est le dossier X ? ». Chaîne : cote → record → `containerId` → container → `positionId` → position.path. 3 appels API, résultat clair.

**3. Le lot** — pour tracer un lot de provisionnement : « sur les 500 boîtes de la commande DMG-2026-11, combien sont déployées, combien encore en transit, combien pleines ? »

**4. Le filtre hiérarchique** — « toutes les boîtes rangées sous `DKR/MAG-B/14` » (toute la travée, toutes tablettes, toutes positions). Utile pour inventaire partiel, préparation de récolement.

### La cohérence multi-entrées

Toutes ces vues doivent **s'accorder** sur une seule vérité en base. Règles :

- Une boîte a **un emplacement à un instant t** (ou aucun = transit). Pas de duplication.
- Un mouvement physique n'est **jamais effacé** (table append-only phase 5D).
- Un barcode pointe toujours vers la même boîte, **même après destruction** (on garde la ligne `state=DESTROYED`, on ne la supprime pas).

Cette dernière règle est cruciale : si on scanne un barcode d'une boîte détruite, l'endpoint doit renvoyer 200 avec `state=DESTROYED` + date + motif. Pas 404. Ça évite qu'un opérateur pense qu'une boîte scannée n'existe pas alors qu'elle a été détruite (différence ÉNORME pour l'audit).

### Le cas « boîte perdue »

Scénario : la boîte `BOX-261122-JS0MG4` est en `STORED` à la position `DKR/MAG-B/14/3/07` depuis 3 ans, mais quand l'archiviste va la chercher, elle n'est plus là physiquement.

**Pattern** :
- La boîte reste à son statut `STORED` + position en base — on ne peut pas dire « elle n'est plus là » automatiquement parce que JokkoRM n'a pas d'yeux.
- L'archiviste **déclare** la perte via `POST /records/movements` avec `kind=EGRESS` et `motive="Introuvable récolement 2026-11"` + nouveau verbe `MISSING` dans `MovementKind` (phase 5N.2).
- Nouvel état `LOST` sur le container (phase 5N.2) : la boîte garde son barcode mais apparaît en rouge, exclusion des listes « disponibles » pour prêt.
- Récolement (phase 5F future) : lors du scan d'inventaire, les barcodes attendus et pas vus pendant N jours passent auto en `LOST`.

### Table de décision — « je cherche X, je vais où ? »

```
          ┌─ j'ai un barcode ────────→ GET /containers/by-barcode
          │
          ├─ j'ai une cote de dossier → GET /records/consolidated?cote
          │
          │                              ┌─ liste → GET /batch?status=PRINTED
          ├─ j'ai un lot ───────────────┤
          │                              └─ détail → GET /batch/{id}/health
Je cherche─┤
          │                              ┌─ zone tampon → GET /containers?inTransit
          ├─ un filtre structurel ──────┤─ sous un magasin → GET /containers?underPath
          │                              ├─ par état → GET /containers?state=SEALED
          │                              └─ dormantes → GET /containers?inactiveSince
          │
          └─ un historique ──────────────┤─ boîte → GET /containers/{id}/movements
                                         └─ dossier → GET /records/movements
```

---

## Partie 2 — Provisionnement en masse

### Le flux de vie d'un lot

```
  COMMANDE                   PROVISIONNEMENT              CONDITIONNEMENT
  ────────                   ───────────────              ───────────────
  500 boîtes            →    JokkoRM génère         →    Archiviste colle
  neuves reçues              500 barcodes uniques        une étiquette
  en zone de                 et imprime 500              sur chaque boîte
  réception                  étiquettes (PDF Avery)      physique
                                                                │
                                                                ▼
                             DÉPLOIEMENT                   VIE EN MAGASIN
                             ───────────                   ──────────────
                        ←    Archiviste scanne        →    Boîte en position,
                             chaque boîte en magasin       prête à recevoir
                             et la range à une             des dossiers
                             POSITION physique             (phase 5C+)
```

### Les 3 états d'une boîte vide

| État | Caractérisation | Rôle |
|---|---|---|
| **En transit** | `state=EMPTY` + `positionId=NULL` | Dans le sas. Étiquette collée, pas encore rangée. |
| **Rangée vide** | `state=EMPTY` + `positionId!=NULL` | En magasin, prête à recevoir des dossiers. |
| **Ouverte** | `state=OPEN` + `positionId!=NULL` | A commencé à recevoir des dossiers. |

**Pas de nouvelle table « zone tampon »**. La zone tampon n'est pas un lieu physique à modéliser dans la topographie — c'est l'absence de position. Cela évite d'inventer un nœud spécial qui polluerait l'arbre et les rapports.

### Le lot (`ContainerBatch`) — source de vérité du provisionnement

```
ContainerBatch {
  id                   UUID
  tenantId
  reference            "Commande DMG 2026-11"   — libre, par l'archiviste
  size                 500
  labelPattern         "Marchés publics DMG"    — libellé commun
  defaultCapacity      50                       — capacité commune
  status               CREATED → PRINTED → FULLY_DEPLOYED → ARCHIVED
  createdAt, createdBy
  firstPrintedAt, lastPrintedAt
}
```

Chaque `physical_container` gagne :
- `batch_id` **nullable** (null = création unitaire, phase 5B conservée)
- `sequence_in_batch` : entier 1..N dans le lot (phase 5N, pour affichage lisible)

### Invariants du lot

- `size == count(containers WHERE batch_id=this.id)` **pour toujours** (append-only)
- Un barcode n'est **jamais réémis**, même après destruction
- Statut `FULLY_DEPLOYED` quand tous les containers ont eu `positionId != NULL` au moins une fois
- Un lot peut être `ARCHIVED` mais **jamais supprimé** s'il contient au moins une boîte qui a servi
- `defaultCapacity`, `labelPattern` : copiés dans chaque container à la création, modifiables par la suite boîte par boîte (pas de propagation rétroactive)

---

## Partie 3 — Étude de faisabilité et de cohérence

### Ce qui existe déjà (acquis des phases 5A → 5L)

- ✅ Génération de barcode unique (`BarcodeGenerator` phase 5K)
- ✅ Table `physical_container` avec barcode unique par tenant
- ✅ Rangement individuel + audit (`StoreRecordInContainerUseCase`)
- ✅ Lookup par barcode (`FindContainerByBarcodeUseCase`)
- ✅ Rendu étiquette PNG configurable (Code128/QR, `BarcodeImageGenerator` phase 5L)
- ✅ Journal des mouvements par **record** (`PhysicalMovement` phase 5D)
- ✅ Audit chaîné HMAC pour toute transition

### Ce qu'il faut ajouter pour 5M

- ⚠️ Table `container_batch` + colonne `batch_id` sur `physical_container`
- ⚠️ Colonne `sequence_in_batch` sur `physical_container` (phase 5N)
- ⚠️ Use case `CreateContainerBatchUseCase` qui génère N barcodes en 1 transaction
- ⚠️ Endpoint `POST /containers/batch` + `GET /batch/{id}/health`
- ⚠️ Dépendance Apache PDFBox pour la génération PDF
- ⚠️ `BatchLabelsPdfGenerator` — rend un PDF Avery multi-pages
- ⚠️ Endpoint `GET /batch/{id}/labels.pdf?template=avery-l7160&symbology=CODE128`
- ⚠️ Frontend `/magasin/lots` + `/magasin/lots/[id]`

### Ce qu'il faut ajouter pour le retrouvage universel (futures phases)

- ⚠️ Endpoint `GET /containers?inTransit=true` pour la zone tampon
- ⚠️ Endpoint `GET /containers?underPath=X` pour filtre hiérarchique
- ⚠️ Endpoint `GET /containers/{id}/movements` pour l'historique boîte
- ⚠️ Enum `MovementKind.MISSING` + état `LOST` sur container (phase 5N.2)
- ⚠️ Scan passif → marquage « dernière vue » + détection dormantes
- ⚠️ Recherche full-text par libellé (phase ultérieure avec OpenSearch ou pg_trgm)

### Cohérence multi-angle — invariants à vérifier en CI

Chaque phase apporte de nouveaux endpoints et de nouveaux états. Pour éviter que ces
angles se contredisent, tests d'invariants à ajouter :

1. **Somme = 100 %** : `inTransit + stored + sealed + destroyed = total` pour un tenant.
2. **Pas de double position** : un container ne peut avoir qu'un `positionId` à la fois.
3. **Append-only movements** : trigger DB refuse UPDATE/DELETE sur `physical_movement`.
4. **Chaîne HMAC continue** : `verifyChain` sur chaque target doit retourner -1.
5. **Barcode unique** : UNIQUE(tenant_id, barcode) même pour les DESTROYED.
6. **Batch cohérent** : `batch.size == count(containers WHERE batch_id=batch.id)`.

Ces tests tournent en IT Testcontainers après chaque suite de création/modification.

---

## Partie 4 — Les 6 règles d'or du pattern

1. **Un barcode = une et une seule boîte, pour toujours.** Jamais réémis, même après destruction physique.
2. **Zone tampon = absence de position**, pas un lieu modélisé. `positionId=NULL` est la seule vérité.
3. **Lot source de vérité du provisionnement**, mais chaque boîte reste indépendante après création (rangement, remplissage, scellement = actions unitaires).
4. **Création bulk → déploiement individuel.** 500 boîtes créées en un clic, 500 scans un par un en magasin.
5. **Impression bulk → scan individuel.** PDF 500 étiquettes en un clic, douchette au magasin pour chaque boîte.
6. **Toute opération bulk est auditée au niveau du lot ET au niveau de chaque boîte** (double trace).

---

## Partie 5 — Anti-patterns à éviter

- ❌ **Ne jamais** créer un nœud topographique `ZONE-TAMPON` ou `TRANSIT` — ça polluerait récolement et rapports par magasin.
- ❌ **Ne jamais** permettre de modifier `batchId` d'une boîte après création (immuable comme le barcode).
- ❌ **Ne jamais** autoriser une transaction qui range 500 boîtes à la fois — garder l'opération unitaire, reflet de l'action physique.
- ❌ **Ne jamais** permettre de « vider » un lot (retirer des boîtes). Si une boîte meurt, elle reste dans le lot avec `state=DESTROYED`.
- ❌ **Ne jamais** renvoyer 404 sur un barcode valide dont la boîte a été détruite. Renvoyer 200 avec `state=DESTROYED` + motif.
- ❌ **Ne jamais** ré-indexer les séquences `sequence_in_batch` après suppression — si la boîte 023/500 meurt, 023 reste 023, il manque juste à l'appel.

---

## Partie 6 — Workflow typique bout-en-bout

```
[ARCHIVISTE]        [JOKKORM]
    |                  |
    | commande 500 ----|
    | boîtes neuves    |
    |                  |
    | POST /batch      |
    | count=500 ------>|
    |                  |-- génère 500 barcodes uniques
    |                  |-- INSERT 500 containers (positionId=NULL,
    |                  |     state=EMPTY, batch_id=X, sequence=1..500)
    |                  |-- audit CONTAINER_BATCH_CREATED
    |<-- {batch, 500 containers}
    |                  |
    | GET labels.pdf   |
    | ?template=avery  |
    |------------------|
    |                  |-- render PDF 24 étiquettes/page = 21 pages A4
    |                  |-- audit CONTAINER_BATCH_PRINTED
    |<-- PDF -----------|
    |                  |
    | [imprime, colle] |
    |                  |
    | reçoit les lots  |
    | physiquement     |
    |                  |
    | [parcourt magasin,|
    |  douchette en main]
    | scanne 1re boîte |
    |                  |
    | GET /by-barcode  |
    |------------------|
    |                  |-- retourne container inTransit=true
    |<-- {state:EMPTY, position:null, batch:...}
    |                  |
    | scanne code POSITION DKR/MAG-B/14/3/07
    | → PUT /{id}/position
    |------------------|
    |                  |-- place + INGRESS mouvement + audit
    |                  |-- batch.deployedCount++
    |                  |
    | ...répète 500 fois...|
    |                  |-- quand deployedCount == size :
    |                  |   batch.status = FULLY_DEPLOYED
    |                  |
```

---

## Partie 7 — Métriques de santé du système

Endpoint `GET /api/v1/physical/health/overview` (phase ultérieure) qui expose :

```json
{
  "containers": {
    "total": 12543,
    "inTransit": 123,
    "stored": 11890,
    "sealed": 420,
    "destroyed": 110,
    "lost": 0
  },
  "batches": {
    "total": 42,
    "fullyDeployed": 38,
    "inProgress": 4
  },
  "alerts": [
    {"kind": "DORMANT_TRANSIT",
     "message": "15 boîtes du lot DMG-2026-11 en transit depuis 45 jours",
     "severity": "warn"}
  ]
}
```

Alimente un dashboard de pilotage pour le responsable du magasin — vision 360° en un coup d'œil.

---

Cette fiche est la **constitution** du module physique. Tout PR qui créerait une
incohérence (nouveau chemin de retrouvage incomplet, violation d'invariant, nouveau
nœud `TRANSIT`, …) est rejeté d'office. Les 6 règles d'or sont le filet, les
anti-patterns sont l'étagère rouge.
