package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;

/**
 * Envoi de l'état physique d'une cote vers le maître (file, une ligne par cote).
 *
 * @param version incrémentée à chaque nouvelle demande : un envoi réussi ne marque « envoyé »
 *                que si aucune demande n'est arrivée entre-temps
 */
public record MasterSync(TenantId tenant, String cote, Status status, int attempts, Instant nextAttemptAt,
                         Instant lastAttemptAt, Instant lastSentAt, String lastError, String lastPayload,
                         long version) {

    public enum Status {
        /** À envoyer (ou à réessayer après une panne du maître). */
        PENDING,
        /** Le maître affiche l'état le plus récent. */
        SENT,
        /** Le maître refuse durablement : voir {@code lastError}. */
        REJECTED
    }
}
