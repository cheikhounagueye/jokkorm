package com.jokkorm.adapter.out.persistence.physical;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomSpecJpaRepository extends JpaRepository<RoomSpecEntity, UUID> {

    Optional<RoomSpecEntity> findByStorehouseIdAndTenantId(UUID storehouseId, String tenantId);
}
