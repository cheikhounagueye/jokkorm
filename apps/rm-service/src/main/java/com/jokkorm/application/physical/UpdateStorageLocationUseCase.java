package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renomme un nœud — seul le {@code label} est modifiable. Le {@code code} fait partie
 * du {@code path} dénormalisé et est donc immuable (sinon tous les descendants et toutes
 * les références externes — boîtes, records, notifications maître — casseraient).
 *
 * <p>Pour changer un code, la procédure propre est : créer un nouveau nœud frère, migrer
 * les enfants, puis supprimer l'ancien. Non implémentée en 5J.
 *
 * <p>TODO phase 4A : exiger {@code AccessGuard.requireRmFunction(user.functions, MANAGE)}
 * quand l'OIDC frontend sera câblé. En mode dev-open on fait confiance.
 */
@Service
public class UpdateStorageLocationUseCase {

    private final StorageLocationRepository repository;
    private final AuditChainService audit;
    private final Clock clock;

    public UpdateStorageLocationUseCase(
        StorageLocationRepository repository,
        AuditChainService audit,
        Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public StorageLocation execute(UUID id, String newLabel, String actor) {
        TenantId tenant = TenantContext.current();
        StorageLocation location = repository.findById(tenant, id)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(id, tenant.value())));

        String previousLabel = location.label();
        location.rename(newLabel, clock.instant());
        StorageLocation saved = repository.save(location);

        audit.record(
            AuditAction.STORAGE_LOCATION_RENAMED,
            AuditTargetType.STORAGE_LOCATION,
            location.id(),
            actor,
            "path=%s previousLabel=%s newLabel=%s"
                .formatted(location.path(),
                    previousLabel == null ? "" : previousLabel,
                    newLabel == null ? "" : newLabel));
        return saved;
    }
}
