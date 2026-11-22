package com.jokkorm.domain.loan;

/**
 * Cycle de vie d'un prêt de boîte (phase 5E). B2B2C : un demandeur interne
 * (agent RH, juriste…) emprunte une boîte pour consultation hors magasin.
 *
 * <ul>
 *   <li>{@link #REQUESTED} — demande saisie, en attente de préparation par l'archiviste</li>
 *   <li>{@link #OUT} — boîte sortie du magasin, fantôme PDF+QR laissé à sa place</li>
 *   <li>{@link #OVERDUE} — expectedReturnAt dépassé sans retour (calculé par le use case)</li>
 *   <li>{@link #RETURNED} — boîte rentrée, fantôme retiré, prêt clôturé (terminal)</li>
 *   <li>{@link #CANCELLED} — demande annulée avant sortie (terminal)</li>
 * </ul>
 */
public enum LoanStatus {
    REQUESTED,
    OUT,
    OVERDUE,
    RETURNED,
    CANCELLED;

    public boolean isTerminal() {
        return this == RETURNED || this == CANCELLED;
    }

    public boolean isActive() {
        return this == OUT || this == OVERDUE;
    }
}
