package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.GetBatchHealthUseCase;

public record BatchHealthResponse(
    ContainerBatchResponse batch,
    long total,
    long inTransit,
    long deployed,
    int deploymentPercent,
    long storedEmpty,
    long open,
    long sealed,
    long destroyed) {

    public static BatchHealthResponse from(GetBatchHealthUseCase.Health h) {
        return new BatchHealthResponse(
            ContainerBatchResponse.from(h.batch()),
            h.total(), h.inTransit(), h.deployed(), h.deploymentPercent(),
            h.storedEmpty(), h.open(), h.sealed(), h.destroyed());
    }
}
