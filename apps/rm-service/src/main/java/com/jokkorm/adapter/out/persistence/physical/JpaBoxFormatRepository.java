package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.BoxFormatRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaBoxFormatRepository implements BoxFormatRepository {

    private final BoxFormatJpaRepository jpa;

    public JpaBoxFormatRepository(BoxFormatJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public BoxFormat save(BoxFormat format) {
        BoxFormatEntity existing = jpa.findById(format.id()).orElse(null);
        if (existing == null) return jpa.saveAndFlush(BoxFormatEntity.fromDomain(format)).toDomain();
        if (!existing.tenantId().equals(format.tenantId().value())) {
            throw new IllegalStateException("Tenant mismatch on box format save");
        }
        existing.copyMutableStateFrom(format);
        return jpa.saveAndFlush(existing).toDomain();
    }

    @Override
    public Optional<BoxFormat> findById(TenantId tenant, UUID id) {
        return jpa.findByIdAndTenantId(id, tenant.value()).map(BoxFormatEntity::toDomain);
    }

    @Override
    public Optional<BoxFormat> findByCode(TenantId tenant, String code) {
        return jpa.findByTenantIdAndCode(tenant.value(), code).map(BoxFormatEntity::toDomain);
    }

    @Override
    public List<BoxFormat> findAll(TenantId tenant) {
        return jpa.findByTenantIdOrderBySpineMmAscCodeAsc(tenant.value()).stream()
            .map(BoxFormatEntity::toDomain).toList();
    }
}
