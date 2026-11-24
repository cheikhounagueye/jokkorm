package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_location")
public class StorageLocationEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 16, nullable = false)
    private StorageLocationKind kind;

    @Column(name = "code", length = 32, nullable = false)
    private String code;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "path", length = 512, nullable = false)
    private String path;

    @Column(name = "length_mm")
    private Integer lengthMm;

    // ─── Mobilier et dimensions (pattern 10, 5T.B) ───
    @Enumerated(EnumType.STRING)
    @Column(name = "furniture_type", length = 32)
    private com.jokkorm.domain.physical.FurnitureType furnitureType;

    @Column(name = "depth_mm")
    private Integer depthMm;

    @Column(name = "clearance_mm")
    private Integer clearanceMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @Column(name = "faces")
    private Integer faces;

    @Column(name = "aisle_code", length = 16)
    private String aisleCode;

    @Column(name = "aisle_order")
    private Integer aisleOrder;

    @Column(name = "aisle_side", length = 1)
    private String aisleSide;

    /** Code de l'étiquette d'emplacement (POSITION), jamais modifié après création. */
    @Column(name = "scan_code", length = 16, updatable = false)
    private String scanCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected StorageLocationEntity() { /* JPA */ }

    public static StorageLocationEntity fromDomain(StorageLocation d) {
        StorageLocationEntity e = new StorageLocationEntity();
        e.id = d.id();
        e.tenantId = d.tenantId().value();
        e.kind = d.kind();
        e.code = d.code();
        e.label = d.label();
        e.parentId = d.parentId().orElse(null);
        e.path = d.path();
        e.lengthMm = d.lengthMm().orElse(null);
        e.copySpec(d.spec());
        e.scanCode = d.scanCode().orElse(null);
        e.createdAt = d.createdAt();
        e.updatedAt = d.updatedAt();
        return e;
    }

    public StorageLocation toDomain() {
        return StorageLocation.rehydrate(
            id, TenantId.of(tenantId), kind, code, label, parentId, path, lengthMm,
            new com.jokkorm.domain.physical.PhysicalSpec(furnitureType, depthMm, clearanceMm, heightMm,
                faces, aisleCode, aisleOrder, aisleSide),
            scanCode, createdAt, updatedAt);
    }

    private void copySpec(com.jokkorm.domain.physical.PhysicalSpec s) {
        this.furnitureType = s.furnitureType();
        this.depthMm = s.depthMm();
        this.clearanceMm = s.clearanceMm();
        this.heightMm = s.heightMm();
        this.faces = s.faces();
        this.aisleCode = s.aisleCode();
        this.aisleOrder = s.aisleOrder();
        this.aisleSide = s.aisleSide();
    }

    public void copyMutableStateFrom(StorageLocation d) {
        this.label = d.label();
        this.path = d.path();
        this.lengthMm = d.lengthMm().orElse(null);
        copySpec(d.spec());
        this.updatedAt = d.updatedAt();
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
}
