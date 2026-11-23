package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomEquipmentRepository {
    RoomEquipment save(RoomEquipment equipment);
    Optional<RoomEquipment> findById(TenantId tenant, UUID id);
    List<RoomEquipment> findByStorehouse(TenantId tenant, UUID storehouseId);
    void delete(TenantId tenant, UUID id);
}
