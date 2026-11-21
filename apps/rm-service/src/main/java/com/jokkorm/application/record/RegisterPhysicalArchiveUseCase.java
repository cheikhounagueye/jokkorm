package com.jokkorm.application.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * Use case : enregistrer une cote reçue du maître (événement {@code cote-assigned}).
 * Idempotent — rejoue sans incident si la cote existe déjà.
 *
 * <p>Phase 1 : logique minimale, pas d'appel au {@code SaeMasterPort} ici (pas d'effet de
 * bord sortant lors de la réception d'un événement entrant). La propagation inverse arrive
 * en phase 3 avec les cas d'usage « magasin ».
 */
public class RegisterPhysicalArchiveUseCase {

    private final PhysicalArchiveRecordRepository repository;
    private final Clock clock;

    public RegisterPhysicalArchiveUseCase(PhysicalArchiveRecordRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PhysicalArchiveRecord execute(TenantId tenant, Cote cote) {
        return repository.findByCote(tenant, cote).orElseGet(() -> {
            PhysicalArchiveRecord fresh = PhysicalArchiveRecord.register(tenant, cote, clock.instant());
            return repository.save(fresh);
        });
    }
}
