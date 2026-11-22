package com.jokkorm.domain.access;

/**
 * Nature de la demande utilisateur (pattern 07).
 */
public enum RequestType {
    /** L'emprunteur veut la boîte entière pour consultation étendue. */
    BORROW_BOX,
    /** L'emprunteur veut 2-3 documents précis (la boîte reste rangée). */
    EXTRACT_PIECES,
    /** L'emprunteur veut consulter à distance : on numérise + upload Maarch, lien envoyé. */
    DIGITIZE_EXTRACT
}
