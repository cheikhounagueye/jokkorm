package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.PhysicalContainer;
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
@Table(name = "physical_container")
public class PhysicalContainerEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "barcode", length = 64, nullable = false)
    private String barcode;

    @Column(name = "label", length = 255)
    private String label;

    @Column(name = "position_id")
    private UUID positionId;

    @Column(name = "capacity")
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", length = 16, nullable = false)
    private ContainerState state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_moved_at")
    private Instant lastMovedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PhysicalContainerEntity() { /* JPA */ }

    public static PhysicalContainerEntity fromDomain(PhysicalContainer d) {
        PhysicalContainerEntity e = new PhysicalContainerEntity();
        e.id = d.id();
        e.tenantId = d.tenantId().value();
        e.barcode = d.barcode();
        e.label = d.label();
        e.positionId = d.positionId().orElse(null);
        e.capacity = d.capacity().orElse(null);
        e.state = d.state();
        e.createdAt = d.createdAt();
        e.updatedAt = d.updatedAt();
        e.lastMovedAt = d.lastMovedAt().orElse(null);
        return e;
    }

    public PhysicalContainer toDomain() {
        return PhysicalContainer.rehydrate(
            id, TenantId.of(tenantId), barcode, label, positionId,
            capacity, state, createdAt, updatedAt, lastMovedAt);
    }

    public void copyMutableStateFrom(PhysicalContainer d) {
        this.label = d.label();
        this.positionId = d.positionId().orElse(null);
        this.capacity = d.capacity().orElse(null);
        this.state = d.state();
        this.updatedAt = d.updatedAt();
        this.lastMovedAt = d.lastMovedAt().orElse(null);
    }

    public String tenantId() { return tenantId; }
}
