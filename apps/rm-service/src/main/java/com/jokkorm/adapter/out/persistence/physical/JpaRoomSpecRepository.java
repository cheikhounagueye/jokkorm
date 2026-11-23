package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.RoomSpec;
import com.jokkorm.domain.physical.RoomSpecRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRoomSpecRepository implements RoomSpecRepository {

    private final RoomSpecJpaRepository jpa;

    public JpaRoomSpecRepository(RoomSpecJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<RoomSpec> findByStorehouse(TenantId tenant, UUID storehouseId) {
        return jpa.findByStorehouseIdAndTenantId(storehouseId, tenant.value()).map(RoomSpecEntity::toDomain);
    }

    @Override
    public RoomSpec save(RoomSpec spec, Instant at) {
        RoomSpecEntity entity = jpa.findByStorehouseIdAndTenantId(spec.storehouseId(), spec.tenant().value())
            .map(e -> { e.copy(spec, at); return e; })
            .orElseGet(() -> RoomSpecEntity.create(spec, at));
        return jpa.save(entity).toDomain();
    }
}
