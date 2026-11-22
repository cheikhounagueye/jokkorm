package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.domain.physical.BatchStatus;
import com.jokkorm.domain.physical.ContainerBatch;
import java.time.Instant;
import java.util.UUID;

public record ContainerBatchResponse(
    UUID id,
    String reference,
    int size,
    String labelPattern,
    Integer defaultCapacity,
    BatchStatus status,
    Instant createdAt,
    String createdBy,
    Instant firstPrintedAt,
    Instant lastPrintedAt,
    Instant updatedAt) {

    public static ContainerBatchResponse from(ContainerBatch b) {
        return new ContainerBatchResponse(
            b.id(), b.reference(), b.size(),
            b.labelPattern().orElse(null),
            b.defaultCapacity().orElse(null),
            b.status(),
            b.createdAt(), b.createdBy(),
            b.firstPrintedAt().orElse(null),
            b.lastPrintedAt().orElse(null),
            b.updatedAt());
    }
}
