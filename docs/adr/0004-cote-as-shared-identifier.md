# ADR 0004 — La cote : identifiant partagé maître ↔ JokkoRM

- **Statut** : Accepté (2026-11-21)
- **Auteur** : Cheikh A. Bamba + Claude (Opus 4.7)
- **Décideurs** : Cheikh A. Bamba
- **Références** : ADR 0003 (SaeMasterPort), `docs/pattern/03-qui-detient-quoi.md`, `docs/pattern/04-dossier-hybride.md`

## Contexte

Les deux systèmes (maître + JokkoRM) parlent de la même archive. Il leur faut une clé commune, stable, que les deux reconnaissent. Sans elle, impossible de rapprocher le contrat papier dans la boîte DMG-2024-112 du PV d'ouverture des plis dans Maarch.

Trois candidats possibles :

1. **UUID interne JokkoRM** : généré ici, exporté au maître. Rejeté : la cote préexiste au versement physique, elle vient du maître par nature.
2. **Chemin plan-de-classement** (`/finances/marchés/2024/047`) : lisible mais pas stable (si le plan bouge, la clé bouge). Rejeté.
3. **Cote émise par le maître** (`DMG/MP/2024/047`) : fixée à la clôture du dossier, stable, humaine, portée par le champ Maarch `originatorArchiveId`. Retenu.

## Décision

### 1. La cote est émise par le maître, reprise telle quelle par JokkoRM

- Type valeur : `com.jokkorm.domain.record.Cote` — record Java, validation stricte (longueur max, charset `[A-Z0-9/_-]`, pas d'espace).
- Immuable : une fois émise par le maître, jamais modifiée côté JokkoRM.
- Clé naturelle de l'archive physique : `UNIQUE(tenant_id, cote)` sur la table `physical_archive_record`.

### 2. Le maître reste autoritaire sur son format

- JokkoRM n'impose **aucune règle de nommage**. Un maître Maarch utilise `DMG/MP/2024/047`, un autre `2024-FIN-00042`, un troisième `urn:isbn:978...`. Tant que ça matche le regex `[A-Z0-9/_\-]{1,64}` (insensible à la casse), JokkoRM accepte.
- Validation côté JokkoRM : seulement la longueur et le charset, pas la sémantique.

### 3. Dérivations internes

- Code-barres physique = `cote` encodé Code128 (ou QR si > 20 chars).
- URL canonique archive = `GET /api/v1/records/{cote}`. Le `{cote}` est URL-encodé parce que la barre oblique est autorisée.
- Export CSV, SEDA, journaux : la cote sert de clé primaire présentée.

### 4. Conflit cote inconnue

Si JokkoRM reçoit un événement `cote-assigned` pour une cote déjà présente, c'est une erreur de synchronisation. Trois cas :

- **Idempotence vraie** (même maître, même payload sémantique) → 200 No-op.
- **Collision de valeurs** (attributs différents) → 409 Conflict + alerte audit `COTE_CONFLICT`, l'opérateur arbitre.
- **Cote reçue hors tenant** (mauvaise clé API) → 403.

## Conséquences

### Positives
- **Zéro ambiguïté** : la slide « qui détient quoi » devient littérale — Maarch RM colonne 2 (maître), JokkoRM colonne 2 (`originatorArchiveId` repris tel quel).
- **Interopérabilité maître libre** : pas de format imposé, JokkoRM est agnostique.
- **Humainement lisible** : les étiquettes papier affichent la cote, pas un UUID.

### Négatives
- **Charset limité** : pas d'unicode dans la cote. Acceptable (les cotes archivistiques sont ASCII par tradition).
- **Dépendance forte au maître pour l'émission** : si le maître ne génère pas de cote au bon moment, JokkoRM ne peut pas provisionner l'enveloppe physique. Mitigation : `NoopAdapter` + endpoint admin pour créer une cote « orpheline » destinée à être adoptée par le maître plus tard.

## Notes

Compatible avec SEDA (balise `<ArchivalAgencyArchiveIdentifier>`) et avec MoReq 2010 (champ `id`). Pas de surprise à l'export réglementaire.
