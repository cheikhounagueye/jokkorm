package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalMovement;
import com.jokkorm.domain.physical.PhysicalMovementRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPhysicalMovementRepository implements PhysicalMovementRepository {

    private final PhysicalMovementJpaRepository jpa;

    public JpaPhysicalMovementRepository(PhysicalMovementJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PhysicalMovement save(PhysicalMovement movement) {
        return jpa.save(PhysicalMovementEntity.fromDomain(movement)).toDomain();
    }

    @Override
    public List<PhysicalMovement> findByRecord(TenantId tenant, UUID recordId) {
        return jpa.findByTenantIdAndRecordIdOrderByOccurredAtAsc(tenant.value(), recordId)
            .stream().map(PhysicalMovementEntity::toDomain).toList();
    }
}
