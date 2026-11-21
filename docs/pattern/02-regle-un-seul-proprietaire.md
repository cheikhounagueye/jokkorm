# 02 — Règle n°1 : une donnée, un seul propriétaire

> Deux applications qui gèrent la même archive finissent toujours par se contredire. Cette règle l'empêche.

**Pour chaque information du dispositif, un et un seul des deux outils est désigné maître.** Lui seul peut la créer et la modifier. L'autre la reçoit, l'affiche, s'en sert — mais ne la touche jamais. Si les deux affichent un jour des valeurs différentes, **c'est la valeur du maître qui est vraie**, sans discussion.

## Trois conséquences à vérifier à la recette

### 1. Rien ne se modifie côté JokkoRM pour une donnée dont le maître est propriétaire

Aucun écran JokkoRM ne permet de changer une DUA, un sort final ou une position dans le plan de classement. Ces champs y sont affichés en lecture seule, visiblement grisés. Toute tentative d'appel API qui les modifierait retourne `403 Forbidden` avec le code `master-owned-field`.

### 2. Rien ne se saisit côté maître pour une donnée dont JokkoRM est propriétaire

Aucun écran maître ne permet de taper à la main un emplacement de magasin, un code-barres de boîte, un mouvement physique. Cette information arrive de JokkoRM (via `notifyPhysicalLocation`, `notifyMovement`) et n'est pas éditable côté maître. La table de répartition (voir page 03) engage le maître sur ce point.

### 3. Un champ nouveau attend sa décision

Si une information ne figure dans aucune ligne du tableau de répartition, aucun des deux outils n'en est propriétaire. **On ne la crée nulle part** tant que le comité n'a pas dit lequel la porte. C'est la règle qui empêche les deux outils de se contredire par dérive silencieuse.

## Pourquoi c'est critique

Sans cette règle, un agent corrige une date d'un côté, personne ne la corrige de l'autre, et six mois plus tard nul ne sait laquelle fait foi. En audit (CGDA, Archives nationales, Cour des comptes), un dispositif qui ne peut pas désigner qui détient quoi est un dispositif qui perd sa valeur probante.

## Mise en œuvre technique

- Côté JokkoRM : les champs lecture seule sont marqués dans l'entité domaine par un commentaire `// MASTER-OWNED` et dans le DTO REST par un champ `@JsonProperty(access = READ_ONLY)`.
- Côté REST : tout endpoint PUT/PATCH qui touche un champ master-owned retourne `403` avec `problem+json` `type=https://jokkorm.io/errors/master-owned-field`.
- Côté UI : les champs master-owned sont rendus avec `disabled`, en gris typographique défini par le design system (`var(--color-text-master-owned)`).
- Côté audit : chaque tentative est loggée avec l'action `MASTER_OWNED_REJECTED` et l'identifiant de l'appelant.
