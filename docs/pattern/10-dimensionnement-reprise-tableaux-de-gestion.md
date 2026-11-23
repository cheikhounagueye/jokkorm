# Pattern 10 — Dimensionnement en mètres linéaires, reprise de l'existant, tableaux de gestion

**Statut** : constitutionnel, validé le 2026-11-23 · **Phase** : 5T · **Complète** : patterns 02, 06, 08, 09

> **Décisions du 2026-11-23** : tablette par défaut **100 cm**, marge de manipulation **5 %** ;
> format par défaut **dos 10 cm** ; formats à plat (registres, liasses, plans) en **v2** ;
> ordre **5T.A → 5T.C, puis interconnexion Maarch, puis 5T.F** (tableaux de gestion).

## 1. Le problème

Aujourd'hui, la capacité d'un emplacement est un **nombre de boîtes** (6 par défaut, phase 5S).
C'est faux dans la réalité d'un magasin :

- une tablette de 100 cm reçoit **10 boîtes dos 10** mais **6 boîtes dos 15** ou **5 boîtes dos 20** ;
- les archivistes comptent en **mètres linéaires (ml)**, pas en boîtes : c'est l'unité des
  rapports d'activité, des appels d'offres, des plans de conservation et des marchés de prestataires ;
- le mobilier varie (rayonnage fixe, mobile, armoire, meuble à plans, rack à palettes) et ses
  dimensions aussi, surtout en Afrique où il est souvent fabriqué localement ;
- un projet ne démarre jamais sur une salle vide : il faut **reprendre l'existant** (boîtes déjà
  rangées, inventaires Excel, fonds en vrac) ;
- le sort final d'une boîte dépend d'un **tableau de gestion** (DUA, sort final, communicabilité),
  qui doit avoir un seul propriétaire.

## 2. Décisions (ce que ce pattern fixe)

### 2.1 La longueur est la seule vérité de capacité

**Capacité = longueur utile, en millimètres entiers.** Le nombre de boîtes est toujours *dérivé*.

| Niveau | Donnée stockée | Règle |
|---|---|---|
| Tablette (RACK) | longueur utile (mm), hauteur libre (mm), profondeur (mm) | défaut hérité de la travée |
| Emplacement (POSITION) | longueur optionnelle (mm) | somme des emplacements ≤ longueur de la tablette |
| Boîte | format → dos (mm), profondeur, hauteur | v1 : boîtes debout uniquement |

**Occupation d'un emplacement** = somme des dos des boîtes ÷ longueur utile.
Affichage dérivé : « reste 46 cm = 4 boîtes dos 10 ou 3 boîtes dos 15 ».

**Chaîne de repli** pour la longueur utile d'un emplacement, dans cet ordre :

1. longueur saisie sur l'emplacement ;
2. longueur de la tablette ÷ nombre d'emplacements de la tablette ;
3. longueur de la travée ÷ nombre d'emplacements de la tablette ;
4. longueur par défaut (100 cm) ÷ nombre d'emplacements de la tablette.

**Reprise de la capacité en boîtes de la phase 5S** : la migration la convertit une fois en
longueur (capacité × dos du format par défaut, soit 6 × 100 mm = 600 mm par emplacement
existant ; la tablette reçoit la somme de ses emplacements). Les taux d'occupation existants
restent identiques, puis l'ancienne colonne disparaît.

**Jeu de manipulation** : une tablette pleine à 100 % est inutilisable (on ne peut plus sortir
une boîte). On réserve une marge configurable par tenant (défaut proposé 5 %, *à valider*).
Le taux affiché reste calculé sur la longueur totale ; la marge sert au contrôle de rangement.

**Contrôle de rangement** : refusé si le dos dépasse la longueur restante **ou** si la hauteur
de la boîte dépasse la hauteur libre de la tablette. Exception : la reprise (§ 4) signale la
surcharge comme **anomalie** au lieu de la refuser.

### 2.2 Un catalogue de formats de boîtes par tenant

| Champ | Exemple | Remarque |
|---|---|---|
| code, libellé | `CONS-25x33-D10` · « Boîte de conservation dos 10 » | |
| dos (mm) | 100 | longueur occupée sur la tablette |
| profondeur (mm) | 250 | doit tenir dans la profondeur de tablette |
| hauteur (mm) | 330 | doit tenir dans la hauteur libre |
| orientation | debout | `à plat` réservé à la v2 |
| qualité de conservation | carton neutre / standard / polypropylène | indépendant du format |
| poids max indicatif (kg) | facultatif | |
| actif | oui / non | un format retiré reste lisible sur les boîtes existantes |

**Catalogue proposé à l'installation** (modifiable) : boîtes 25 × 33 cm dos 8, 10, 15 et 20 cm,
gamme courante du marché. Le format par défaut du tenant (proposé : dos 10) est attribué aux
boîtes existantes lors de la migration, et proposé à la création d'un lot.

**Hors v1, explicitement** : registres, cartons de transfert stockés à plat, liasses, plans
roulés ou à plat. Ils demandent un modèle d'**empilement** (hauteur de pile vs hauteur libre),
différent du rangement debout. Les modéliser à moitié serait pire que d'attendre la v2.

### 2.3 Le mobilier : type et dimensions sur la travée

Un champ **type de mobilier** sur la travée (SHELF), avec ses valeurs par défaut :

| Type | Usage typique | Spécificité |
|---|---|---|
| Rayonnage fixe | magasins publics, Afrique et Europe | travées de longueur variable, simple ou double face |
| Rayonnage mobile (compactus) | magasins européens denses | une seule allée mobile ; plancher renforcé |
| Armoire | bureaux, petits services, archives courantes | portes, souvent verrouillée |
| Armoire forte / ignifuge | pièces sensibles | capacité faible |
| Meuble à plans | plans, cartes (v2 : à plat) | tiroirs |
| Rack à palettes | tiers archiveurs | conteneurs et palettes (v2) |

Dimensions sur la travée : longueur (mm), profondeur (mm), nombre de tablettes, faces
(simple/double), hauteur. Les tablettes en héritent, sauf surcharge locale (une tablette
basse plus haute, une tablette abîmée retirée…).

**Vocabulaire et adressage.** Dans la pratique : *salle → épi (rangée) → travée → tablette*.
Pour ne pas casser les chemins et les codes-barres existants, l'épi n'est **pas** un nouveau
niveau : c'est un attribut de la travée (code d'épi + ordre dans l'épi + face). L'adresse
affichée devient « Salle A · Épi 3 · Travée 12 · Tablette 4 » sans toucher au chemin stocké.
Par défaut, **une tablette = un emplacement** ; les sous-emplacements restent possibles.

### 2.4 La salle : dimensions réelles et limites

Sur le magasin (STOREHOUSE) : longueur, largeur, hauteur sous plafond (m), surface utile,
charge au sol admissible (kg/m²), consignes climatiques (plages de température et d'humidité
relative). Ces valeurs :

- alimentent la **visite 3D à l'échelle réelle** (épis, allées, emprise) ;
- déclenchent un avertissement si l'emprise du mobilier dépasse la salle ;
- permettent les indicateurs de densité (ml/m²) et de charge estimée.

Les consignes climatiques ne sont **pas codées en dur** : elles se configurent à partir de
l'ISO 11799 et des recommandations nationales (voir § 6), et le thermo-hygromètre (5S) les compare.

## 3. Indicateurs en mètres linéaires

| Indicateur | Calcul |
|---|---|
| ml installés | somme des longueurs utiles |
| ml occupés | somme des dos des boîtes rangées |
| ml libres / taux d'occupation | différence, ratio, par salle, épi, travée, tablette |
| capacité restante en boîtes | ml libres ÷ dos, pour chaque format actif |
| densité | ml installés ÷ surface de la salle |
| charge estimée | ml occupés × poids moyen au ml ÷ emprise ; comparée à la charge admissible |
| date de saturation | ml libres ÷ rythme moyen d'entrée sur les N derniers mois |

Le **poids moyen au ml** est un paramètre (ordre de grandeur commercial souvent cité :
~50 kg/ml, *à valider*). Toute alerte de charge au sol est **indicative** et renvoie vers un
bureau d'études structure.

## 4. Reprise de l'existant

### 4.1 Les situations rencontrées

| Situation | Fréquence | Traitement |
|---|---|---|
| Export d'un logiciel ou d'un tiers archiveur (CSV/Excel avec codes-barres, adresses, cotes) | Europe surtout | import direct avec correspondance de colonnes |
| Boîtes rangées mais inventaire Excel ou registre papier, étiquettes manuscrites | Afrique et Europe | import + campagne de réétiquetage + récolement |
| Mobilier non standard (fabrication locale, longueurs 90 ou 120 cm) | Afrique surtout | longueurs saisies par travée, générateur à gabarit |
| **Fonds en vrac** (liasses, dossiers non conditionnés, arriérés), mesurés en ml | Afrique très souvent | entité « fonds à traiter » (§ 4.3) |
| Absence d'inventaire | partout | métrage → conditionnement → étiquetage → récolement |

### 4.2 Le pipeline d'import

1. **Gabarit** à télécharger, avec feuille d'aide, comme l'import topographie (5I).
2. **Correspondance de colonnes** : ancien code-barres ou numéro prestataire → référence
   externe ; adresse texte (« Salle 2 / Épi 3 / Trav 12 / Tab 4 ») → chemin de topographie ;
   format ; cotes contenues ; service producteur ; dates extrêmes ; observations.
3. **Simulation à blanc** (aucune écriture) : rapport ligne par ligne avec erreurs bloquantes
   (adresse inconnue, format inconnu, doublon) et **anomalies non bloquantes** (surcharge,
   hauteur incompatible, boîte sans cote).
4. **Application** idempotente par référence externe : rejouer le même fichier ne duplique rien.
   Nouveaux codes-barres générés, boîtes regroupées dans un **lot « reprise »** pour imprimer
   les nouvelles étiquettes avec le PDF existant (5M), adresses inconnues en zone tampon.
5. **Récolement** (phase 5F) pour confirmer physiquement.

**Règle clé** : la reprise **accepte la surcharge** et la signale. Les magasins existants sont
souvent surchargés ; refuser bloquerait le projet dès le premier jour.

### 4.3 Fonds à traiter (vrac)

Entité dédiée, distincte des boîtes : salle ou zone, métrage estimé (ml), service producteur,
dates extrêmes estimées, état (à traiter, en cours, traité), et lien vers les lots de boîtes
produits par le traitement. Elle donne une vision honnête de l'arriéré et de son avancement.

### 4.4 Générateur de topographie à gabarit

« Créer 20 travées de 100 cm × 6 tablettes, rayonnage fixe double face, épis de 5 travées »
en une action. Sur un projet réel, personne ne saisit 120 tablettes une par une. L'import Excel
de topographie (5I) gagne les colonnes correspondantes : type de mobilier, longueur, profondeur,
nombre de tablettes, épi, face.

## 5. Tableaux de gestion

### 5.1 Règle de propriété (pattern 02, sans exception)

| Mode | Propriétaire des règles | Rôle de JokkoRM |
|---|---|---|
| Maître configuré (Maarch RM, générique) | **le maître** | miroir en **lecture seule**, avec date de synchronisation |
| Autonome (pas de maître) | **JokkoRM** | tableau local éditable, versionné, validé |

Jamais deux tableaux modifiables en même temps. Ce point conditionne l'interconnexion Maarch,
qui reste la priorité : le modèle local doit pouvoir être **alimenté** par les règles de
conservation de Maarch sans conversion de sens.

### 5.2 Modèle d'une règle

| Champ | Exemple |
|---|---|
| service producteur | Direction des ressources humaines |
| série / typologie (code) | Dossiers individuels du personnel |
| DUA | 5 ans (années + mois) |
| événement de départ | départ de l'agent · clôture du dossier · fin d'exercice · date de fin du contrat |
| sort final | conservation · destruction · tri (échantillonnage) |
| communicabilité | délai + événement de départ, ou « immédiate » |
| références réglementaires | texte libre |
| observations | texte libre |

Le **tableau** porte une version, un statut (brouillon, validé par l'autorité d'archives,
abrogé), la date et la référence du visa. Une règle abrogée reste lisible sur les boîtes qui
l'ont utilisée.

Les **délais de communicabilité ne sont jamais codés en dur** : ils dépendent de la loi de chaque
pays. Le tenant porte un profil national configurable.

### 5.3 Effets

- Une boîte au contenu mixte prend la **date d'éligibilité la plus tardive** de son contenu :
  aucune boîte n'est éliminée tant qu'un seul dossier doit être conservé.
- Listes « à éliminer » (vers le bordereau d'élimination, phase 5G) et « à verser ».
- Import d'un tableau existant depuis Excel ou Word converti, avec contrôle de cohérence.

## 6. Normes et références (vérifiées)

| Référence | Objet | Usage dans JokkoRM |
|---|---|---|
| **ISO 11799:2024** (3ᵉ édition ; l'édition 2015 est retirée) | exigences de stockage des documents d'archives et de bibliothèque | consignes climatiques et conditions de magasin, à configurer depuis le texte |
| **NF Z40-350 (2021)**, certification NF 342 | prestations d'archivage et de gestion externalisée de documents physiques (tiers archiveurs) | traçabilité, inventaire, récolement, reprise des fonds clients |
| **ISO 15489-1:2016** | principes du records management, dont le sort final | tableaux de gestion |
| Instruction DITN/RES/2004/001 (Archives de France) | charges au sol : 1 200 kg/m² en rayonnage fixe de 2,20 m, 1 700 kg/m² en mobile (jusqu'à 2 400), 900 kg/m² retenus pour le calcul | valeurs par défaut de contrôle, *à faire valider par un bureau d'études (Eurocodes)* |
| Instruction DITN/RES/2008/005 (Archives de France) | rayonnages de magasins d'archives (hypothèses de 5,5 tablettes en fixe, 6,5 en mobile) | gabarits par défaut du générateur |
| Lois nationales sur les archives, par exemple **loi n° 2006-19 du 30 juin 2006** (Sénégal) | cadre légal, calendriers de conservation, communicabilité | profil national du tenant |

Les seuils chiffrés non cités ci-dessus (climat, poids au ml, marges) restent des
**paramètres** et ne sont jamais présentés comme normatifs dans l'interface.

## 7. Afrique et Europe : ce qui change vraiment

| Sujet | Contexte africain fréquent | Contexte européen fréquent | Réponse du modèle |
|---|---|---|---|
| Climat | chaleur, humidité, risque de moisissures et d'insectes | magasins régulés | consignes et relevés par salle, alertes du thermo-hygromètre |
| Locaux | bureaux, couloirs, conteneurs, sous-sols inondables | bâtiments dédiés | dimensions et limites saisies par salle, avertissements |
| Mobilier | fabrication locale, longueurs hétérogènes, bois ou métal | standard, rayonnages mobiles | longueur par travée et par tablette, types de mobilier |
| Boîtes | approvisionnement variable, dos hétérogènes | catalogue normalisé | catalogue par tenant, format par boîte |
| Existant | arriérés en vrac, inventaires papier | exports de logiciels et de prestataires | fonds à traiter + import en deux passes |
| Tableaux de gestion | parfois absents ou non visés | validés, souvent dans un SAE | tableau local avec statut brouillon/validé, ou miroir du maître |
| Unités | système métrique (ml) | système métrique (ml) | millimètres en base, mètres linéaires à l'affichage |

## 8. Découpage

| Phase | Contenu | Dépendance |
|---|---|---|
| **5T.A** | catalogue de formats, format par boîte, occupation en longueur, chaîne de repli, migration (format par défaut aux boîtes existantes) | — |
| **5T.B** | type et dimensions du mobilier, épi/face, dimensions et limites de salle, contrôle longueur + hauteur | 5T.A |
| **5T.C** | générateur à gabarit, import topographie étendu | 5T.B |
| **5T.D** | indicateurs ml, date de saturation, visite 3D à l'échelle réelle par épis | 5T.B |
| **5T.E** | reprise : import en deux passes, anomalies, lot de reprise, fonds à traiter | 5T.A–C |
| **5T.F** | tableaux de gestion (local autonome, miroir du maître), éligibilité, listes d'élimination et de versement | interconnexion Maarch |

Les migrations sont **additives** (nouvelle migration 0013 et suivantes, la 0012 déjà
appliquée n'est pas modifiée) et la phase 5S reste fonctionnelle à chaque étape.

## 9. Décisions de mise en œuvre (5T.B, validées le 2026-11-23)

Le texte ci-dessus laissait plusieurs points ouverts. Voici ce qui a été retenu.

1. **Deux hauteurs sur la travée.** La *hauteur libre par tablette* (espace entre deux
   tablettes) sert au contrôle des boîtes et se transmet aux tablettes. La *hauteur du meuble*
   (hors tout) sert seulement à l'avertissement de plafond.
2. **Le nombre de tablettes n'est pas saisi** : il est compté dans l'arbre, pour qu'il n'y ait
   jamais deux chiffres contradictoires.
3. **La profondeur est contrôlée au rangement**, comme la longueur et la hauteur (§ 2.2 :
   « doit tenir dans la profondeur de tablette »).
4. **L'épi n'est qu'une adresse.** Code, rang et face A/B s'affichent (« Salle A · Épi 3 ·
   rang 2 · face A · Travée T12 ») ; chemins et codes-barres ne changent pas.
5. **Non mesuré = non contrôlé.** Aucune valeur par défaut de hauteur ou de profondeur : les
   salles existantes continuent de fonctionner tant qu'on ne les mesure pas.
6. **Les avertissements de salle ne bloquent jamais** (emprise estimée, plafond, épi plus long
   que la salle, doublon d'épi/rang/face).
7. **Aucune valeur préremplie** pour le climat, la charge au sol ou les dimensions (§ 6).
8. **Gel et numérisation** : simple avertissement (rien ne sort de la salle) ; prêt et
   extraction restent bloqués par un gel.

### Convention « double face » (5T.C, 5T.D) — validée le 2026-11-23

Un épi double face porte **une travée par rang et par face**. Les deux travées d'un même rang
ont le même code d'épi et le même rang, l'une face A, l'autre face B, et toutes deux
`faces = 2`. La **profondeur saisie est celle d'une face**, l'espace que voit une boîte.

```
            face A  →  T01   T02   T03   T04   T05      ← l'archiviste se tient dans l'allée A
  Épi E1   ═══════════════════════════════════════
            face B  →  T06   T07   T08   T09   T10      ← allée B
             rang :     1     2     3     4     5
```

Ainsi l'emprise au sol s'additionne sans double compte (A + B), et le contrôle de profondeur
reste exact. Exemple de générateur : 2 épis × 5 rangs × 2 faces = **20 travées**.

### Autres décisions validées le 2026-11-23 (5T.C, 5T.D)

- **Saturation, prudente** : une boîte déplacée à l'intérieur d'une salle compte comme une
  arrivée ; la date estimée est donc un peu plus proche. Calcul seulement après 90 jours
  d'historique réel, sur le rythme des 12 derniers mois.
- **Import Excel** : une case vide garde la valeur actuelle ; effacer une mesure se fait à
  l'écran. L'import est réservé à l'archiviste et au responsable des archives.
- **Charge au sol** : calculée seulement si le poids moyen au mètre linéaire est mesuré pour
  la salle ; résultat indicatif, à faire confirmer par un bureau d'études.

