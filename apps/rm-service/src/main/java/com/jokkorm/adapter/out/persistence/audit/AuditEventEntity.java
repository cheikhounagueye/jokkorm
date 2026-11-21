package com.jokkorm.adapter.out.persistence.audit;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditEvent;
import com.jokkorm.domain.audit.AuditTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapping JPA de {@link AuditEvent}. La table porte un trigger PG qui refuse
 * DELETE/UPDATE — l'application ne fait que des INSERT. Pas de {@code @Version}
 * (append-only), pas de timestamp JPA auto (on commit l'instant du domaine).
 */
@Entity
@Table(name = "audit_event")
public class AuditEventEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "ts", nullable = false)
    private Instant ts;

    @Column(name = "actor", length = 255, nullable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 64, nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 64, nullable = false)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "payload_hash", nullable = false, columnDefinition = "bytea")
    private byte[] payloadHash;

    @Column(name = "prev_event_hash", columnDefinition = "bytea")
    private byte[] prevEventHash;

    @Column(name = "hmac", nullable = false, columnDefinition = "bytea")
    private byte[] hmac;

    @Column(name = "metadata", columnDefinition = "text")
    private String metadata;

    protected AuditEventEntity() { /* JPA */ }

    public static AuditEventEntity fromDomain(AuditEvent e) {
        AuditEventEntity ent = new AuditEventEntity();
        ent.id = e.id();
        ent.tenantId = e.tenantId().value();
        ent.ts = e.ts();
        ent.actor = e.actor();
        ent.action = e.action();
        ent.targetType = e.targetType();
        ent.targetId = e.targetId();
        ent.payloadHash = e.payloadHash();
        ent.prevEventHash = e.prevEventHash();
        ent.hmac = e.hmac();
        ent.metadata = e.metadata();
        return ent;
    }

    public AuditEvent toDomain() {
        return new AuditEvent(
            id, TenantId.of(tenantId), ts, actor, action, targetType, targetId,
            payloadHash, prevEventHash, hmac, metadata);
    }

    public Instant ts() { return ts; }
}
