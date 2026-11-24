package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPhysicalContainerRepository implements PhysicalContainerRepository {

    private final PhysicalContainerJpaRepository jpa;

    public JpaPhysicalContainerRepository(PhysicalContainerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PhysicalContainer save(PhysicalContainer container) {
        PhysicalContainerEntity existing = jpa.findById(container.id()).orElse(null);
        PhysicalContainerEntity saved;
        if (existing == null) {
            saved = jpa.save(PhysicalContainerEntity.fromDomain(container));
        } else {
            if (!existing.tenantId().equals(container.tenantId().value())) {
                throw new IllegalStateException(
                    "Tenant mismatch on save: entity=%s domain=%s"
                        .formatted(existing.tenantId(), container.tenantId().value()));
            }
            existing.copyMutableStateFrom(container);
            saved = jpa.save(existing);
        }
        return saved.toDomain();
    }

    @Override
    public Optional<PhysicalContainer> findById(TenantId tenant, UUID id) {
        return jpa.findByIdAndTenantId(id, tenant.value()).map(PhysicalContainerEntity::toDomain);
    }

    @Override
    public Optional<PhysicalContainer> findByBarcode(TenantId tenant, String barcode) {
        return jpa.findByTenantIdAndBarcode(tenant.value(), barcode)
            .map(PhysicalContainerEntity::toDomain);
    }

    @Override
    public boolean existsByBarcode(TenantId tenant, String barcode) {
        return jpa.existsByTenantIdAndBarcode(tenant.value(), barcode);
    }

    @Override
    public void lockLabels(TenantId tenant) {
        jpa.advisoryXactLock("container-label:" + tenant.value());
    }

    @Override
    public List<PhysicalContainer> findActiveByLabel(TenantId tenant, String label, int limit) {
        return jpa.findActiveByNormalizedLabel(tenant.value(), label, limit)
            .stream().map(PhysicalContainerEntity::toDomain).toList();
    }

    @Override
    public List<PhysicalContainer> findActiveByLabelPrefix(TenantId tenant, String prefix, int limit) {
        return jpa.findActiveByNormalizedLabelPrefix(tenant.value(), prefix, limit)
            .stream().map(PhysicalContainerEntity::toDomain).toList();
    }

    @Override
    public long countActiveByLabelPrefix(TenantId tenant, String prefix) {
        return jpa.countActiveByNormalizedLabelPrefix(tenant.value(), prefix);
    }

    @Override
    public List<PhysicalContainer> findByPositions(TenantId tenant, java.util.Collection<UUID> positionIds) {
        if (positionIds.isEmpty()) return List.of();
        return jpa.findByTenantIdAndPositionIdInOrderByBarcodeAsc(tenant.value(), positionIds)
            .stream().map(PhysicalContainerEntity::toDomain).toList();
    }

    @Override
    public List<PhysicalContainer> findByPosition(TenantId tenant, UUID positionId) {
        return jpa.findByTenantIdAndPositionIdOrderByBarcodeAsc(tenant.value(), positionId)
            .stream().map(PhysicalContainerEntity::toDomain).toList();
    }

    @Override
    public Page<PhysicalContainer> findAll(TenantId tenant, PageRequest pageRequest) {
        var page = jpa.findByTenantIdOrderByCreatedAtDesc(
            tenant.value(), toPageable(pageRequest));
        List<PhysicalContainer> content = page.getContent().stream()
            .map(PhysicalContainerEntity::toDomain).toList();
        return new Page<>(content, pageRequest.page(), pageRequest.size(), page.getTotalElements());
    }

    private static Pageable toPageable(PageRequest r) {
        return org.springframework.data.domain.PageRequest.of(r.page(), r.size());
    }

    @Override
    public List<StaleBufferEntry> findStaleBufferByBatch(TenantId tenant, Instant threshold) {
        return jpa.findStaleBufferGroupedByBatch(tenant.value(), threshold).stream()
            .map(row -> {
                UUID batchId = (UUID) row[0];
                String reference = (String) row[1];
                long count = ((Number) row[2]).longValue();
                Object oldestRaw = row[3];
                Instant oldest = oldestRaw instanceof Timestamp ts
                    ? ts.toInstant()
                    : oldestRaw instanceof Instant i
                        ? i
                        : Instant.parse(String.valueOf(oldestRaw));
                return new StaleBufferEntry(batchId, reference, count, oldest);
            })
            .toList();
    }
}
