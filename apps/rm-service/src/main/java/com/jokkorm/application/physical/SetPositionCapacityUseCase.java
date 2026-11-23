package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fixe la capacité en boîtes d'une POSITION. Refuse de descendre sous le nombre de boîtes déjà rangées. */
@Service
public class SetPositionCapacityUseCase {

    private final StorageLocationRepository locations;
    private final PhysicalContainerRepository containers;
    private final AuditChainService audit;
    private final Clock clock;

    public SetPositionCapacityUseCase(
        StorageLocationRepository locations,
        PhysicalContainerRepository containers,
        AuditChainService audit,
        Clock clock) {
        this.locations = locations;
        this.containers = containers;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public StorageLocation execute(UUID positionId, Integer capacity, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        StorageLocation position = locations.findById(tenant, positionId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(positionId, tenant.value())));

        int stored = containers.findByPosition(tenant, positionId).size();
        if (capacity != null && capacity < stored) {
            throw new StorageLocationConflictException(
                "Position %s contient déjà %d boîtes : capacité %d impossible"
                    .formatted(position.path(), stored, capacity));
        }

        int previous = position.kind() == com.jokkorm.domain.physical.StorageLocationKind.POSITION
            ? position.effectiveCapacity() : 0;
        position.changeCapacity(capacity, clock.instant());
        StorageLocation saved = locations.save(position);

        audit.record(AuditAction.STORAGE_LOCATION_CAPACITY_SET, AuditTargetType.STORAGE_LOCATION,
            saved.id(), actor,
            "path=%s previous=%d new=%d".formatted(saved.path(), previous, saved.effectiveCapacity()));
        return saved;
    }
}
