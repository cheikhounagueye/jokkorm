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
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mode magasin, geste « arrivée » (1 scan) : la boîte est reçue dans la zone tampon.
 * On garde qui l'a reçue et quand ; la zone tampon reste l'absence de position (pattern 06).
 *
 * <ul>
 *   <li>une boîte rangée n'« arrive » pas : on la sort d'abord avec « Retirer » ;</li>
 *   <li>une boîte en prêt revient par l'enregistrement du retour, pas par une arrivée ;</li>
 *   <li>pointer deux fois de suite ne change rien et le dit.</li>
 * </ul>
 */
@Service
public class ReceiveContainerUseCase {

    public record Result(PhysicalContainer container, boolean alreadyReceived) {}

    private final PhysicalContainerRepository containers;
    private final StorageLocationRepository locations;
    private final PhysicalLoanRepository loans;
    private final MasterSyncService masterSync;
    private final AuditChainService audit;
    private final Clock clock;

    public ReceiveContainerUseCase(
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
    public Result execute(String barcode, String actor) {
        TenantId tenant = TenantContext.current();
        String code = MagasinScanService.normalize(barcode);
        PhysicalContainer box = containers.findByBarcode(tenant, code)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "Aucune boîte ne porte le code %s dans cet espace".formatted(code)));

        if (box.state() == ContainerState.DESTROYED) {
            throw new PhysicalContainerConflictException(
                "La boîte %s est détruite : elle ne peut pas arriver en magasin".formatted(code));
        }
        if (loans.findActiveByContainer(tenant, box.id()).filter(l -> l.status().isActive()).isPresent()) {
            throw new PhysicalContainerConflictException(
                "La boîte %s est en prêt : enregistre son retour (menu Prêts) au lieu de pointer une arrivée"
                    .formatted(code));
        }
        if (box.positionId().isPresent()) {
            String address = box.positionId().flatMap(id -> locations.findById(tenant, id))
                .map(p -> masterSync.address(tenant, p)).orElse("une tablette");
            throw new PhysicalContainerConflictException(
                "La boîte %s est déjà rangée (%s). Pour la sortir de sa tablette, utilise « Retirer »"
                    .formatted(code, address));
        }

        if (!box.receive(actor, clock.instant())) {
            return new Result(box, true);
        }
        PhysicalContainer saved = containers.save(box);
        audit.record(AuditAction.CONTAINER_RECEIVED, AuditTargetType.CONTAINER, saved.id(), actor,
            "barcode=%s".formatted(saved.barcode()));
        return new Result(saved, false);
    }
}
