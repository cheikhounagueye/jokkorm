package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
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
}
