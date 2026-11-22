package com.jokkorm.adapter.out.persistence.physical;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalContainerJpaRepository extends JpaRepository<PhysicalContainerEntity, UUID> {

    Optional<PhysicalContainerEntity> findByIdAndTenantId(UUID id, String tenantId);

    Optional<PhysicalContainerEntity> findByTenantIdAndBarcode(String tenantId, String barcode);

    boolean existsByTenantIdAndBarcode(String tenantId, String barcode);

    List<PhysicalContainerEntity> findByTenantIdAndPositionIdOrderByBarcodeAsc(
        String tenantId, UUID positionId);

    Page<PhysicalContainerEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
}
