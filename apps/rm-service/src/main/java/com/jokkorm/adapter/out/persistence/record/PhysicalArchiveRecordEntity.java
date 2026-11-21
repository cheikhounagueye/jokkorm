package com.jokkorm.adapter.out.persistence.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Entité JPA miroir de {@link PhysicalArchiveRecord}. Pure cartographie — pas de
 * logique métier ici. {@code fromDomain} / {@code toDomain} à chaque frontière.
 *
 * <p>Note Hibernate 6 : on stocke {@code tenantId} en {@link String} brut plutôt qu'en
 * {@link TenantId} + {@code @Convert}, pour éviter le heurt schema-validation BYTEA
 * documenté dans la mémoire eversuite-modern (feedback-security-standards).
 */
@Entity
@Table(name = "physical_archive_record")
public class PhysicalArchiveRecordEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "cote", length = 64, nullable = false)
    private String cote;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private PhysicalArchiveStatus status;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    @Column(name = "last_transition_at", nullable = false)
    private Instant lastTransitionAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PhysicalArchiveRecordEntity() { /* JPA */ }

    public static PhysicalArchiveRecordEntity fromDomain(PhysicalArchiveRecord r) {
        PhysicalArchiveRecordEntity e = new PhysicalArchiveRecordEntity();
        e.id = r.id();
        e.tenantId = r.tenantId().value();
        e.cote = r.cote().value();
        e.status = r.status();
        e.registeredAt = r.registeredAt();
        e.lastTransitionAt = r.lastTransitionAt();
        return e;
    }

    public PhysicalArchiveRecord toDomain() {
        return PhysicalArchiveRecord.rehydrate(
            id, TenantId.of(tenantId), Cote.of(cote), status, registeredAt, lastTransitionAt);
    }

    /** Pour une mise à jour sans perdre le compteur @Version. */
    public void copyMutableStateFrom(PhysicalArchiveRecord r) {
        this.status = r.status();
        this.lastTransitionAt = r.lastTransitionAt();
    }

    public UUID id() { return id; }
    public String tenantId() { return tenantId; }
    public String cote() { return cote; }
}
