# Pattern 09 — Design system + visualisation salle d'archives

**Statut** : constitutionnel · **Auteur** : phase 5R · **Supersede** : UI phases précédentes

## Problème

L'UI actuelle est **fonctionnelle mais moche** : inline styles éparpillés, émojis
en guise d'icônes, pas de hiérarchie visuelle claire, tables sans style, pas de
métaphore pour le magasin physique qui est pourtant le cœur métier du produit.

Un archiviste passe sa journée dans le produit. Il faut que ce soit **beau,
rapide, dense mais aérés** — comme Linear, Notion, Supabase.

Et comme l'application gère un **espace physique réel** (sites, magasins,
travées, tablettes, positions, boîtes), l'UI doit refléter cette réalité :
**l'archiviste doit voir son magasin comme s'il y était**.

## Direction design

### Langage visuel

| Dimension | Choix |
|---|---|
| **Typographie** | Inter (system fallback) — tracking serré, hiérarchie claire |
| **Iconographie** | [Lucide](https://lucide.dev) — 1200+ icônes SVG, stroke 1.75px cohérent |
| **Couleurs base** | Navy `#1e2d5e` (actions), warm paper `#faf8f4` (surfaces), carton `#a07b00` (archive feel) |
| **Dark mode** | Natif via `prefers-color-scheme` + toggle |
| **Espacement** | Grille 4px (padding, gap, radius multiples de 4) |
| **Radius** | 6px défaut, 10px cartes, pill pour badges |
| **Shadows** | 3 niveaux : `sm` subtle, `md` cartes, `lg` modales/drawers |
| **Animations** | 150-200ms ease-out transitions, pas d'effets gratuits |

### Principes

1. **Icône > label** quand possible. Tooltips Lucide natifs via `title`.
2. **Moins de texte, plus de données visibles**. Status = badge couleur, pas
   "Status: Approuvé" en texte plein.
3. **Hiérarchie typo stricte** : h1 24px, h2 18px, h3 14px uppercase tracker,
   body 14px, caption 12px.
4. **Densité maîtrisée** : tables compactes mais lisibles, cartes avec padding
   généreux.
5. **Dark mode first-class** — pas un afterthought.
6. **Pas d'emojis** dans les primitives UI (gardés pour décoration ou feedback
   utilisateur explicite).

### Architecture technique

```
apps/web/src/
├── lib/
│   ├── theme.ts             design tokens (colors, spacing, typography, shadows, radius)
│   └── cn.ts                className merger (tiny util)
├── components/
│   ├── ui/                  primitives sans logique métier
│   │   ├── Button.tsx       variants {primary, secondary, ghost, danger} × sizes {sm, md, lg}
│   │   ├── Card.tsx
│   │   ├── Badge.tsx        avec couleurs sémantiques
│   │   ├── Input.tsx
│   │   ├── Select.tsx
│   │   ├── Table.tsx
│   │   ├── EmptyState.tsx
│   │   ├── Drawer.tsx       side drawer latéral
│   │   ├── Modal.tsx
│   │   ├── Tooltip.tsx
│   │   ├── KpiCard.tsx      pour dashboards
│   │   └── Icon.tsx         wrapper Lucide
│   └── layout/
│       ├── AppShell.tsx     sidebar + topbar + main
│       ├── Sidebar.tsx
│       └── TopBar.tsx
└── app/
    ├── globals.css          CSS variables + dark mode
    └── ...
```

### Palette sémantique (tokens)

```css
:root {
  /* Surface */
  --surface-page:    #faf8f4;
  --surface-card:    #ffffff;
  --surface-elevated:#ffffff;
  --surface-sunken:  #f3efe7;
  --border-subtle:   #e8e2d5;
  --border-strong:   #d4ccb8;

  /* Text */
  --text-primary:    #1a1f2e;
  --text-secondary:  #5c6475;
  --text-tertiary:   #94a0b8;
  --text-on-brand:   #ffffff;

  /* Brand */
  --brand-50:  #eef2ff;
  --brand-100: #d8e1ff;
  --brand-500: #1e2d5e;
  --brand-600: #182547;
  --brand-700: #121a35;

  /* Status */
  --success-50:  #e8f5e9; --success-500: #2e7d32; --success-700: #1b5e20;
  --warning-50:  #fff8e1; --warning-500: #f59e0b; --warning-700: #b45309;
  --danger-50:   #fef2f2; --danger-500:  #dc2626; --danger-700:  #991b1b;
  --info-50:     #eff6ff; --info-500:    #3b82f6; --info-700:    #1d4ed8;

  /* Archive feel (paper, box, kraft) */
  --paper-500:   #a07b00;
  --kraft-500:   #8c5a2c;
}

:root[data-theme="dark"] {
  --surface-page:    #0f1420;
  --surface-card:    #1a2132;
  --surface-elevated:#232c42;
  --surface-sunken:  #0b0f18;
  --border-subtle:   #2a324a;
  --border-strong:   #3a435f;
  --text-primary:    #e8ecf5;
  --text-secondary:  #8c96ab;
  --text-tertiary:   #5a6378;
  /* brand/status restent pareils mais on utilise -600 pour meilleur contraste */
}
```

## La « salle d'archives virtuelle »

Pas de WebGL 3D complexe — une **vue isométrique SVG** performante et
responsive. L'archiviste navigue par **drill-down** :

```
   /magasin
   ┌──────────────────────────────────────────────────┐
   │  🏢 Sites (vue plan)                              │
   │  ┌─────────┐  ┌─────────┐  ┌─────────┐         │
   │  │  DKR    │  │  THI    │  │  ZIG    │         │
   │  │  2 mag  │  │  1 mag  │  │  0 mag  │         │
   │  └─────────┘  └─────────┘  └─────────┘         │
   └──────────────────────────────────────────────────┘

   Click DKR →
   /magasin/DKR
   ┌──────────────────────────────────────────────────┐
   │  🏢 DKR / Magasins   📊 500 boîtes sur 800 cap    │
   │  ┌──────────┐  ┌──────────┐                      │
   │  │  MAG-01  │  │  MAG-02  │                      │
   │  │ ▓▓▓▓▓░░ │  │ ▓▓░░░░░░ │                      │
   │  │ 60%      │  │ 25%      │                      │
   │  └──────────┘  └──────────┘                      │
   └──────────────────────────────────────────────────┘

   Click MAG-01 →
   /magasin/DKR/MAG-01
   ┌──────────────────────────────────────────────────────────┐
   │  🏢 DKR / MAG-01 / Travées                                │
   │  Vue isométrique SVG — on voit les travées en 3D dépliée │
   │                                                            │
   │     ┌─┬─┬─┬─┐  ┌─┬─┬─┬─┐  ┌─┬─┬─┬─┐                   │
   │   A │▓│▓│░│░│  │▓│▓│▓│░│  │░│░│░│░│  (couleur = taux    │
   │     └─┴─┴─┴─┘  └─┴─┴─┴─┘  └─┴─┴─┴─┘   remplissage)      │
   │   B │▓│▓│▓│▓│  ...                                        │
   │                                                            │
   │     Hover tablette → tooltip : "Tablette A2, 3/4 pos"    │
   │     Click → zoom sur la tablette                          │
   └──────────────────────────────────────────────────────────┘

   Click tablette →
   Vue linéaire des positions avec boîtes
   ┌──────────────────────────────────────────────────┐
   │  🏢 DKR / MAG-01 / A / 2   • 3 positions        │
   │                                                    │
   │  [BOX] [BOX] [BOX] [___]                          │
   │   📦    📦    🔒    vide                          │
   │                                                    │
   │  Click boîte → Drawer latéral avec détails       │
   │    - cote, archives dedans                        │
   │    - prêts passés / actifs                        │
   │    - actions (ranger, prêter, numériser)          │
   └──────────────────────────────────────────────────┘
```

### Codes couleur sur les boîtes

| État | Couleur | Label |
|---|---|---|
| EMPTY (vide rangée) | gris clair | « Disponible » |
| OPEN (ouverte avec dossiers) | vert menthe | « Active » |
| SEALED (scellée) | ambre | « Scellée » |
| DESTROYED | rouge barré | « Détruite » |
| LOANED_OUT (prêtée) | orange avec icône flèche | « Sortie » |
| STALE (en transit > 14j) | rouge avec warning | « Zone tampon » |

### Interactions

- **Hover** sur un nœud → tooltip avec stats + nombre d'enfants
- **Click** → drill-down
- **Right-click / ⌘ click** → menu contextuel (renommer, supprimer, imprimer)
- **Drag** d'une boîte vers une position → `PUT /containers/{id}/position`
- **Double-click sur une boîte** → ouvre le drawer détails
- **Filtres en haut** : état, dates, prêts actifs uniquement

## Dashboards par rôle (page d'accueil `/`)

### REQUESTER (home)
- **Mes demandes en cours** (KpiCard avec compteur par statut)
- **Mes archives récemment consultées** (list avec lien direct)
- **Nouvelles demandes** (CTA gros)

### ARCHIVIST (home)
- **File à qualifier** (compteur rouge si > 5)
- **File à exécuter** (compteur)
- **Prêts actifs / en retard** (compteur rouge si overdue)
- **Zone tampon** (alerte orange si > seuil)
- **Taux d'occupation magasin** (barre + %)

### ARCHIVES_HEAD (home)
- Tout ce que voit l'ARCHIVIST +
- **File à approuver** (compteur)
- **KPIs globaux** : total archives, prêts du mois, destructions pending
- **Graphe mouvements 30 jours** (petit sparkline)

### AUDITOR (home)
- **Vue historique complète** (timeline + filtres)
- **Export audit** (bouton)

## Découpage sous-phases

| Phase | Scope | Priorité |
|---|---|---|
| **5R.A** | Design system foundation : tokens CSS, Lucide installé, primitives UI (Button, Card, Badge, Input, Table, EmptyState, Icon) | 🔴 fondation |
| **5R.B** | Layout refactor : AppShell (sidebar + topbar), RoleSwitcher refactor en dropdown | 🔴 structurel |
| **5R.C** | Home dashboards par rôle avec KpiCard + sparklines minimalistes | 🔴 valeur immédiate |
| **5R.D** | Refonte `/demandes` + `/demandes/new` + `/demandes/qualifier` avec le nouveau design | 🟡 |
| **5R.E** | Refonte `/magasin` + `/magasin/lots` + lots détail | 🟡 |
| **5R.F** | **Salle d'archives virtuelle** : vue isométrique SVG, drill-down, drag-drop boîtes | 🟢 wow-factor |
| **5R.G** | Dark mode toggle + sauvegarde préférence | 🟢 |
| **5R.H** | Mobile responsive (archiviste sur tablette avec douchette Bluetooth) | 🟢 |

## Principes d'acceptance

1. **Avant / après** sur chaque écran refactored : captures obligatoires dans le
   PR.
2. **Lighthouse perf ≥ 90** sur toutes les pages — pas de dégradation.
3. **Accessibilité WCAG AA** : contraste, focus visible, labels ARIA.
4. **Pas de régression fonctionnelle** : tous les flows existants (workflow 4-eyes,
   création lot, import Excel) continuent de marcher.
5. **Dark mode** sur chaque écran dès 5R.G.
