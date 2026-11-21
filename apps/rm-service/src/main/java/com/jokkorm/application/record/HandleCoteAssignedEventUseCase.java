package com.jokkorm.application.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * Événement entrant du maître : une nouvelle cote a été attribuée à un dossier
 * clos. On crée l'enveloppe physique correspondante. Idempotent : rejoue sans
 * incident si la cote existe déjà.
 */
@Service
public class HandleCoteAssignedEventUseCase {

    private final PhysicalArchiveRecordRepository repository;
    private final Clock clock;

    public HandleCoteAssignedEventUseCase(
        PhysicalArchiveRecordRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public PhysicalArchiveRecord execute(TenantId tenant, Cote cote) {
        return repository.findByCote(tenant, cote).orElseGet(() ->
            repository.save(PhysicalArchiveRecord.register(tenant, cote, clock.instant())));
    }
}
