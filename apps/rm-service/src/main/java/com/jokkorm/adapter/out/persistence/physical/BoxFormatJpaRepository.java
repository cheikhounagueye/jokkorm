package com.jokkorm.adapter.out.persistence.physical;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoxFormatJpaRepository extends JpaRepository<BoxFormatEntity, UUID> {
    Optional<BoxFormatEntity> findByIdAndTenantId(UUID id, String tenantId);
    Optional<BoxFormatEntity> findByTenantIdAndCode(String tenantId, String code);
    List<BoxFormatEntity> findByTenantIdOrderBySpineMmAscCodeAsc(String tenantId);
}
