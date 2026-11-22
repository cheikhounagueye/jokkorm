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
        e.createdAt = d.createdAt();
        e.updatedAt = d.updatedAt();
        return e;
    }

    public StorageLocation toDomain() {
        return StorageLocation.rehydrate(
            id, TenantId.of(tenantId), kind, code, label, parentId, path, createdAt, updatedAt);
    }

    public void copyMutableStateFrom(StorageLocation d) {
        this.label = d.label();
        this.path = d.path();
        this.updatedAt = d.updatedAt();
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
}
