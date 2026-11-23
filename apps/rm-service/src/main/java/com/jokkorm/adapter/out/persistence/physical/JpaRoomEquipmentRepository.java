package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.RoomEquipment;
import com.jokkorm.domain.physical.RoomEquipmentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaRoomEquipmentRepository implements RoomEquipmentRepository {

    private final RoomEquipmentJpaRepository jpa;

    public JpaRoomEquipmentRepository(RoomEquipmentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RoomEquipment save(RoomEquipment equipment) {
        return jpa.save(RoomEquipmentEntity.fromDomain(equipment)).toDomain();
    }

    @Override
    public Optional<RoomEquipment> findById(TenantId tenant, UUID id) {
        return jpa.findByIdAndTenantId(id, tenant.value()).map(RoomEquipmentEntity::toDomain);
    }

    @Override
    public List<RoomEquipment> findByStorehouse(TenantId tenant, UUID storehouseId) {
        return jpa.findByTenantIdAndStorehouseIdOrderByKindAscLabelAsc(tenant.value(), storehouseId)
            .stream().map(RoomEquipmentEntity::toDomain).toList();
    }

    @Override
    public void delete(TenantId tenant, UUID id) {
        jpa.findByIdAndTenantId(id, tenant.value()).ifPresent(jpa::delete);
    }
}
