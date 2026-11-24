# 12 — Mode magasin : les gestes au scan

> Dans les rayons, l'archiviste n'a pas d'ordinateur. Il a un téléphone ou une douchette,
> et des étiquettes partout : sur les boîtes et sur les tablettes. Chaque geste se fait
> en quelques scans, et JokkoRM vérifie chaque scan avant d'écrire quoi que ce soit.

## Le nombre de scans fait le geste

| Geste | Scans | Ce qui change |
|---|---|---|
| **Identifier** | 1, sans effet | Rien : « qu'est-ce que je tiens ? » (boîte ou emplacement, adresse, état) |
| **Arrivée** | 1 : la boîte | La boîte est reçue en **zone tampon** ; JokkoRM garde *qui* l'a reçue et *quand* |
| **Placer** | 2 : la boîte, puis l'emplacement | La boîte est rangée (ou déplacée) ; mêmes contrôles de place que l'écran (pattern 10) |
| **Retirer** | 3 : la boîte, son emplacement, la boîte encore | La boîte quitte sa tablette, repasse en zone tampon, sa place est libérée |

Le troisième scan du retrait est une **confirmation** : sortir une boîte d'un rayon est le
geste qui fait perdre une archive quand il est fait à moitié. Le scan de l'emplacement prouve
qu'on est devant la bonne tablette.

## Les étiquettes

- **Boîte** : son code-barres `BOX-AAMMJJ-XXXXXX` (pattern 06), Code128 par défaut, QR possible.
- **Emplacement** : un code `EMP-XXXXXXXX` attribué à la création de l'emplacement et **qui ne
  change jamais**, même si on renomme la travée. On peut réimprimer une étiquette sans recoller
  les autres. Planche Avery L7162 (16 par A4, 99,1 × 33,9 mm) à coller sur le chant de la tablette : QR pour le
  téléphone, Code128 pour les douchettes, code en clair et adresse lisible.
- Le code est cherché dans les boîtes **et** dans les emplacements : on ne devine pas d'après le
  préfixe, et un code qui désignerait les deux est refusé.
- Le contenu de l'étiquette est le code brut, jamais une adresse web : l'étiquette reste valable
  si le serveur change de nom.

## Règles

1. **Arrivée** : une boîte rangée n'« arrive » pas (on la retire d'abord) ; une boîte en prêt
   revient par l'enregistrement du retour, pas par une arrivée ; pointer deux fois de suite ne
   change rien et le dit. Après un retrait, la boîte peut être pointée à nouveau.
2. **Placer** : une simple *demande* de prêt laisse la boîte en rayon, on peut la déplacer ; une
   boîte *sortie* en prêt ne se déplace pas (sa fiche fantôme garde la place).
3. **Retirer** : si l'emplacement scanné n'est pas celui enregistré, le retrait est refusé et
   l'écran montre l'adresse enregistrée. Si la boîte était bien là en vrai, on corrige d'un
   geste « Placer ici » : le registre suit le terrain, jamais l'inverse en silence.
4. **Zone tampon** : toujours l'absence de position (pattern 06). L'alerte des 14 jours compte à
   partir de l'entrée dans la zone : création, arrivée pointée ou retrait, le plus récent.
5. **Traçabilité** : chaque geste entre dans la chaîne d'audit (`CONTAINER_RECEIVED`,
   `CONTAINER_PLACED` / `CONTAINER_MOVED`, `CONTAINER_REMOVED`) avec le nom de la personne ;
   un retrait prévient la GED maître, l'adresse de ses archives change.
6. **Droits** : les gestes sont réservés à l'archiviste et au responsable des archives
   (pattern 11) ; le code-barres d'une boîte ne se montre qu'au personnel des archives (pattern 08).

## Le téléphone

- Appli web installée sur l'écran d'accueil (« Magasin »), ouverte sur `/terrain`. Elle parle à
  l'instance JokkoRM d'où elle a été installée : un client sur Internet l'installe depuis la
  sienne.
- Elle marche sur un **réseau local sans Internet** : police, moteur de lecture (zxing-cpp en
  WebAssembly) et icônes sont servis par JokkoRM.
- La caméra d'un iPhone n'est ouverte qu'en **HTTPS avec un certificat de confiance** : voir
  `docs/OPERATIONS.md`, « Mode magasin sur iPhone ».
- L'écran dit toujours où on en est (étape, refus, réussite) ; les bips sont un plus, car le
  bouton silencieux de l'iPhone les coupe et Safari ne sait pas faire vibrer.
- Une douchette Bluetooth (clavier) ou la saisie à la main remplacent la caméra.
