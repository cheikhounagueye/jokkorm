package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContainerBatchRepository {

    ContainerBatch save(ContainerBatch batch);

    Optional<ContainerBatch> findById(TenantId tenant, UUID id);

    Page<ContainerBatch> findAll(TenantId tenant, PageRequest pageRequest);

    /** Containers d'un lot, triés par sequence_in_batch ascendant. */
    List<PhysicalContainer> listContainers(TenantId tenant, UUID batchId);

    /** Stats pour {@link com.jokkorm.application.physical.GetBatchHealthUseCase}. */
    BatchCounts countContainersByState(TenantId tenant, UUID batchId);

    record BatchCounts(
        long total,
        long inTransit,
        long storedEmpty,
        long open,
        long sealed,
        long destroyed) {

        public long deployed() { return total - inTransit; }
    }
}
