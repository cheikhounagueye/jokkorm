package com.jokkorm.application.record;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.physical.PhysicalContainerNotFoundException;
import com.jokkorm.application.record.RecordContainerConflictException.Reason;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.master.PhysicalLocation;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.physical.MovementKind;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.PhysicalMovement;
import com.jokkorm.domain.physical.PhysicalMovementRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Range un dossier dans une boîte — pivot central phase 5C. Enchaîne :
 *
 * <ol>
 *   <li>Charger le record + la boîte (même tenant).</li>
 *   <li>Vérifier que la boîte a une position (sinon on ne peut pas remonter l'adresse au maître).</li>
 *   <li>Règle « un dossier, une seule boîte » : déjà dans cette boîte → refus sans écriture ;
 *       dans une autre boîte → refus, sauf transfert confirmé (et aucune des deux boîtes en prêt).</li>
 *   <li>Marquer le record {@code STORED} + lier au container.</li>
 *   <li>Marquer la boîte comme {@code OPEN} (EMPTY → OPEN auto).</li>
 *   <li>Récupérer l'adresse complète sur 5 niveaux (SITE/MAG/TRAV/TAB/POS) depuis la
 *       topographie, à partir du {@code path} dénormalisé ("DKR/MAG-B/14/3/07").</li>
 *   <li>Notifier le maître via {@link SaeMasterPort#notifyPhysicalLocation}.</li>
 *   <li>Émettre un évent d'audit {@code RECORD_STORED} avec la cote + barcode + path.</li>
 * </ol>
 *
 * <p>Le notify maître est best-effort (l'adapter catch ses propres erreurs), mais si
 * l'adapter lève une exception non catchée, le @Transactional rollback la mise à jour
 * record+container. On préfère cette cohérence stricte pour la phase 5C.
 */
@Service
public class StoreRecordInContainerUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(StoreRecordInContainerUseCase.class);

    private final PhysicalArchiveRecordRepository records;
    private final PhysicalContainerRepository containers;
    private final StorageLocationRepository locations;
    private final PhysicalMovementRepository movements;
    private final SaeMasterPort master;
    private final AuditChainService audit;
    private final Clock clock;
    private final com.jokkorm.application.master.MasterSyncService masterSync;
    private final PhysicalLoanRepository loans;

    public StoreRecordInContainerUseCase(
        PhysicalArchiveRecordRepository records,
        PhysicalContainerRepository containers,
        StorageLocationRepository locations,
        PhysicalMovementRepository movements,
        SaeMasterPort master,
        AuditChainService audit,
        Clock clock,
        com.jokkorm.application.master.MasterSyncService masterSync,
        PhysicalLoanRepository loans) {
        this.masterSync = masterSync;
        this.loans = loans;
        this.records = records;
        this.containers = containers;
        this.locations = locations;
        this.movements = movements;
        this.master = master;
        this.audit = audit;
        this.clock = clock;
    }

    /** Premier rangement : refusé si le dossier est déjà dans une boîte (voir {@link #execute(Cote, String, boolean)}). */
    @Transactional
    public PhysicalArchiveRecord execute(Cote cote, String containerBarcode) {
        return execute(cote, containerBarcode, false);
    }

    /**
     * Range le dossier dans la boîte. S'il est déjà dans une autre boîte, il n'en change que si
     * {@code transfer} est vrai (geste confirmé) et qu'aucune des deux boîtes n'est en prêt.
     * Déjà dans cette boîte : refusé, rien n'est écrit.
     */
    @Transactional
    public PhysicalArchiveRecord execute(Cote cote, String containerBarcode, boolean transfer) {
        TenantId tenant = TenantContext.current();

        PhysicalArchiveRecord record = records.findByCote(tenant, cote)
            .orElseThrow(() -> new RecordNotFoundException(
                "No physical archive record for cote=%s (tenant=%s)"
                    .formatted(cote.value(), tenant.value())));

        PhysicalContainer container = containers.findByBarcode(tenant, containerBarcode)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "No container with barcode=%s (tenant=%s)"
                    .formatted(containerBarcode, tenant.value())));

        java.util.UUID positionId = container.positionId()
            .orElseThrow(() -> new IllegalStateException(
                "Container %s has no position — place it first before storing records"
                    .formatted(container.barcode())));

        StorageLocation position = locations.findById(tenant, positionId)
            .orElseThrow(() -> new IllegalStateException(
                "Container %s points to a missing storage_location %s"
                    .formatted(container.barcode(), positionId)));

        if (position.kind() != StorageLocationKind.POSITION) {
            throw new IllegalStateException(
                "Container %s is attached to a %s, expected POSITION"
                    .formatted(container.barcode(), position.kind()));
        }

        // Un dossier n'est que dans une seule boîte : jamais de changement de boîte en silence.
        PhysicalContainer from = record.containerId()
            .map(id -> containers.findById(tenant, id).orElse(null))
            .orElse(null);
        if (record.containerId().isPresent()) {
            if (record.containerId().get().equals(container.id())) {
                throw conflict(Reason.ALREADY_IN_THIS_BOX, tenant, container,
                    "Le dossier %s est déjà dans cette boîte (%s) : rien à faire."
                        .formatted(cote.value(), describe(container)));
            }
            if (!transfer) {
                throw conflict(Reason.IN_ANOTHER_BOX, tenant, from,
                    "Le dossier %s est déjà rangé dans la boîte %s, %s. Un dossier ne peut être que dans "
                        .formatted(cote.value(), describe(from), where(tenant, from))
                        + "une seule boîte : pour le changer de boîte, confirmez le transfert.");
            }
            requireNoLoan(tenant, from, "d'origine");
            requireNoLoan(tenant, container, "d'arrivée");
        }

        java.time.Instant now = clock.instant();
        boolean firstIngress = record.containerId().isEmpty();

        // 1) mise à jour record + container
        if (record.containerId().isPresent()) {
            record.transferToContainer(container.id(), now);
        } else {
            record.storeInContainer(container.id(), now);
        }
        container.markHasContent(now);
        records.save(record);
        containers.save(container);

        // 2) journal physique : INGRESS pour un premier rangement, RELOCATE pour un transfert (phase 5D)
        MovementKind kind = firstIngress ? MovementKind.INGRESS : MovementKind.RELOCATE;
        String fromPart = from == null ? "" : " from=" + from.barcode();
        movements.save(PhysicalMovement.create(
            tenant, record.id(), container.id(), kind, "user", now,
            "container=%s%s path=%s".formatted(container.barcode(), fromPart, position.path())));

        // 3) notify master avec l'adresse complète (5 niveaux parsés depuis le path)
        PhysicalLocation address = resolveFiveLevelLocation(position);
        master.notifyPhysicalLocation(tenant, cote, address);
        masterSync.touchRecord(tenant, cote); // état physique affiché côté maître (file d'envoi)

        // 4) audit
        audit.record(
            AuditAction.RECORD_STORED,
            AuditTargetType.PHYSICAL_ARCHIVE_RECORD,
            record.id(),
            "user",
            "cote=%s container=%s%s path=%s kind=%s"
                .formatted(cote.value(), container.barcode(), fromPart, position.path(), kind));

        LOG.info("Record cote={} stored in container={}{} at path={} (kind={})",
            cote.value(), container.barcode(), fromPart, position.path(), kind);

        return record;
    }

    /** Une boîte en prêt (demandé ou sorti) ne gagne ni ne perd de dossier : son contenu est promis. */
    private void requireNoLoan(TenantId tenant, PhysicalContainer box, String role) {
        if (box == null) {
            return;
        }
        loans.findActiveByContainer(tenant, box.id()).ifPresent(loan -> {
            String state = loan.status().isActive() ? "est sortie en prêt" : "a une demande de prêt en cours";
            throw conflict(Reason.TRANSFER_BLOCKED, tenant, box,
                "Transfert impossible : la boîte %s %s %s. Terminez ou annulez le prêt d'abord."
                    .formatted(role, describe(box), state));
        });
    }

    private RecordContainerConflictException conflict(
        Reason reason, TenantId tenant, PhysicalContainer box, String message) {
        return new RecordContainerConflictException(reason, message,
            box == null ? null : box.barcode(),
            box == null ? null : box.label(),
            box == null ? null : where(tenant, box));
    }

    /** « BOX-261124-2BR4K8 « Dossiers du personnel 2026 » ». */
    private static String describe(PhysicalContainer box) {
        if (box == null) {
            return "(introuvable)";
        }
        return box.label() == null || box.label().isBlank()
            ? box.barcode()
            : "%s « %s »".formatted(box.barcode(), box.label().strip());
    }

    /** Adresse lisible de la boîte, même libellé que dans la GED maître. */
    private String where(TenantId tenant, PhysicalContainer box) {
        if (box == null) {
            return "emplacement inconnu";
        }
        return box.positionId()
            .flatMap(id -> locations.findById(tenant, id))
            .map(p -> masterSync.address(tenant, p))
            .orElse("pas encore sur une tablette");
    }

    /**
     * Décompose le {@code path} dénormalisé « DKR/MAG-B/14/3/07 » en PhysicalLocation à 5
     * segments. Si la hiérarchie est incomplète (ne devrait pas arriver vu la contrainte
     * domaine), on complète avec "N/A".
     */
    private PhysicalLocation resolveFiveLevelLocation(StorageLocation position) {
        String[] parts = position.path().split("/");
        String site = parts.length > 0 ? parts[0] : "N/A";
        String storehouse = parts.length > 1 ? parts[1] : "N/A";
        String shelf = parts.length > 2 ? parts[2] : "N/A";
        String rack = parts.length > 3 ? parts[3] : "N/A";
        String pos = parts.length > 4 ? parts[4] : "N/A";
        return new PhysicalLocation(site, storehouse, shelf, rack, pos);
    }
}
