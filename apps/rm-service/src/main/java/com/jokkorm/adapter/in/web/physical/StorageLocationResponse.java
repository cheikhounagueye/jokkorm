package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import java.time.Instant;
import java.util.UUID;

public record StorageLocationResponse(
    UUID id,
    StorageLocationKind kind,
    String code,
    String label,
    UUID parentId,
    String path,
    /** Boîtes acceptées (POSITION uniquement, valeur par défaut incluse), null sinon. */
    Integer capacity,
    Instant createdAt,
    Instant updatedAt) {

    public static StorageLocationResponse from(StorageLocation d) {
        return new StorageLocationResponse(
            d.id(), d.kind(), d.code(), d.label(),
            d.parentId().orElse(null), d.path(),
            d.kind() == StorageLocationKind.POSITION ? Integer.valueOf(d.effectiveCapacity()) : null,
            d.createdAt(), d.updatedAt());
    }
}
