package com.jokkorm.domain.access;

/**
 * États du workflow 4-eyes des demandes d'accès (pattern 07).
 *
 * <pre>
 *   DRAFT → SUBMITTED → QUALIFIED → APPROVED → FULFILLED → COMPLETED
 *                  └─ REJECTED (par archiviste ou responsable)
 *      └─ CANCELLED (par demandeur)
 * </pre>
 */
public enum AccessRequestStatus {
    DRAFT,
    SUBMITTED,
    QUALIFIED,
    APPROVED,
    FULFILLED,
    COMPLETED,
    REJECTED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }
}
