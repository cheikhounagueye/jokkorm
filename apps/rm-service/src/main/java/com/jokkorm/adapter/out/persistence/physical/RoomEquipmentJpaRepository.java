package com.jokkorm.adapter.out.persistence.physical;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomEquipmentJpaRepository extends JpaRepository<RoomEquipmentEntity, UUID> {
    Optional<RoomEquipmentEntity> findByIdAndTenantId(UUID id, String tenantId);
    List<RoomEquipmentEntity> findByTenantIdAndStorehouseIdOrderByKindAscLabelAsc(String tenantId, UUID storehouseId);
}
