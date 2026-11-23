package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;

/**
 * État du veilleur pour un tenant.
 *
 * @param cursorAt      horodatage (donné par le MAÎTRE) du dernier événement traité ; la lecture
 *                      suivante repart un peu avant, les doublons sont écartés
 * @param lastRunAt     dernier passage, réussi ou non
 * @param lastSuccessAt dernier passage réussi
 * @param lastError     raison du dernier échec, {@code null} si le dernier passage a réussi
 * @param lastEvents    nombre d'événements lus au dernier passage réussi
 */
public record MasterWatchState(TenantId tenant, Instant cursorAt, Instant lastRunAt, Instant lastSuccessAt,
                               String lastError, int lastEvents) {

    public static MasterWatchState start(TenantId tenant, Instant cursorAt) {
        return new MasterWatchState(tenant, cursorAt, null, null, null, 0);
    }

    public MasterWatchState succeeded(Instant cursor, Instant at, int events) {
        return new MasterWatchState(tenant, cursor, at, at, null, events);
    }

    public MasterWatchState failed(Instant cursor, Instant at, String error) {
        return new MasterWatchState(tenant, cursor, at, lastSuccessAt, error, lastEvents);
    }
}
