package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhysicalContainerRepository {

    PhysicalContainer save(PhysicalContainer container);

    Optional<PhysicalContainer> findById(TenantId tenant, UUID id);

    Optional<PhysicalContainer> findByBarcode(TenantId tenant, String barcode);

    boolean existsByBarcode(TenantId tenant, String barcode);

    /** Boîtes rangées à une position donnée. */
    List<PhysicalContainer> findByPosition(TenantId tenant, UUID positionId);

    Page<PhysicalContainer> findAll(TenantId tenant, PageRequest pageRequest);
}
