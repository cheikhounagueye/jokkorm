package com.jokkorm.domain.audit;

/**
 * Type d'objet audité — sert à chaîner les évents par cible et à filtrer le journal.
 *
 * <p>Phase 3A : un seul type ({@link #PHYSICAL_ARCHIVE_RECORD}). Les autres contextes
 * (loan, recolement, disposal) viendront enrichir cette enum au fil des phases.
 */
public enum AuditTargetType {
    /** Enveloppe physique d'archive — l'agrégat central du module physique. */
    PHYSICAL_ARCHIVE_RECORD,

    /** Nœud de la topographie (SITE / MAGASIN / TRAVÉE / TABLETTE / POSITION) — phase 5J. */
    STORAGE_LOCATION,

    /** Lot de provisionnement de boîtes (phase 5M). */
    CONTAINER_BATCH,

    /** Conteneur physique individuel (boîte, carton) — phase 5N. */
    CONTAINER
}
