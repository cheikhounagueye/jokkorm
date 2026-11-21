# 04 — Le dossier hybride : une cote, deux supports

> Pour l'agent, c'est un seul dossier. Le support n'est qu'une colonne de plus.

Un même dossier peut contenir des pièces nativement numériques et des pièces papier. L'agent qui le consulte ne doit pas avoir à savoir où est quoi : il cherche, il trouve, il demande.

## Exemple : marché public n° 2024-047

**Dossier** — Marché public n° 2024-047 — Fourniture de matériel informatique · Direction des moyens généraux

- **Cote** : `DMG/MP/2024/047`
- **Dates extrêmes** : 03.2024 — 11.2024
- **DUA** : 10 ans
- **Sort final** : Versement aux Archives nationales

### Pièces numériques — conservées côté maître

- Avis d'appel d'offres · PDF signé · scellé le 14.03.2024
- Procès-verbal d'ouverture des plis · PDF · scellé le 02.05.2024
- Notification d'attribution · PDF signé · scellé le 21.06.2024

Consultation immédiate en ligne, valeur probante garantie par le scellement.

### Pièces papier — suivies côté JokkoRM

- Contrat original signé et timbré · 2 exemplaires · boîte `DMG-2024-112`
- Cautionnement bancaire original · 1 pièce · boîte `DMG-2024-112`

**Emplacement physique** : Site Diamniadio · Magasin MAG-B · Travée 14 · Tablette 3 · Position 07 — code-barres `DMG2024112`.

## Ce que voit l'agent — un seul écran, un seul résultat

Le support n'est qu'une colonne de plus. L'agent ne choisit pas son outil.

| Pièce | Support | Action |
|---|---|---|
| Avis d'appel d'offres | Numérique | **Ouvrir** |
| Procès-verbal d'ouverture des plis | Numérique | **Ouvrir** |
| Notification d'attribution | Numérique | **Ouvrir** |
| Contrat original signé et timbré | Papier · MAG-B / 14 / 3 / 07 | **Demander la communication** |
| Cautionnement bancaire original | Papier · MAG-B / 14 / 3 / 07 | **Demander la communication** |

## Mécanique technique

- Le portail agent appelle `GET /api/v1/records/{cote}/consolidated` sur JokkoRM.
- JokkoRM retourne ses propres pièces papier (base locale) + appelle le `SaeMasterPort.fetchByCote` pour récupérer les pièces numériques du maître.
- La fusion se fait côté JokkoRM — un seul contrat pour le portail.
- Si le maître est injoignable, JokkoRM retourne ses propres pièces + un champ `master.status = unreachable` : le portail affiche un bandeau « contenu numérique temporairement indisponible », pas une page d'erreur.
