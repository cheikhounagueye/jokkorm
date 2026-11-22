package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supprime un nœud — mais seulement s'il est <b>strictement vide</b> :
 * <ul>
 *   <li>pas d'enfants (STORAGE_LOCATION dont parent = this)</li>
 *   <li>pas de boîtes rangées dessus (POSITION-level uniquement, mais on vérifie partout
 *       par sécurité)</li>
 * </ul>
 *
 * <p>En cas d'échec de ces préconditions → {@link StorageLocationInUseException} → 409.
 * L'utilisateur doit d'abord vider manuellement le nœud (déplacer boîtes, supprimer enfants
 * un par un depuis les feuilles) avant de pouvoir supprimer la branche.
 *
 * <p>C'est volontairement conservateur : une suppression récursive silencieuse qui
 * détruirait 500 positions d'un seul coup serait trop dangereuse pour un admin.
 */
@Service
public class DeleteStorageLocationUseCase {

    private final StorageLocationRepository repository;
    private final PhysicalContainerRepository containers;
    private final AuditChainService audit;

    public DeleteStorageLocationUseCase(
        StorageLocationRepository repository,
        PhysicalContainerRepository containers,
        AuditChainService audit) {
        this.repository = repository;
        this.containers = containers;
        this.audit = audit;
    }

    @Transactional
    public void execute(UUID id, String actor) {
        TenantId tenant = TenantContext.current();
        StorageLocation location = repository.findById(tenant, id)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(id, tenant.value())));

        if (!repository.findChildren(tenant, id).isEmpty()) {
            throw new StorageLocationInUseException(
                "Cannot delete %s (%s) — it has children. Delete leaves first."
                    .formatted(location.path(), location.kind()));
        }

        if (!containers.findByPosition(tenant, id).isEmpty()) {
            throw new StorageLocationInUseException(
                "Cannot delete %s (%s) — containers are stored here. Move them first."
                    .formatted(location.path(), location.kind()));
        }

        // Audit AVANT delete pour que la chaîne porte l'empreinte du nœud.
        audit.record(
            AuditAction.STORAGE_LOCATION_DELETED,
            AuditTargetType.STORAGE_LOCATION,
            location.id(),
            actor,
            "path=%s kind=%s".formatted(location.path(), location.kind()));

        repository.delete(tenant, id);
    }
}
