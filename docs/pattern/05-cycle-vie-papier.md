# 05 — Le cycle de vie d'une archive papier

> De la clôture du dossier au sort final. À chaque étape, un responsable désigné.

Légende : **(M)** côté maître · **(J)** côté JokkoRM · **(2)** les deux, en relais.

## Les 5 étapes du cycle en magasin

| # | Étape | Qui | Détail |
|---|---|---|---|
| 1 | **Clôture du dossier** | M | Le courrier ou le dossier cesse d'être actif. Le maître lui attribue sa cote, sa DUA et son sort final. |
| 2 | **Versement physique** | 2 | Le service producteur remet son lot papier. JokkoRM enregistre le bordereau de versement et contrôle le contenu. |
| 3 | **Cotation et conditionnement** | J | Boîte neutre, étiquette, code-barres. La cote déjà émise par le maître est reprise telle quelle. |
| 4 | **Mise en magasin** | J | Un emplacement est affecté : site, magasin, travée, tablette, position. JokkoRM le renvoie au maître. |
| 5 | **Vie en magasin** | J | Consultations, prêts avec fantôme PDF+QR, retours, déplacements, récolements. Chaque mouvement est tracé dans la chaîne d'audit HMAC. |

## Règle : un dossier, une seule boîte (retenue le 2026-11-24)

Un dossier n'est jamais dans deux boîtes, et ne change jamais de boîte en silence.

- **Ranger un dossier déjà dans une autre boîte est refusé.** L'écran dit dans quelle boîte il se
  trouve et à quelle adresse (même libellé que dans la GED maître).
- **Changer de boîte** (reconditionnement) est un geste confirmé : « Transférer dans cette boîte ».
  Il est tracé comme un déplacement (`RELOCATE`, avec la boîte d'origine), et la nouvelle adresse
  part vers le maître.
- Le transfert est refusé si l'une des deux boîtes a un prêt en cours (demandé ou sorti) : son
  contenu est promis à l'emprunteur.
- Ranger une 2ᵉ fois dans la même boîte : « déjà dans cette boîte », rien n'est écrit.
- Après avoir posé une boîte sur une position, c'est elle qui est sélectionnée : le dossier ne part
  pas dans la boîte voisine.

Origine : le 2026-11-24, un dossier rangé par erreur dans une boîte de test a été « rangé » dans une
nouvelle boîte. JokkoRM l'avait déplacé sans le dire. Le journal gardait la trace, mais l'écran
annonçait un simple rangement.

API : `POST /api/v1/records/store?cote=…` corps `{ "containerBarcode": "…", "transfer": true }`.
Sans `transfer` → 409 `record-in-other-container` (`currentBarcode`, `currentLabel`,
`currentAddress`) ; même boîte → 409 `record-already-in-container` ; boîte en prêt → 409
`record-transfer-blocked`.

## Conditionner avant de placer (retenu le 2026-11-24, étude D1 et D4)

L'étape 3 vient avant l'étape 4 : on met les dossiers en boîte, puis on range la boîte.

- **À conditionner** (menu Salle d'archives) : les dossiers dans aucune boîte, enregistrés ou gelés,
  les plus anciens d'abord. Un dossier gelé y figure (son papier attend quelque part) mais n'entre en
  boîte qu'après la levée du gel ; la levée du gel d'un dossier sans boîte le remet « Enregistré ».
- **Une boîte encore en zone tampon** reçoit des dossiers. Leur adresse part au maître quand la boîte
  est placée ; d'ici là, la file d'envoi affiche « en zone tampon ». Le statut d'un dossier en boîte
  s'affiche « En boîte » : l'adresse se lit toujours sur la boîte.
- **Plusieurs dossiers d'un geste, tout ou rien** : au moindre refus rien n'est écrit, et chaque refus
  a sa raison (dossier inconnu, déjà en boîte, gelé ; boîte fermée, en prêt, trop pleine). Une trace
  d'audit et de journal par dossier, au nom de la personne.
- **Capacité (R4)** : une boîte ne dépasse pas son nombre maximal de dossiers. Le contrôle se fait sous
  verrou du contenu de la boîte : deux mises en boîte simultanées ne la font pas déborder.
- **Une boîte en prêt** (demandé ou sorti), **fermée ou détruite** ne reçoit plus de dossier, même au
  premier rangement.

API : `GET /api/v1/physical/conditioning/pending` ; `POST /api/v1/physical/conditioning` corps
`{ "containerBarcode": "…", "cotes": ["…"] }` → 409 `conditioning-refused` avec `refusals`
(`cote`, `reason`, `message` ; `cote` vide = la boîte). À l'unité, `POST /records/store` renvoie
409 `container-full`, `container-on-loan` ou `container-closed`.

## Échéance de la DUA

À l'échéance de la Durée d'Utilité Administrative, le maître prononce le sort final. **JokkoRM ne décide jamais seul du devenir d'une archive : il exécute, il trace, il atteste.**

## Les trois sorts finaux possibles

### A. Versement aux Archives nationales (M + J)

Le maître produit le bordereau SEDA. JokkoRM prépare physiquement le lot, le remet au transporteur et solde l'emplacement.

### B. Conservation définitive (J)

L'archive reste en magasin sans échéance. Elle entre dans le cycle de récolement patrimonial. Le maître la marque « conservation définitive » ; JokkoRM continue à la ranger, la récolement, la communiquer.

### C. Destruction contrôlée (M + J)

Le maître instruit et vise le bordereau d'élimination (potentiellement avec quorum N-eyes selon la politique tenant). JokkoRM exécute la sortie et joint l'attestation de destruction via `notifyDestructionExecuted`. L'original de l'attestation (PDF scellé HMAC) est archivé par le maître.

## Carte des événements de l'API

```
Étape                     Direction            Événement SaeMasterPort
─────────────────────────────────────────────────────────────────────
Clôture dossier           M → J                /api/events/master/cote-assigned
Versement physique        J (local)            POST /api/v1/records/{cote}/physical-receipt
Cotation                  J (local)            POST /api/v1/records/{cote}/container
Mise en magasin           J → M                notifyPhysicalLocation(...)
Prêt / retour / déplace   J → M                notifyMovement(...)
Gel contentieux           M → J                /api/events/master/legal-hold
Ordre de destruction      M → J                /api/events/master/disposal-ordered
Exécution destruction     J → M                notifyDestructionExecuted(...)
```

Chaque événement entrant est signé HMAC-SHA256 avec le secret partagé par tenant. Chaque événement sortant inclut la cote, le tenant, l'agent nommé et l'horodatage UTC. Le journal des actes consolidé est la vue croisée des deux flux.
