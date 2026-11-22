package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée une racine SITE ou un enfant d'un emplacement existant. Enforce la cohérence
 * hiérarchique (parentKind correct) et l'unicité du {@code code} parmi les frères.
 */
@Service
public class CreateStorageLocationUseCase {

    private final StorageLocationRepository repository;
    private final Clock clock;

    public CreateStorageLocationUseCase(StorageLocationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Crée une racine SITE. */
    @Transactional
    public StorageLocation createSite(String code, String label) {
        TenantId tenant = TenantContext.current();
        String normalized = code == null ? null : code.toUpperCase(java.util.Locale.ROOT).trim();
        if (normalized != null && repository.existsRootByCode(tenant, normalized)) {
            throw new StorageLocationConflictException(
                "A SITE with code '%s' already exists for tenant %s".formatted(normalized, tenant.value()));
        }
        return repository.save(StorageLocation.createSite(tenant, code, label, clock.instant()));
    }

    /** Crée un enfant (STOREHOUSE sous SITE, SHELF sous STOREHOUSE, etc.). */
    @Transactional
    public StorageLocation createChild(UUID parentId, StorageLocationKind childKind, String code, String label) {
        TenantId tenant = TenantContext.current();
        StorageLocation parent = repository.findById(tenant, parentId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Parent storage location %s not found (tenant=%s)".formatted(parentId, tenant.value())));
        String normalized = code == null ? null : code.toUpperCase(java.util.Locale.ROOT).trim();
        if (normalized != null && repository.existsByParentIdAndCode(tenant, parentId, normalized)) {
            throw new StorageLocationConflictException(
                "A %s with code '%s' already exists under %s"
                    .formatted(childKind, normalized, parent.path()));
        }
        return repository.save(StorageLocation.createChild(parent, childKind, code, label, clock.instant()));
    }
}
