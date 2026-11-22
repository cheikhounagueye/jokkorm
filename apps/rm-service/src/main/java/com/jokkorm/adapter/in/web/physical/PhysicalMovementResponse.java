package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.domain.physical.MovementKind;
import com.jokkorm.domain.physical.PhysicalMovement;
import java.time.Instant;
import java.util.UUID;

public record PhysicalMovementResponse(
    UUID id,
    UUID recordId,
    UUID containerId,
    MovementKind kind,
    String actor,
    Instant occurredAt,
    String motive) {

    public static PhysicalMovementResponse from(PhysicalMovement m) {
        return new PhysicalMovementResponse(
            m.id(), m.recordId(), m.containerId(), m.kind(), m.actor(),
            m.occurredAt(), m.motive());
    }
}
