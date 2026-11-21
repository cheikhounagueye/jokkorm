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
