package com.jokkorm.domain.master;

import java.time.Instant;
import java.util.Objects;

/**
 * Mouvement physique à notifier au maître. 6 verbes (cf. JokkoFile {@code sae-p1-5-movements}).
 */
public record MovementEvent(Kind kind, String agentSubject, Instant occurredAt, String motive) {

    public MovementEvent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(agentSubject, "agentSubject");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public enum Kind {
        /** Première mise en magasin. */
        INGRESS,
        /** Changement d'emplacement interne. */
        RELOCATE,
        /** Sortie pour prêt (consultation). */
        LOAN_OUT,
        /** Retour d'un prêt. */
        LOAN_IN,
        /** Numérisation à la demande, l'original reste en magasin. */
        DIGITIZE_REQUEST,
        /** Sortie définitive (destruction ou versement AN). */
        EGRESS
    }
}
