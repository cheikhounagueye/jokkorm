package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.EquipmentKind;
import com.jokkorm.domain.physical.RoomEquipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "room_equipment")
public class RoomEquipmentEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", length = 64, nullable = false) private String tenantId;
    @Column(name = "storehouse_id", nullable = false) private UUID storehouseId;
    @Enumerated(EnumType.STRING) @Column(name = "kind", length = 32, nullable = false) private EquipmentKind kind;
    @Column(name = "label", length = 255) private String label;
    @Column(name = "pos_x", nullable = false) private double posX;
    @Column(name = "pos_z", nullable = false) private double posZ;
    @Column(name = "last_inspection_on") private LocalDate lastInspectionOn;
    @Column(name = "next_inspection_on") private LocalDate nextInspectionOn;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private long version;

    protected RoomEquipmentEntity() { /* JPA */ }

    public static RoomEquipmentEntity fromDomain(RoomEquipment d) {
        RoomEquipmentEntity e = new RoomEquipmentEntity();
        e.id = d.id();
        e.tenantId = d.tenantId().value();
        e.storehouseId = d.storehouseId();
        e.kind = d.kind();
        e.label = d.label().orElse(null);
        e.posX = d.x();
        e.posZ = d.z();
        e.lastInspectionOn = d.lastInspectionOn().orElse(null);
        e.nextInspectionOn = d.nextInspectionOn().orElse(null);
        e.createdAt = d.createdAt();
        e.updatedAt = d.updatedAt();
        return e;
    }

    public RoomEquipment toDomain() {
        return RoomEquipment.rehydrate(id, TenantId.of(tenantId), storehouseId, kind, label,
            posX, posZ, lastInspectionOn, nextInspectionOn, createdAt, updatedAt);
    }
}
