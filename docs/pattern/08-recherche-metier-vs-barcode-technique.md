# Pattern 08 — Recherche métier côté user, barcode côté archiviste

**Statut** : constitutionnel · **Auteur** : phase 5Q · **Supersede** : phase 5P `new` UI

## Problème

Dans la phase 5P, la saisie d'une demande d'accès commençait par :

> « Scanne ou saisis le code-barres de la boîte »

C'est **doublement incorrect** :

1. **Confidentialité** — le barcode est **anonyme par design** (`BOX-YYMMDD-xxxxxx`,
   Crockford base32). Son but, c'est précisément que l'emprunteur ne puisse pas
   mémoriser ni deviner ce que contient une boîte juste à son code. Un barcode
   affiché partout casse cette anonymisation.

2. **Ergonomie métier** — un agent DRH, juriste ou auditeur **ne connaît pas**
   le barcode. Il connaît le dossier qu'il cherche : *« le contrat de travail
   d'Awa Diop signé en 2015 »*, *« les marchés publics DMG 2024 Q2 »*, *« les
   procès-verbaux CA 2023 »*. Lui demander un barcode, c'est comme demander à
   un lecteur en bibliothèque la cote Dewey avant de pouvoir chercher un livre.

Comme sur **Maarch RM** où la recherche se fait via un formulaire structuré
(type de document, service producteur, dates extrêmes, mots-clés), l'UI JokkoRM
doit offrir la même ergonomie à l'utilisateur métier.

## Principe

**Deux modes de saisie coexistent selon le rôle** :

| Mode | Pour qui | Entrée principale | Entrée secondaire |
|------|----------|-------------------|-------------------|
| **Recherche métier** (défaut) | REQUESTER (DRH, juriste, auditeur) | Formulaire critères métier | — |
| **Scan barcode** (expert) | ARCHIVIST (quand il connaît déjà la boîte) | Code-barres direct | Recherche métier |

Le formulaire REQUESTER ne montre **jamais** de barcode. Il montre le **titre
du dossier**, son **service producteur**, sa **période**, sa **typologie** —
tout ce qui a du sens métier pour le demandeur.

Le barcode reste strictement interne : l'archiviste le voit à la qualification,
l'étiquette l'imprime, la douchette le lit. Mais côté user, c'est opaque.

## Flow recherche métier (pattern)

```
   Formulaire user           Backend                    Maître (Maarch)
   ─────────────────        ──────────                 ─────────────────
   service: DRH        →    MasterSearchService  →    SaeMasterPort.search(criteria)
   typologie: contrat        résout criteria          retourne List<ArchiveSummary>
   période: 2015-2020        appelle maître           (cote, titre, service, dates)
   mots-clés: Awa Diop
                        ←    Liste résultats    ←
   (plusieurs résultats)

   User sélectionne 1 résultat
                       →     resolve(cote)       →    fetchByCote(cote) si pas déjà en cache
                             résout container          (DUA, communicabilité…)
                             via physical_archive_record
                       ←     { archive, container }
   Clique « Demander »

   Saisit type (BORROW/EXTRACT/DIGITIZE)
   Saisit motif, pièces, durée…
   Submit → access_request créée avec container_id résolu
```

L'utilisateur ne voit **jamais** le barcode côté UI. S'il ouvre un onglet
inspecteur et lit le JSON de l'API, il le verra — mais ce n'est pas affiché
par défaut. Un vrai hardening (phase 4A OIDC + redaction côté serveur selon
rôle) bouchera ce trou.

## Critères de recherche supportés

Première version (phase 5Q) :

| Critère | Type | Exemple | Obligatoire ? |
|---|---|---|---|
| `freeText` | string | « Awa Diop contrat » | non |
| `service` | string (producteur) | « DRH », « Juridique » | non |
| `typologie` | enum | CONTRAT, BULLETIN_PAIE, MARCHE_PUBLIC, PROCES_VERBAL, FACTURE, DOSSIER_RH | non |
| `classificationPath` | string | « RH/Contrats/CDI » | non |
| `dateFrom` / `dateTo` | ISO date | 2015-01-01 → 2020-12-31 | non |
| `cote` | string | si l'user connaît | non |
| `limit` | int | 20 par défaut, max 100 | non |

Au moins **un critère** doit être rempli. Sinon l'API refuse (évite le
« donne-moi tout »).

## Modèle de résultat

```java
record ArchiveSearchResult(
    String cote,                // "RH/CDI/2015/047"
    String title,               // "Contrat de travail Awa Diop (signé 2015-03-12)"
    String service,             // "DRH"
    String typologie,           // "CONTRAT"
    String classificationPath,  // "RH/Contrats/CDI"
    LocalDate dateStart,
    LocalDate dateEnd,
    boolean availableInPhysical, // est-ce qu'une boîte physique existe pour cette cote ?
    int duaRemainingYears,       // ex. DUA 10 ans, reste 2 ans
    boolean communicable         // selon règles Maarch
) {}
```

Si `availableInPhysical = false`, l'UI affiche le résultat mais désactive le
bouton « Demander » (archive numérique only, pas de boîte physique à prêter).

Si `communicable = false`, même chose + message « accès restreint » — l'user
peut quand même faire une demande justifiée qui remontera au responsable.

## Pattern « 2 gardiens » respecté

**JokkoRM ne stocke pas les critères métier.** Toutes les données `service`,
`typologie`, `classificationPath`, `dateExtremes`, `DUA`, `communicabilité`
restent **chez le maître** (Maarch RM). JokkoRM se contente de :

- Transmettre les critères au maître
- Recevoir la liste
- Résoudre `cote → container_id` localement (c'est la table de correspondance
  physique qu'on possède, le maître ne la connaît pas)
- Renvoyer au frontend

Aucune duplication. Aucun risque de divergence. Le jour où Maarch change sa
taxonomie des typologies, c'est transparent pour JokkoRM — il relaie.

## Résolution cote → container

Un dossier logique (cote) peut être réparti sur :
- **1 boîte** (cas typique)
- **0 boîte** (archive numérique pure)
- **N boîtes** (dossier volumineux)

Donc :

```sql
SELECT pc.id, pc.barcode, pc.label, pc.state
FROM physical_container pc
JOIN physical_archive_record par ON par.container_id = pc.id
WHERE par.tenant_id = :tenant AND par.cote = :cote
```

Si 0 → `availableInPhysical = false` dans le résultat.
Si N → l'UI affiche le choix « cette archive est répartie sur N boîtes,
laquelle veux-tu ? » (nouveau modèle de demande groupée à prévoir en 5R).

Pour le MVP 5Q : une archive = au plus une boîte. On raffine plus tard.

## Scan barcode reste disponible pour l'archiviste

Dans `/demandes/new`, si le rôle courant est **ARCHIVIST** ou
**ARCHIVES_HEAD**, on affiche un **toggle** « Mode expert : saisir le
code-barres directement ». Utile quand l'archiviste prépare une demande pour
quelqu'un d'autre et qu'il a la boîte sous les yeux.

Pour REQUESTER, le toggle n'apparaît pas — la recherche métier est le seul
chemin.

## Confidentialité : le barcode n'apparaît jamais côté user

- Formulaire recherche : critères métier purs
- Table résultats : titre, service, période — **pas de barcode**
- Fiche demande créée : titre + statut — **pas de barcode**
- Mail de notification : « votre demande concernant [titre] » — **pas de barcode**

Côté archiviste et head, le barcode apparaît naturellement dans la vue
qualifier/approuver/exécuter. C'est là que c'est utile pour prendre la boîte.

## Découpage sous-phases

| Phase | Scope |
|---|---|
| **5Q.A** | Fiche pattern 08 (fait) |
| **5Q.B** | Domain : étendre SaeMasterPort avec `search(criteria)` + types ArchiveSearchCriteria, ArchiveSearchResult + demo adapter qui génère des résultats plausibles |
| **5Q.C** | Backend : `MasterSearchService` + résolution cote → container via `physical_archive_record` + endpoint `POST /api/v1/master-search` |
| **5Q.D** | Frontend : nouveau composant `ArchiveSearchForm` + remplace scan dans `/demandes/new` pour REQUESTER. Toggle « mode expert » pour ARCHIVIST |
| **5Q.E** | Fiche demande → affiche titre+service, pas barcode côté user. Archiviste voit tout |
| **5Q.F** (plus tard) | Vraie recherche sur Maarch RM via adapter Medona (dépend 5P.K) |
