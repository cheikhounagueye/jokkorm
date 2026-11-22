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
    Instant createdAt,
    Instant updatedAt) {

    public static StorageLocationResponse from(StorageLocation d) {
        return new StorageLocationResponse(
            d.id(), d.kind(), d.code(), d.label(),
            d.parentId().orElse(null), d.path(),
            d.createdAt(), d.updatedAt());
    }
}
