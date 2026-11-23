package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Range (ou déplace) une boîte sur une position, comme sur le terrain (pattern 10) :
 * <ul>
 *   <li>la boîte doit tenir en longueur sur l'emplacement, et la tablette garde sa marge ;</li>
 *   <li>une boîte en prêt est chez l'emprunteur : on ne la déplace pas, sa fiche fantôme
 *       lui garde sa place ;</li>
 *   <li>une boîte détruite ne se range plus ;</li>
 *   <li>re-scanner une boîte sur sa propre position ne change rien ;</li>
 *   <li>chaque rangement et chaque déplacement est tracé dans la chaîne d'audit.</li>
 * </ul>
 * L'ancienne position est libérée dans la même transaction : l'occupation se recalcule
 * des deux côtés.
 */
@Service
public class PlaceContainerUseCase {

    private final PhysicalContainerRepository containers;
    private final StorageLocationRepository locations;
    private final PhysicalLoanRepository loans;
    private final ShelfSpaceService space;
    private final AuditChainService audit;
    private final Clock clock;

    public PlaceContainerUseCase(
        PhysicalContainerRepository containers,
        StorageLocationRepository locations,
        PhysicalLoanRepository loans,
        ShelfSpaceService space,
        AuditChainService audit,
        Clock clock) {
        this.containers = containers;
        this.locations = locations;
        this.loans = loans;
        this.space = space;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalContainer execute(UUID containerId, UUID positionId, String actor) {
        TenantId tenant = TenantContext.current();

        PhysicalContainer container = containers.findById(tenant, containerId)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "Container %s not found (tenant=%s)".formatted(containerId, tenant.value())));

        StorageLocation position = locations.findById(tenant, positionId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(positionId, tenant.value())));

        if (position.kind() != StorageLocationKind.POSITION) {
            throw new IllegalArgumentException(
                "Can only place a container at a POSITION, got %s (path=%s)"
                    .formatted(position.kind(), position.path()));
        }
        if (container.state() == ContainerState.DESTROYED) {
            throw new PhysicalContainerConflictException(
                "La boîte %s est détruite : elle ne peut plus être rangée".formatted(container.barcode()));
        }
        if (container.positionId().filter(positionId::equals).isPresent()) {
            return container;
        }
        if (loans.findActiveByContainer(tenant, containerId).isPresent()) {
            throw new PhysicalContainerConflictException(
                "La boîte %s est en prêt : enregistre son retour avant de la ranger ailleurs"
                    .formatted(container.barcode()));
        }

        space.requireRoomFor(tenant, position, container);

        String fromPath = container.positionId()
            .flatMap(id -> locations.findById(tenant, id))
            .map(StorageLocation::path)
            .orElse(null);

        container.placeAt(positionId, clock.instant());
        PhysicalContainer saved = containers.save(container);

        if (fromPath == null) {
            audit.record(AuditAction.CONTAINER_PLACED, AuditTargetType.CONTAINER, saved.id(), actor,
                "barcode=%s to=%s".formatted(saved.barcode(), position.path()));
        } else {
            audit.record(AuditAction.CONTAINER_MOVED, AuditTargetType.CONTAINER, saved.id(), actor,
                "barcode=%s from=%s to=%s".formatted(saved.barcode(), fromPath, position.path()));
        }
        return saved;
    }
}
