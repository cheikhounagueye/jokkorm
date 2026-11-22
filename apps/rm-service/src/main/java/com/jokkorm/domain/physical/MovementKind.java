package com.jokkorm.domain.physical;

/**
 * Les 6 verbes du journal des mouvements physiques (docs/pattern/05-cycle-vie-papier.md).
 *
 * <ul>
 *   <li>{@link #INGRESS} — première mise en magasin.</li>
 *   <li>{@link #RELOCATE} — changement d'emplacement interne.</li>
 *   <li>{@link #LOAN_OUT} — sortie pour prêt (consultation).</li>
 *   <li>{@link #LOAN_IN} — retour d'un prêt.</li>
 *   <li>{@link #DIGITIZE_REQUEST} — numérisation à la demande, l'original reste en magasin.</li>
 *   <li>{@link #EGRESS} — sortie définitive (destruction ou versement AN).</li>
 * </ul>
 */
public enum MovementKind {
    INGRESS,
    RELOCATE,
    LOAN_OUT,
    LOAN_IN,
    DIGITIZE_REQUEST,
    EGRESS
}
