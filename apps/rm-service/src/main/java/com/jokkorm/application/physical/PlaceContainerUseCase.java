package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Range (ou déplace) une boîte sur une position. La position cible doit être de kind
 * {@link StorageLocationKind#POSITION} — on ne range pas une boîte à l'étage « magasin ».
 */
@Service
public class PlaceContainerUseCase {

    private final PhysicalContainerRepository containers;
    private final StorageLocationRepository locations;
    private final Clock clock;

    public PlaceContainerUseCase(
        PhysicalContainerRepository containers,
        StorageLocationRepository locations,
        Clock clock) {
        this.containers = containers;
        this.locations = locations;
        this.clock = clock;
    }

    @Transactional
    public PhysicalContainer execute(UUID containerId, UUID positionId) {
        TenantId tenant = TenantContext.current();

        PhysicalContainer container = containers.findById(tenant, containerId)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "Container %s not found (tenant=%s)".formatted(containerId, tenant.value())));

        var position = locations.findById(tenant, positionId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(positionId, tenant.value())));

        if (position.kind() != StorageLocationKind.POSITION) {
            throw new IllegalArgumentException(
                "Can only place a container at a POSITION, got %s (path=%s)"
                    .formatted(position.kind(), position.path()));
        }

        long alreadyThere = containers.findByPosition(tenant, positionId).stream()
            .filter(c -> !c.id().equals(containerId))
            .count();
        if (alreadyThere >= position.effectiveCapacity()) {
            throw new PhysicalContainerConflictException(
                "Position %s pleine : %d/%d boîtes".formatted(
                    position.path(), alreadyThere, position.effectiveCapacity()));
        }

        container.placeAt(positionId, clock.instant());
        return containers.save(container);
    }
}
