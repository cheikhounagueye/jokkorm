package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.physical.ContainerBatch;
import com.jokkorm.domain.physical.ContainerBatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListContainerBatchesUseCase {

    private final ContainerBatchRepository batches;

    public ListContainerBatchesUseCase(ContainerBatchRepository batches) {
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public Page<ContainerBatch> execute(PageRequest request) {
        TenantId tenant = TenantContext.current();
        return batches.findAll(tenant, request);
    }
}
