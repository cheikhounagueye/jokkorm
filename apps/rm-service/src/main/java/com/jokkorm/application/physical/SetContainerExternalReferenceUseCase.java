package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 5N — fixe (ou efface) la référence externe prestataire d'une boîte,
 * ex. numéro PRO ARCHIVES SYSTEMES "19944" ou code interne client.
 *
 * <p>Idempotent : ré-appel avec la même valeur ne fait rien. Audit uniquement
 * sur changement effectif.
 */
@Service
public class SetContainerExternalReferenceUseCase {

    private final PhysicalContainerRepository containers;
    private final AuditChainService audit;
    private final Clock clock;

    public SetContainerExternalReferenceUseCase(
        PhysicalContainerRepository containers,
        AuditChainService audit,
        Clock clock) {
        this.containers = containers;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalContainer execute(UUID containerId, String newRef, String actor) {
        TenantId tenant = TenantContext.current();
        PhysicalContainer c = containers.findById(tenant, containerId)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "Container %s not found (tenant=%s)".formatted(containerId, tenant.value())));

        String oldRef = c.externalReference().orElse(null);
        String normalized = (newRef == null || newRef.isBlank()) ? null : newRef.trim();
        if (java.util.Objects.equals(oldRef, normalized)) {
            return c; // idempotent, pas d'audit
        }

        c.setExternalReference(normalized, clock.instant());
        PhysicalContainer saved = containers.save(c);

        audit.record(
            AuditAction.CONTAINER_EXTERNAL_REFERENCE_SET,
            AuditTargetType.CONTAINER,
            saved.id(),
            actor,
            "old=%s new=%s".formatted(oldRef == null ? "" : oldRef, normalized == null ? "" : normalized));

        return saved;
    }
}
