package com.jokkorm.application.record;

import java.util.List;

/**
 * Mise en boîte groupée refusée (HTTP 409) : rien n'a été écrit. Chaque dossier refusé est listé
 * avec sa raison ; un refus sans cote concerne la boîte elle-même (fermée, en prêt, trop pleine).
 */
public class ConditioningRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** {@code cote} null = la boîte entière. {@code reason} = slug stable pour l'écran. */
    public record Refusal(String cote, String reason, String message) implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
    }

    private final transient List<Refusal> refusals;

    public ConditioningRefusedException(String message, List<Refusal> refusals) {
        super(message);
        this.refusals = List.copyOf(refusals);
    }

    public List<Refusal> refusals() {
        return refusals;
    }
}
