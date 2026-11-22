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

    /** Un dossier a été rangé dans une boîte à une position physique — phase 5C. */
    RECORD_STORED,

    /** Un mouvement physique a été enregistré (6 verbes, phase 5D). */
    MOVEMENT_RECORDED,

    /** Admin a renommé un nœud de la topographie — phase 5J. */
    STORAGE_LOCATION_RENAMED,

    /** Admin a supprimé un nœud de la topographie — phase 5J. */
    STORAGE_LOCATION_DELETED,

    /** Lot de boîtes créé en masse — phase 5M. */
    CONTAINER_BATCH_CREATED,

    /** Lot imprimé (PDF étiquettes téléchargé) — phase 5M.2. */
    CONTAINER_BATCH_PRINTED,

    /** Référence externe prestataire mise à jour sur une boîte — phase 5N. */
    CONTAINER_EXTERNAL_REFERENCE_SET,

    /** Import Excel topographie exécuté — phase 5I. */
    STORAGE_LOCATIONS_IMPORTED,

    /** Prêt de boîte demandé (phase 5E). */
    LOAN_REQUESTED,
    /** Prêt marqué sorti (boîte remise à l'emprunteur, fantôme imprimé) — phase 5E. */
    LOAN_OUT,
    /** Prêt retourné (boîte rangée, fantôme retiré) — phase 5E. */
    LOAN_RETURNED,
    /** Prêt annulé avant sortie — phase 5E. */
    LOAN_CANCELLED
}
