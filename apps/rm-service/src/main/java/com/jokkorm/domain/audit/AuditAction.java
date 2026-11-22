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
    RECORD_STORED
}
