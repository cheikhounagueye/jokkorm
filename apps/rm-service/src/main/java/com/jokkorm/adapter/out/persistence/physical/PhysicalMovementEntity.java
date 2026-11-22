package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.MovementKind;
import com.jokkorm.domain.physical.PhysicalMovement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Mapping JPA — append-only (trigger PG refuse UPDATE/DELETE). Pas de @Version. */
@Entity
@Table(name = "physical_movement")
public class PhysicalMovementEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "container_id")
    private UUID containerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 32, nullable = false)
    private MovementKind kind;

    @Column(name = "actor", length = 255, nullable = false)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "motive", length = 512)
    private String motive;

    protected PhysicalMovementEntity() { /* JPA */ }

    public static PhysicalMovementEntity fromDomain(PhysicalMovement m) {
        PhysicalMovementEntity e = new PhysicalMovementEntity();
        e.id = m.id();
        e.tenantId = m.tenantId().value();
        e.recordId = m.recordId();
        e.containerId = m.containerId();
        e.kind = m.kind();
        e.actor = m.actor();
        e.occurredAt = m.occurredAt();
        e.motive = m.motive();
        return e;
    }

    public PhysicalMovement toDomain() {
        return new PhysicalMovement(
            id, TenantId.of(tenantId), recordId, containerId, kind, actor, occurredAt, motive);
    }
}
