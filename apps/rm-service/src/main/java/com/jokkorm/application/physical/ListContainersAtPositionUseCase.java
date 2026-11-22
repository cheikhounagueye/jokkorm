package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Liste des boîtes rangées à une position donnée (phase 5B + 5H UI magasin). */
@Service
public class ListContainersAtPositionUseCase {

    private final PhysicalContainerRepository containers;

    public ListContainersAtPositionUseCase(PhysicalContainerRepository containers) {
        this.containers = containers;
    }

    @Transactional(readOnly = true)
    public List<PhysicalContainer> execute(UUID positionId) {
        TenantId tenant = TenantContext.current();
        return containers.findByPosition(tenant, positionId);
    }
}
