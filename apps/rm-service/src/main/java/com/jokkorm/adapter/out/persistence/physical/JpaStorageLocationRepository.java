package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaStorageLocationRepository implements StorageLocationRepository {

    private final StorageLocationJpaRepository jpa;

    public JpaStorageLocationRepository(StorageLocationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public StorageLocation save(StorageLocation location) {
        StorageLocationEntity existing = jpa.findById(location.id()).orElse(null);
        StorageLocationEntity saved;
        if (existing == null) {
            saved = jpa.save(StorageLocationEntity.fromDomain(location));
        } else {
            if (!existing.tenantId().equals(location.tenantId().value())) {
                throw new IllegalStateException(
                    "Tenant mismatch on save: entity=%s domain=%s"
                        .formatted(existing.tenantId(), location.tenantId().value()));
            }
            existing.copyMutableStateFrom(location);
            saved = jpa.save(existing);
        }
        return saved.toDomain();
    }

    @Override
    public Optional<StorageLocation> findById(TenantId tenant, UUID id) {
        return jpa.findByIdAndTenantId(id, tenant.value()).map(StorageLocationEntity::toDomain);
    }

    @Override
    public Optional<StorageLocation> findByPath(TenantId tenant, String path) {
        return jpa.findByTenantIdAndPath(tenant.value(), path).map(StorageLocationEntity::toDomain);
    }

    @Override
    public List<StorageLocation> findChildren(TenantId tenant, UUID parentId) {
        return jpa.findByTenantIdAndParentIdOrderByCodeAsc(tenant.value(), parentId)
            .stream().map(StorageLocationEntity::toDomain).toList();
    }

    @Override
    public List<StorageLocation> findRoots(TenantId tenant) {
        return jpa.findByTenantIdAndKindAndParentIdIsNullOrderByCodeAsc(
                tenant.value(), StorageLocationKind.SITE)
            .stream().map(StorageLocationEntity::toDomain).toList();
    }

    @Override
    public boolean existsByParentIdAndCode(TenantId tenant, UUID parentId, String code) {
        return jpa.existsByTenantIdAndParentIdAndCode(tenant.value(), parentId, code);
    }

    @Override
    public boolean existsRootByCode(TenantId tenant, String code) {
        return jpa.existsByTenantIdAndKindAndParentIdIsNullAndCode(
            tenant.value(), StorageLocationKind.SITE, code);
    }

    @Override
    public void delete(TenantId tenant, UUID id) {
        jpa.findByIdAndTenantId(id, tenant.value())
            .ifPresent(e -> jpa.delete(e));
    }
}
