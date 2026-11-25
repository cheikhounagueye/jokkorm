# Pattern 07 — Demandes utilisateurs multi-types + workflow 4-eyes + lien Maarch

**Statut** : constitutionnel · **Auteur** : phase 5P · **Supersede** : phase 5E (à refactor)

## Problème

Le prêt actuel (phase 5E) est **saisi par l'archiviste lui-même** via `/prets/new`.
C'est irréaliste : en pratique les demandes viennent des **services utilisateurs**
(DRH, Juridique, Finances, Audit…), l'archiviste est le **traitant**, pas le
demandeur. Et il y a **plusieurs types de besoins** — pas seulement "prêter la
boîte entière".

De plus, un prêt (surtout sur document sensible) exige traditionnellement une
**validation hiérarchique** (4-eyes) avant allocation : l'archiviste qualifie,
le responsable des archives confirme.

## 3 types de demandes

| Type | Code | Description | Résultat physique |
|------|------|-------------|---|
| **Prêt boîte** | `BORROW_BOX` | L'emprunteur veut la boîte entière pour consultation étendue (audit RH sur 3 mois, instruction juridique) | Boîte sortie + fantôme à sa place |
| **Extraction pièces** | `EXTRACT_PIECES` | L'emprunteur veut 2-3 documents précis (un contrat, un bulletin de paie) | Documents sortis, boîte reste rangée, fantôme interne |
| **Numérisation extraits** | `DIGITIZE_EXTRACT` | L'emprunteur veut consulter à distance, pas besoin de physique → on numérise les pièces demandées | Boîte sort temporairement, numérise, remise en place, **lien Maarch envoyé** |

## Workflow unifié (états)

```
                     ┌──────────────┐
        (demandeur)  │   DRAFT      │  Le demandeur rédige sa demande
                     └──────┬───────┘
                            │ submit
                            ▼
                     ┌──────────────┐
      (archiviste)   │  SUBMITTED   │  En attente de qualification archiviste
                     └──────┬───────┘
                            │ qualify (check dispo boîte)
                            ├─────→ reject → REJECTED  (motif)
                            ▼
                     ┌──────────────┐
      (resp.arch.)   │  QUALIFIED   │  En attente d'approbation hiérarchique
                     └──────┬───────┘
                            │ approve
                            ├─────→ reject → REJECTED  (motif)
                            ▼
                     ┌──────────────┐
      (archiviste)   │  APPROVED    │  Prête à exécution
                     └──────┬───────┘
                            │ fulfill (sortir boîte / numériser / extraire)
                            ▼
                     ┌──────────────┐
                     │  FULFILLED   │  Boîte sortie ou lien envoyé
                     └──────┬───────┘
                            │ complete (retour boîte / confirmation accès)
                            ▼
                     ┌──────────────┐
                     │  COMPLETED   │  Clôturé (terminal)
                     └──────────────┘

      Plus : CANCELLED (par demandeur avant SUBMITTED ou APPROVED)
             REJECTED (par archiviste ou responsable)
```

## Rôles et responsabilités

| Rôle | Peut faire | Visibilité |
|---|---|---|
| **REQUESTER** (tout utilisateur) | Créer DRAFT, soumettre, suivre ses propres demandes | Ses propres demandes uniquement |
| **ARCHIVIST** | Qualifier (dispo) ou rejeter les SUBMITTED, exécuter les APPROVED | Toutes les demandes du tenant |
| **ARCHIVES_HEAD** (responsable) | Approuver ou rejeter les QUALIFIED | Toutes les demandes du tenant + dashboard global |
| **AUDITOR** (lecture seule) | Consulter l'historique | Toutes les demandes |

**4-eyes strict** : ARCHIVIST ne peut pas s'auto-approuver (qualifier ≠ approuver,
même personne autorisée uniquement si ARCHIVES_HEAD distinct dans le tenant).

## Qualification — check de disponibilité

À l'étape `SUBMITTED → QUALIFIED`, l'archiviste coche ou rejette. L'UI lui
affiche automatiquement :

| Contrôle | Signal | Action |
|---|---|---|
| Boîte existe en base | ❌ DESTROYED | Reject auto, motif "boîte détruite" |
| Pas déjà en prêt actif | ⚠️ si LOAN actif | Reject auto, motif "déjà prêtée à X" |
| Pas en zone tampon (si rangement attendu) | ⚠️ positionId IS NULL | Warning, l'archiviste peut quand même qualifier (ex. numérisation possible sur boîte en transit) |
| DUA pas expirée (via Maarch) | ⚠️ si expirée | Warning, suggestion destruction préalable |
| Communicabilité OK (via Maarch) | ❌ si NON communicable | Reject auto, motif "accès restreint" |
| Classification du demandeur suffisante (future) | ❌ | Reject auto, motif "habilitation insuffisante" |

Ces checks sont **informatifs** côté archiviste, mais certains sont **bloquants**
(destruction, communicabilité) — l'UI refuse de valider.

## Exécution (FULFILLED) selon le type

### BORROW_BOX
- Archiviste scanne la boîte → la marque `OUT` → télécharge fantôme PDF+QR → le
  glisse dans la position → remet à l'emprunteur.
- Status demande : `FULFILLED`. Status container : implicite OUT.
- Au retour : archiviste scanne → marque `RETURNED` → status demande :
  `COMPLETED`.

### EXTRACT_PIECES
- Archiviste sort la boîte temporairement, extrait les pièces, remet la boîte à
  sa position, remet les pièces à l'emprunteur dans une pochette avec fantôme
  interne (feuille dans la boîte à la place des pièces).
- Status demande : `FULFILLED`.
- Au retour : les pièces rejoignent la boîte, fantôme interne retiré.
- **Note** : la boîte elle-même n'est jamais en état `OUT` (elle reste rangée).

### DIGITIZE_EXTRACT
- Archiviste sort la boîte temporairement, numérise les pièces demandées
  (scanner dédié), les **upload directement dans Maarch RM** (API Maarch) sous
  la cote enfant du dossier courant.
- Maarch retourne une `resId` → JokkoRM stocke uniquement `maarchResId` +
  `maarchUrl` sur la demande. **Aucun fichier binaire côté JokkoRM.**
- Boîte retourne immédiatement à sa position (jamais marquée `OUT`).
- Status demande : `FULFILLED` avec le lien Maarch envoyé par email au demandeur.
- Status : `COMPLETED` dès que le lien est consulté (ou à la validation manuelle).

**Procédure en vigueur tant que le connecteur d'envoi n'existe pas** (décision du 25 novembre
2026 ; connecteur prévu après le lot 2 de l'étude « du dossier enregistré à la boîte en rayon ») :

1. L'archiviste dépose lui-même le scan dans Maarch RM (sous-archive « Extrait » du dossier, ou
   document ajouté à l'archive), avec les droits et la conservation que Maarch applique.
2. Dans JokkoRM (« À exécuter »), il saisit la **référence** du scan chez Maarch (n° d'archive ou
   cote de l'extrait, obligatoire) et, s'il le souhaite, son **lien** Maarch.
3. JokkoRM enregistre ces deux valeurs (`maarchResId`, `maarchUrl`) et rien d'autre : l'API refuse
   tout fichier (`digitizedPdfBase64` → 400), le lien doit être http(s), sans identifiant ni mot
   de passe, et pointer vers l'adresse publique du maître quand elle est configurée.

Pourquoi pas un dépôt « en attente » dans JokkoRM : une copie gardée hors du SAE, sans date de fin,
échapperait à l'élimination décidée par Maarch, aux droits d'accès de Maarch et à ses sauvegardes.
Le futur connecteur, s'il garde un fichier en transit, devra borner cette copie (empreinte SHA-256,
suppression dès l'accusé de réception de Maarch) — ce sera un amendement de cette fiche, à valider.

## Pattern "une seule archive, deux gardiens" — rappel critique

**JokkoRM ne garde JAMAIS les fichiers numérisés.** Ils vivent côté Maarch RM.

Toutes les règles qui s'appliquent au document (**DUA**, durée de
communicabilité, sort final, tableau de gestion) sont **définies et appliquées
côté Maarch**. JokkoRM ne les duplique pas, ne les override pas.

JokkoRM se contente de :
- Stocker le lien `maarchUrl` sur la demande
- Afficher à l'emprunteur "votre numérisation est disponible → [lien]"
- Tracer la demande et son exécution dans son audit chain propre

Côté Maarch :
- Le document hérite de la DUA du dossier parent
- La communicabilité filtre qui peut accéder au lien
- Le sort final s'applique au terme de la DUA
- Le tableau de gestion reste la source de vérité réglementaire

## Modèle de données proposé

### Table `access_request` (remplace progressivement `physical_loan`)

```sql
CREATE TABLE access_request (
  id                  UUID PRIMARY KEY,
  tenant_id           VARCHAR(64) NOT NULL REFERENCES tenant(id),
  request_type        VARCHAR(32) NOT NULL, -- BORROW_BOX, EXTRACT_PIECES, DIGITIZE_EXTRACT
  container_id        UUID NOT NULL REFERENCES physical_container(id),
  status              VARCHAR(32) NOT NULL, -- DRAFT, SUBMITTED, QUALIFIED, APPROVED, FULFILLED, COMPLETED, REJECTED, CANCELLED

  -- Demandeur
  requester_id        VARCHAR(255) NOT NULL,     -- user id / email
  requester_name      VARCHAR(255) NOT NULL,
  requester_dept      VARCHAR(255),

  -- Contenu de la demande
  purpose             VARCHAR(500) NOT NULL,     -- motif obligatoire
  pieces_description  TEXT,                       -- pour EXTRACT/DIGITIZE : quelles pièces ?
  expected_return_at  TIMESTAMP WITH TIME ZONE,   -- pour BORROW_BOX uniquement

  -- Traitement
  qualified_at        TIMESTAMP WITH TIME ZONE,
  qualified_by        VARCHAR(255),
  qualification_notes TEXT,

  approved_at         TIMESTAMP WITH TIME ZONE,
  approved_by         VARCHAR(255),
  approval_notes      TEXT,

  fulfilled_at        TIMESTAMP WITH TIME ZONE,
  fulfilled_by        VARCHAR(255),
  fulfillment_notes   TEXT,

  completed_at        TIMESTAMP WITH TIME ZONE,

  -- Pour DIGITIZE_EXTRACT uniquement
  maarch_res_id       VARCHAR(128),
  maarch_url          VARCHAR(1024),

  -- Rejets/annulations
  rejected_at         TIMESTAMP WITH TIME ZONE,
  rejected_by         VARCHAR(255),
  reject_reason       VARCHAR(500),
  cancelled_at        TIMESTAMP WITH TIME ZONE,
  cancel_reason       VARCHAR(500),

  -- Lien avec l'ancien modèle de prêt (phase 5E)
  physical_loan_id    UUID REFERENCES physical_loan(id), -- rempli à FULFILLED pour BORROW_BOX

  created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
  updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
  version             BIGINT NOT NULL DEFAULT 0
);
```

Le `physical_loan` existant devient un **artefact interne** créé automatiquement
à `FULFILLED` quand `request_type = BORROW_BOX`. Pas de duplication de données —
les champs communs (containerId, dates) remontent via la FK.

## Garde-fous workflow

- **Pas d'auto-approbation** : `qualified_by` ≠ `approved_by` sauf si le tenant
  n'a qu'un seul utilisateur avec rôle ARCHIVIST+ARCHIVES_HEAD (config
  `jokkorm.loans.allow-self-approval=true`, défaut `false`).
- **Expiration SUBMITTED** : au bout de N jours sans qualification (défaut 7),
  notification au demandeur et au responsable.
- **Expiration APPROVED** : au bout de N jours sans exécution (défaut 3),
  reset vers QUALIFIED avec notification.
- **Idempotence transitions** : chaque transition vérifie l'état courant,
  rejoue sans erreur si déjà fait (mais pas d'audit doublon).

## Découpage en sous-phases

| Phase | Scope | Priorité |
|---|---|---|
| **5P.A** | Fiche pattern 07 + fix bug error toast UI | 🔴 maintenant |
| **5P.B** | Domain `AccessRequest` + Liquibase 0011 + repository + états/transitions | 🔴 |
| **5P.C** | Use cases : createDraft, submit, qualify, reject, approve, cancel | 🔴 |
| **5P.D** | Use case : fulfill (3 variants selon type) + création PhysicalLoan si BORROW_BOX | 🟡 |
| **5P.E** | Qualification checks auto : disponibilité, zone tampon, loan actif | 🔴 |
| **5P.F** | Rôles : REQUESTER / ARCHIVIST / ARCHIVES_HEAD / AUDITOR (pour l'instant via header `X-Role`, OIDC en 4A) | 🟡 |
| **5P.G** | REST + exception handler | 🔴 |
| **5P.H** | Frontend `/demandes` (requester : mes demandes + nouvelle) | 🔴 |
| **5P.I** | Frontend `/demandes/traitement` (archiviste : file à qualifier + exécuter) | 🔴 |
| **5P.J** | Frontend `/demandes/validation` (responsable : file à approuver) | 🔴 |
| **5P.K** | Intégration Maarch RM pour DIGITIZE : upload + récup resId/url | 🟡 (dépend de la disponibilité d'une instance Maarch de test) |
| **5P.L** | Notifications email (mailpit en dev) aux étapes-clés | 🟡 |

## Décisions ouvertes à valider avec l'utilisateur

1. **Nom final de la table/entité** : `access_request` (générique) ou `request`
   tout court ? Je propose `access_request` pour éviter l'ambiguïté avec les
   requêtes HTTP.

2. **DIGITIZE_EXTRACT sans Maarch** : si pas d'instance Maarch disponible en
   dev, est-ce qu'on simule l'upload avec un adapter demo (comme on l'a fait
   pour `SaeMasterPort`) qui retourne un faux `resId` ?

3. **Boîte en zone tampon** : la numérisation d'une boîte en zone tampon
   (jamais rangée) est-elle autorisée ou refusée ? Mon avis : autorisée avec
   warning, parce qu'une boîte peut arriver d'un prestataire externe déjà
   numérisée mais pas encore rangée.

4. **EXTRACT_PIECES** : faut-il un modèle de pièces (liste précise de documents
   attendus) ou juste un champ texte libre `pieces_description` (plus simple,
   l'archiviste décide sur place) ? Je propose texte libre pour commencer.

5. **Classification/habilitation** : on verra ça quand on aura l'OIDC
   (phase 4A) qui apportera les rôles/attributs utilisateurs.
