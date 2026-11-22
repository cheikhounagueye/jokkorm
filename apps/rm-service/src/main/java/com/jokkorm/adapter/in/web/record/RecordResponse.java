package com.jokkorm.adapter.in.web.record;

import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Représentation REST d'un {@link PhysicalArchiveRecord}. Le mapping domain → REST
 * vit ici à la frontière web pour ne pas polluer le domaine.
 *
 * <p>{@code containerId} est null tant que le dossier n'a pas été rangé dans une boîte
 * (phase 5C). Set dès qu'un {@code /records/store} est appliqué.
 */
public record RecordResponse(
    UUID id, String cote, PhysicalArchiveStatus status,
    Instant registeredAt, Instant lastTransitionAt,
    UUID containerId) {

    public static RecordResponse from(PhysicalArchiveRecord r) {
        return new RecordResponse(
            r.id(), r.cote().value(), r.status(), r.registeredAt(), r.lastTransitionAt(),
            r.containerId().orElse(null));
    }
}
