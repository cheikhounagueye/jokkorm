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

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "sequence_in_batch")
    private Integer sequenceInBatch;

    @Column(name = "external_reference", length = 128)
    private String externalReference;

    @Column(name = "box_format_id")
    private UUID boxFormatId;

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
        e.batchId = d.batchId().orElse(null);
        e.sequenceInBatch = d.sequenceInBatch().orElse(null);
        e.externalReference = d.externalReference().orElse(null);
        e.boxFormatId = d.boxFormatId().orElse(null);
        return e;
    }

    public PhysicalContainer toDomain() {
        return PhysicalContainer.rehydrate(
            id, TenantId.of(tenantId), barcode, label, positionId,
            capacity, state, createdAt, updatedAt, lastMovedAt,
            batchId, sequenceInBatch, externalReference, boxFormatId);
    }

    public void copyMutableStateFrom(PhysicalContainer d) {
        this.label = d.label();
        this.positionId = d.positionId().orElse(null);
        this.capacity = d.capacity().orElse(null);
        this.state = d.state();
        this.updatedAt = d.updatedAt();
        this.lastMovedAt = d.lastMovedAt().orElse(null);
        this.externalReference = d.externalReference().orElse(null);
        this.boxFormatId = d.boxFormatId().orElse(null);
    }

    public String tenantId() { return tenantId; }
}
