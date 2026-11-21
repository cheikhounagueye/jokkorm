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
 * Événement entrant du maître : une nouvelle cote a été attribuée à un dossier
 * clos. On crée l'enveloppe physique correspondante. Idempotent : rejoue sans
 * incident si la cote existe déjà (pas de nouvel évent d'audit non plus).
 */
@Service
public class HandleCoteAssignedEventUseCase {

    private final PhysicalArchiveRecordRepository repository;
    private final AuditChainService audit;
    private final Clock clock;

    public HandleCoteAssignedEventUseCase(
        PhysicalArchiveRecordRepository repository, AuditChainService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalArchiveRecord execute(TenantId tenant, Cote cote) {
        var existing = repository.findByCote(tenant, cote);
        if (existing.isPresent()) {
            return existing.get();
        }
        PhysicalArchiveRecord saved = repository.save(
            PhysicalArchiveRecord.register(tenant, cote, clock.instant()));
        audit.record(
            AuditAction.COTE_ASSIGNED,
            AuditTargetType.PHYSICAL_ARCHIVE_RECORD,
            saved.id(),
            resolveActor(),
            "cote=" + cote.value());
        return saved;
    }

    /** Phase 3A — actor par défaut en l'absence de SecurityContext riche. Phase 3C enrichira. */
    private String resolveActor() {
        return "master-event";
    }
}
