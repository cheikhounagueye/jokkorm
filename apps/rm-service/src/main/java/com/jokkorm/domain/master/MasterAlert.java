package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.UUID;

/**
 * Signalement à l'archiviste, né d'un événement du journal du maître (veilleur).
 *
 * <p>Le texte {@code detail} est rédigé par JokkoRM (jamais recopié du maître). La paire
 * (événement maître, type) est unique : c'est aussi le marqueur « déjà traité ».
 */
public record MasterAlert(UUID id, TenantId tenant, Kind kind, String cote, UUID recordId, String masterEventId,
                          Instant occurredAt, String actor, String detail, Instant createdAt,
                          Instant acknowledgedAt, String acknowledgedBy) {

    public enum Kind {
        /** L'emplacement affiché dans le maître a été modifié ou effacé à la main : JokkoRM l'a rétabli. */
        LOCATION_RESTORED(true),
        /** Après une écriture de JokkoRM, le maître affiche une autre valeur : à vérifier, pas de réécriture. */
        LOCATION_MISMATCH(true),
        /** Gel posé dans le maître alors que la boîte est sortie en prêt : demander son retour. */
        FROZEN_WHILE_OUT(true),
        /** Gel posé dans le maître alors qu'une demande de prêt attend : prévenir le demandeur. */
        FROZEN_WITH_REQUEST(true),
        /** Gel posé dans le maître : toute sortie est bloquée (information). */
        FROZEN(false),
        /** Gel levé dans le maître (information). */
        UNFROZEN(false);

        private final boolean requiresAction;

        Kind(boolean requiresAction) {
            this.requiresAction = requiresAction;
        }

        public boolean requiresAction() {
            return requiresAction;
        }
    }

    public static MasterAlert raise(TenantId tenant, Kind kind, String cote, UUID recordId, MasterEvent event,
                                    String detail, Instant now) {
        return new MasterAlert(UUID.randomUUID(), tenant, kind, cote, recordId, event.id(), event.occurredAt(),
            event.actor(), detail, now, null, null);
    }

    public boolean open() {
        return acknowledgedAt == null;
    }
}
