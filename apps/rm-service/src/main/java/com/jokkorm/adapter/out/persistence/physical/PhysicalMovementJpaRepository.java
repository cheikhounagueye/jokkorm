package com.jokkorm.adapter.out.persistence.physical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalMovementJpaRepository extends JpaRepository<PhysicalMovementEntity, UUID> {

    List<PhysicalMovementEntity> findByTenantIdAndRecordIdOrderByOccurredAtAsc(
        String tenantId, UUID recordId);
}
