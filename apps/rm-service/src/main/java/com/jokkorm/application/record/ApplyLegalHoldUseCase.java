package com.jokkorm.application.record;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Événement entrant du maître : gel ou dégel contentieux sur une archive. L'enveloppe
 * physique passe à {@code FROZEN} (toute sortie bloquée) ou revient à {@code STORED}.
 * Audit chaîné par cible — chaque transition ajoute un maillon.
 */
@Service
public class ApplyLegalHoldUseCase {

    public enum Action { FREEZE, RELEASE }

    private final PhysicalArchiveRecordRepository repository;
    private final AuditChainService audit;
    private final Clock clock;

    public ApplyLegalHoldUseCase(
        PhysicalArchiveRecordRepository repository, AuditChainService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalArchiveRecord execute(TenantId tenant, Cote cote, Action action) {
        return execute(tenant, cote, action, "master-event", null);
    }

    /**
     * @param actor qui applique (ex. {@code veilleur-maarch})
     * @param note  référence croisée ajoutée à l'audit (ex. {@code evenement-maitre=…}), sans données personnelles
     */
    @Transactional
    public PhysicalArchiveRecord execute(TenantId tenant, Cote cote, Action action, String actor, String note) {
        PhysicalArchiveRecord record = repository.findByCote(tenant, cote)
            .orElseThrow(() -> new RecordNotFoundException(
                "No physical archive record for cote=%s (tenant=%s)"
                    .formatted(cote.value(), tenant.value())));
        switch (action) {
            case FREEZE -> record.markFrozen(clock.instant());
            case RELEASE -> record.releaseFreeze(clock.instant());
        }
        PhysicalArchiveRecord saved = repository.save(record);

        AuditAction auditAction = switch (action) {
            case FREEZE -> AuditAction.LEGAL_HOLD_APPLIED;
            case RELEASE -> AuditAction.LEGAL_HOLD_RELEASED;
        };
        audit.record(
            auditAction,
            AuditTargetType.PHYSICAL_ARCHIVE_RECORD,
            saved.id(),
            actor,
            "cote=" + cote.value() + (note == null ? "" : "; " + note));

        return saved;
    }
}
