package com.jokkorm.adapter.in.web.record;

import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Représentation REST d'un {@link PhysicalArchiveRecord}. Le mapping domain → REST
 * vit ici à la frontière web pour ne pas polluer le domaine.
 */
public record RecordResponse(
    UUID id, String cote, PhysicalArchiveStatus status,
    Instant registeredAt, Instant lastTransitionAt) {

    public static RecordResponse from(PhysicalArchiveRecord r) {
        return new RecordResponse(
            r.id(), r.cote().value(), r.status(), r.registeredAt(), r.lastTransitionAt());
    }
}
