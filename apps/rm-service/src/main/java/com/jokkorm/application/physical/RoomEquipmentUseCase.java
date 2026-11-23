package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.EquipmentKind;
import com.jokkorm.domain.physical.RoomEquipment;
import com.jokkorm.domain.physical.RoomEquipmentRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ajout et retrait des équipements de sécurité d'un magasin. */
@Service
public class RoomEquipmentUseCase {

    private final RoomEquipmentRepository equipment;
    private final StorageLocationRepository locations;
    private final AuditChainService audit;
    private final Clock clock;

    public RoomEquipmentUseCase(
        RoomEquipmentRepository equipment,
        StorageLocationRepository locations,
        AuditChainService audit,
        Clock clock) {
        this.equipment = equipment;
        this.locations = locations;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public RoomEquipment add(UUID storehouseId, EquipmentKind kind, String label, double x, double z,
                             LocalDate lastInspectionOn, LocalDate nextInspectionOn, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        StorageLocation room = requireStorehouse(tenant, storehouseId);
        RoomEquipment saved = equipment.save(RoomEquipment.place(
            tenant, room.id(), kind, label, x, z, lastInspectionOn, nextInspectionOn, clock.instant()));
        audit.record(AuditAction.ROOM_EQUIPMENT_ADDED, AuditTargetType.STORAGE_LOCATION, room.id(), actor,
            "equipment=%s kind=%s label=%s".formatted(saved.id(), kind, saved.label().orElse("")));
        return saved;
    }

    @Transactional
    public void remove(UUID storehouseId, UUID equipmentId, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        RoomEquipment item = equipment.findById(tenant, equipmentId)
            .filter(e -> e.storehouseId().equals(storehouseId))
            .orElseThrow(() -> new RoomEquipmentNotFoundException(
                "Equipment %s not found in storehouse %s".formatted(equipmentId, storehouseId)));
        audit.record(AuditAction.ROOM_EQUIPMENT_REMOVED, AuditTargetType.STORAGE_LOCATION, storehouseId, actor,
            "equipment=%s kind=%s label=%s".formatted(item.id(), item.kind(), item.label().orElse("")));
        equipment.delete(tenant, equipmentId);
    }

    private StorageLocation requireStorehouse(TenantId tenant, UUID storehouseId) {
        StorageLocation room = locations.findById(tenant, storehouseId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(storehouseId, tenant.value())));
        if (room.kind() != StorageLocationKind.STOREHOUSE) {
            throw new IllegalArgumentException(
                "Equipment belongs to a STOREHOUSE, got %s (path=%s)".formatted(room.kind(), room.path()));
        }
        return room;
    }
}
