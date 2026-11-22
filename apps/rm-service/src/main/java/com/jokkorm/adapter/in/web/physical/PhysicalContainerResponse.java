package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.PhysicalContainer;
import java.time.Instant;
import java.util.UUID;

public record PhysicalContainerResponse(
    UUID id,
    String barcode,
    String label,
    UUID positionId,
    Integer capacity,
    ContainerState state,
    Instant createdAt,
    Instant updatedAt,
    Instant lastMovedAt,
    UUID batchId,
    Integer sequenceInBatch,
    String externalReference) {

    public static PhysicalContainerResponse from(PhysicalContainer d) {
        return new PhysicalContainerResponse(
            d.id(), d.barcode(), d.label(),
            d.positionId().orElse(null),
            d.capacity().orElse(null),
            d.state(),
            d.createdAt(), d.updatedAt(),
            d.lastMovedAt().orElse(null),
            d.batchId().orElse(null),
            d.sequenceInBatch().orElse(null),
            d.externalReference().orElse(null));
    }
}
