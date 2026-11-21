package com.jokkorm.application.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * Événement entrant du maître : gel ou dégel contentieux sur une archive. L'enveloppe
 * physique passe à {@code FROZEN} (toute sortie bloquée) ou revient à {@code STORED}.
 */
@Service
public class ApplyLegalHoldUseCase {

    public enum Action { FREEZE, RELEASE }

    private final PhysicalArchiveRecordRepository repository;
    private final Clock clock;

    public ApplyLegalHoldUseCase(PhysicalArchiveRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PhysicalArchiveRecord execute(TenantId tenant, Cote cote, Action action) {
        PhysicalArchiveRecord record = repository.findByCote(tenant, cote)
            .orElseThrow(() -> new RecordNotFoundException(
                "No physical archive record for cote=%s (tenant=%s)"
                    .formatted(cote.value(), tenant.value())));
        switch (action) {
            case FREEZE -> record.markFrozen(clock.instant());
            case RELEASE -> record.releaseFreeze(clock.instant());
        }
        return repository.save(record);
    }
}
