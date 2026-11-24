package com.jokkorm.domain.audit;

/**
 * Catalogue des actions auditées. Enum explicite plutôt que string libre pour permettre
 * un traçage statique et éviter les divergences d'orthographe entre sites d'émission.
 */
public enum AuditAction {

    /** Cote reçue du maître, enveloppe physique provisionnée. */
    COTE_ASSIGNED,

    /** Gel contentieux prononcé par le maître. */
    LEGAL_HOLD_APPLIED,

    /** Gel contentieux levé par le maître. */
    LEGAL_HOLD_RELEASED,

    /** Emplacement modifié à la main dans le maître, rétabli par le veilleur (étape 1). */
    MASTER_LOCATION_RESTORED,

    /** Signalement du veilleur marqué traité par un archiviste. */
    MASTER_ALERT_ACKNOWLEDGED,

    /** Archive déposée chez le maître, importée seule par une règle de profil (étape 2). */
    MASTER_DEPOSIT_AUTO_IMPORTED,

    /** Archive de la liste « à importer » importée par un archiviste (étape 2). */
    MASTER_CANDIDATE_IMPORTED,

    /** Archive de la liste « à importer » écartée : pas de papier (étape 2). */
    MASTER_CANDIDATE_IGNORED,

    /** Archive écartée remise dans la liste « à importer » (étape 2). */
    MASTER_CANDIDATE_RESTORED,

    /** Règle d'import automatique d'un profil activée ou retirée (étape 2). */
    MASTER_AUTO_IMPORT_CHANGED,

    /** Un dossier a été rangé dans une boîte à une position physique — phase 5C. */
    RECORD_STORED,

    /** Un mouvement physique a été enregistré (6 verbes, phase 5D). */
    MOVEMENT_RECORDED,

    /** Admin a renommé un nœud de la topographie — phase 5J. */
    STORAGE_LOCATION_RENAMED,

    /** Admin a supprimé un nœud de la topographie — phase 5J. */
    STORAGE_LOCATION_DELETED,

    /** Capacité en boîtes d'une position modifiée — phase 5S (remplacée par la longueur, 5T). */
    STORAGE_LOCATION_CAPACITY_SET,
    /** Longueur utile d'une travée, tablette ou position modifiée — phase 5T. */
    STORAGE_LOCATION_LENGTH_SET,
    /** Mobilier et dimensions d'une travée ou d'une tablette (pattern 10, 5T.B). */
    STORAGE_LOCATION_SPEC_SET,
    /** Dimensions, limites et consignes climatiques d'une salle (pattern 10, 5T.B). */
    ROOM_SPEC_SET,
    /** Boîte rangée pour la première fois sur une position — phase 5T. */
    CONTAINER_PLACED,
    /** Boîte déplacée d'une position à une autre — phase 5T. */
    CONTAINER_MOVED,
    /** Boîte pointée à son arrivée en zone tampon (mode magasin, pattern 12). */
    CONTAINER_RECEIVED,
    /** Boîte retirée de sa tablette, repassée en zone tampon (mode magasin, 3 scans). */
    CONTAINER_REMOVED,
    /** Planche d'étiquettes d'emplacement imprimée (mode magasin). */
    POSITION_LABELS_PRINTED,
    /** Format de boîte corrigé — phase 5T. */
    CONTAINER_FORMAT_CHANGED,
    /** Format ajouté ou modifié dans le catalogue du tenant — phase 5T. */
    BOX_FORMAT_CREATED,
    BOX_FORMAT_UPDATED,
    /** Équipement (extincteur, issue…) ajouté ou retiré d'un magasin — phase 5S. */
    ROOM_EQUIPMENT_ADDED,
    ROOM_EQUIPMENT_REMOVED,

    /** Lot de boîtes créé en masse — phase 5M. */
    CONTAINER_BATCH_CREATED,

    /** Lot imprimé (PDF étiquettes téléchargé) — phase 5M.2. */
    CONTAINER_BATCH_PRINTED,

    /** Référence externe prestataire mise à jour sur une boîte — phase 5N. */
    CONTAINER_EXTERNAL_REFERENCE_SET,

    /** Import Excel topographie exécuté — phase 5I. */
    STORAGE_LOCATIONS_IMPORTED,
    /** Travées, tablettes et emplacements créés par le générateur à gabarit (pattern 10, 5T.C). */
    STORAGE_LOCATIONS_GENERATED,

    /** Prêt de boîte demandé (phase 5E). */
    LOAN_REQUESTED,
    /** Prêt marqué sorti (boîte remise à l'emprunteur, fantôme imprimé) — phase 5E. */
    LOAN_OUT,
    /** Prêt retourné (boîte rangée, fantôme retiré) — phase 5E. */
    LOAN_RETURNED,
    /** Prêt annulé avant sortie — phase 5E. */
    LOAN_CANCELLED,

    /** Demande d'accès cycle de vie (phase 5P). */
    ACCESS_REQUEST_CREATED,
    ACCESS_REQUEST_SUBMITTED,
    ACCESS_REQUEST_QUALIFIED,
    ACCESS_REQUEST_APPROVED,
    ACCESS_REQUEST_REJECTED,
    ACCESS_REQUEST_CANCELLED,
    ACCESS_REQUEST_FULFILLED,
    ACCESS_REQUEST_COMPLETED
}
