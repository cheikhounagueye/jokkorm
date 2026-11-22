package com.jokkorm.commons.role;

/**
 * Rôles applicatifs JokkoRM (phase 5P). En attendant OIDC (phase 4A), le rôle
 * est passé via header HTTP {@code X-Role}. Un utilisateur peut cumuler
 * plusieurs rôles (ex. ARCHIVIST + ARCHIVES_HEAD dans un petit tenant).
 */
public enum UserRole {
    /** Tout utilisateur — peut créer/suivre ses propres demandes. */
    REQUESTER,
    /** Archiviste — qualifie les demandes, exécute les approuvées. */
    ARCHIVIST,
    /** Responsable des archives — approuve ou rejette les demandes qualifiées. */
    ARCHIVES_HEAD,
    /** Lecture seule — audit, consultation historique. */
    AUDITOR
}
