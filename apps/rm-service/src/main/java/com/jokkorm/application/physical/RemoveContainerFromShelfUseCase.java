package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.master.MasterSyncService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mode magasin, geste « retirer » (3 scans : la boîte, son emplacement, la boîte encore).
 * La boîte quitte sa tablette, repasse en zone tampon et libère sa place.
 *
 * <ul>
 *   <li>l'emplacement scanné doit être celui où la boîte est enregistrée, sinon refus avec
 *       l'adresse enregistrée : si elle était bien là, on corrige avec « Placer » ;</li>
 *   <li>une boîte en prêt n'est pas sur sa tablette (sa fiche fantôme garde la place) ;</li>
 *   <li>les archives de la boîte changent d'adresse : la GED maître est prévenue.</li>
 * </ul>
 */
@Service
public class RemoveContainerFromShelfUseCase {

    private final PhysicalContainerRepository containers;
    private final StorageLocationRepository locations;
    private final PhysicalLoanRepository loans;
    private final MasterSyncService masterSync;
    private final AuditChainService audit;
    private final Clock clock;

    public RemoveContainerFromShelfUseCase(
        PhysicalContainerRepository containers,
        StorageLocationRepository locations,
        PhysicalLoanRepository loans,
        MasterSyncService masterSync,
        AuditChainService audit,
        Clock clock) {
        this.containers = containers;
        this.locations = locations;
        this.loans = loans;
        this.masterSync = masterSync;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalContainer execute(UUID containerId, UUID scannedPositionId, String actor) {
        TenantId tenant = TenantContext.current();
        PhysicalContainer box = containers.findById(tenant, containerId)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "Container %s not found (tenant=%s)".formatted(containerId, tenant.value())));

        if (box.state() == ContainerState.DESTROYED) {
            throw new PhysicalContainerConflictException(
                "La boîte %s est détruite : elle n'est plus sur une tablette".formatted(box.barcode()));
        }
        if (loans.findActiveByContainer(tenant, box.id()).filter(l -> l.status().isActive()).isPresent()) {
            throw new PhysicalContainerConflictException(
                "La boîte %s est en prêt : sa fiche fantôme lui garde sa place. Enregistre d'abord son retour"
                    .formatted(box.barcode()));
        }
        if (box.positionId().isEmpty()) {
            throw new PhysicalContainerConflictException(
                "La boîte %s n'est sur aucune tablette : elle est déjà en zone tampon".formatted(box.barcode()));
        }
        StorageLocation from = locations.findById(tenant, box.positionId().get())
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(box.positionId().get(), tenant.value())));
        if (!from.id().equals(scannedPositionId)) {
            String address = masterSync.address(tenant, from);
            throw new ContainerNotAtPositionException(
                "La boîte %s n'est pas enregistrée sur cet emplacement mais sur : %s. Si elle était bien ici, range-la avec « Placer » pour corriger"
                    .formatted(box.barcode(), address),
                address);
        }

        box.removeFromShelf(clock.instant());
        PhysicalContainer saved = containers.save(box);
        masterSync.touchContainer(tenant, saved.id()); // ses archives n'ont plus d'adresse en rayon
        audit.record(AuditAction.CONTAINER_REMOVED, AuditTargetType.CONTAINER, saved.id(), actor,
            "barcode=%s from=%s".formatted(saved.barcode(), from.path()));
        return saved;
    }
}
