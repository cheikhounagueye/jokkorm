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
    /** Longueur utile saisie en mm (travée, tablette, position). Null = déduite (pattern 10). */
    Integer lengthMm,
    Instant createdAt,
    Instant updatedAt) {

    public static StorageLocationResponse from(StorageLocation d) {
        return new StorageLocationResponse(
            d.id(), d.kind(), d.code(), d.label(),
            d.parentId().orElse(null), d.path(),
            d.lengthMm().orElse(null),
            d.createdAt(), d.updatedAt());
    }
}
