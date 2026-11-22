package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BatchStatus;
import com.jokkorm.domain.physical.ContainerBatch;
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
@Table(name = "container_batch")
public class ContainerBatchEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "reference", length = 255, nullable = false)
    private String reference;

    @Column(name = "size", nullable = false)
    private int size;

    @Column(name = "label_pattern", length = 255)
    private String labelPattern;

    @Column(name = "default_capacity")
    private Integer defaultCapacity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private BatchStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 255, nullable = false)
    private String createdBy;

    @Column(name = "first_printed_at")
    private Instant firstPrintedAt;

    @Column(name = "last_printed_at")
    private Instant lastPrintedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ContainerBatchEntity() { /* JPA */ }

    public static ContainerBatchEntity fromDomain(ContainerBatch b) {
        ContainerBatchEntity e = new ContainerBatchEntity();
        e.id = b.id();
        e.tenantId = b.tenantId().value();
        e.reference = b.reference();
        e.size = b.size();
        e.labelPattern = b.labelPattern().orElse(null);
        e.defaultCapacity = b.defaultCapacity().orElse(null);
        e.status = b.status();
        e.createdAt = b.createdAt();
        e.createdBy = b.createdBy();
        e.firstPrintedAt = b.firstPrintedAt().orElse(null);
        e.lastPrintedAt = b.lastPrintedAt().orElse(null);
        e.updatedAt = b.updatedAt();
        return e;
    }

    public ContainerBatch toDomain() {
        return ContainerBatch.rehydrate(
            id, TenantId.of(tenantId), reference, size,
            labelPattern, defaultCapacity, status,
            createdAt, createdBy, firstPrintedAt, lastPrintedAt, updatedAt);
    }

    public void copyMutableStateFrom(ContainerBatch b) {
        this.status = b.status();
        this.firstPrintedAt = b.firstPrintedAt().orElse(null);
        this.lastPrintedAt = b.lastPrintedAt().orElse(null);
        this.updatedAt = b.updatedAt();
    }

    public String tenantId() { return tenantId; }
}
