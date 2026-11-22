package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.FindStorageLocationTreeUseCase;
import java.util.List;

/** Vue arborescente — un nœud contient ses enfants récursivement. */
public record StorageLocationTreeResponse(StorageLocationResponse node, List<StorageLocationTreeResponse> children) {

    public static StorageLocationTreeResponse from(FindStorageLocationTreeUseCase.Node n) {
        return new StorageLocationTreeResponse(
            StorageLocationResponse.from(n.location()),
            n.children().stream().map(StorageLocationTreeResponse::from).toList());
    }
}
