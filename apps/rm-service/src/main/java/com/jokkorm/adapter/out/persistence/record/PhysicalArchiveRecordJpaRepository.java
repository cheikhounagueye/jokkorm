package com.jokkorm.adapter.out.persistence.record;

import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalArchiveRecordJpaRepository
    extends JpaRepository<PhysicalArchiveRecordEntity, UUID> {

    Optional<PhysicalArchiveRecordEntity> findByIdAndTenantId(UUID id, String tenantId);

    Optional<PhysicalArchiveRecordEntity> findByTenantIdAndCote(String tenantId, String cote);

    boolean existsByTenantIdAndCote(String tenantId, String cote);

    Page<PhysicalArchiveRecordEntity> findByTenantIdOrderByRegisteredAtDesc(
        String tenantId, Pageable pageable);

    Page<PhysicalArchiveRecordEntity> findByTenantIdAndStatusOrderByRegisteredAtDesc(
        String tenantId, PhysicalArchiveStatus status, Pageable pageable);
}
