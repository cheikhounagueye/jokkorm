package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.record.RecordNotFoundException;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.master.MovementEvent;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.physical.MovementKind;
import com.jokkorm.domain.physical.PhysicalMovement;
import com.jokkorm.domain.physical.PhysicalMovementRepository;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enregistre un mouvement physique dans le journal append-only. Déclenche en cascade :
 * <ol>
 *   <li>INSERT dans {@code physical_movement}.</li>
 *   <li>Notification au maître via {@link SaeMasterPort#notifyMovement}.</li>
 *   <li>Évent d'audit {@code MOVEMENT_RECORDED} chaîné HMAC.</li>
 * </ol>
 *
 * <p>Pas de validation « ce verbe est-il permis dans ce statut » en 5D — on fait confiance
 * à l'opérateur. Phase ultérieure si on veut gatter LOAN_OUT ↔ LOAN_IN dans le workflow prêts.
 */
@Service
public class RecordMovementUseCase {

    private final PhysicalArchiveRecordRepository records;
    private final PhysicalMovementRepository movements;
    private final SaeMasterPort master;
    private final AuditChainService audit;
    private final Clock clock;

    public RecordMovementUseCase(
        PhysicalArchiveRecordRepository records,
        PhysicalMovementRepository movements,
        SaeMasterPort master,
        AuditChainService audit,
        Clock clock) {
        this.records = records;
        this.movements = movements;
        this.master = master;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalMovement execute(Cote cote, MovementKind kind, String actor, String motive) {
        TenantId tenant = TenantContext.current();

        PhysicalArchiveRecord record = records.findByCote(tenant, cote)
            .orElseThrow(() -> new RecordNotFoundException(
                "No physical archive record for cote=%s (tenant=%s)"
                    .formatted(cote.value(), tenant.value())));

        java.time.Instant now = clock.instant();
        PhysicalMovement movement = PhysicalMovement.create(
            tenant, record.id(), record.containerId().orElse(null),
            kind, actor, now, motive);
        movements.save(movement);

        master.notifyMovement(tenant, cote,
            new MovementEvent(translate(kind), actor, now, motive));

        audit.record(
            AuditAction.MOVEMENT_RECORDED,
            AuditTargetType.PHYSICAL_ARCHIVE_RECORD,
            record.id(),
            actor,
            "kind=%s cote=%s motive=%s"
                .formatted(kind, cote.value(), motive == null ? "" : motive));

        return movement;
    }

    /**
     * Traduit {@link MovementKind} du domaine physique vers {@link MovementEvent.Kind} du
     * domaine master — les deux enums sont volontairement identiques en 5D, mais on garde
     * la fonction pour pouvoir évoluer indépendamment (ex. ajouter des sous-types côté phys).
     */
    private static MovementEvent.Kind translate(MovementKind k) {
        return switch (k) {
            case INGRESS -> MovementEvent.Kind.INGRESS;
            case RELOCATE -> MovementEvent.Kind.RELOCATE;
            case LOAN_OUT -> MovementEvent.Kind.LOAN_OUT;
            case LOAN_IN -> MovementEvent.Kind.LOAN_IN;
            case DIGITIZE_REQUEST -> MovementEvent.Kind.DIGITIZE_REQUEST;
            case EGRESS -> MovementEvent.Kind.EGRESS;
        };
    }
}
