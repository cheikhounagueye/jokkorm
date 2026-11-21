package com.jokkorm.domain.record;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.Optional;
import java.util.UUID;

/**
 * Port de persistance pour {@link PhysicalArchiveRecord}. Les implémentations vivent
 * dans {@code adapter/out/persistence/record/}.
 *
 * <p>Tous les lookups portent {@link TenantId} en premier paramètre — ADR-0002.
 */
public interface PhysicalArchiveRecordRepository {

    PhysicalArchiveRecord save(PhysicalArchiveRecord record);

    Optional<PhysicalArchiveRecord> findById(TenantId tenant, UUID id);

    Optional<PhysicalArchiveRecord> findByCote(TenantId tenant, Cote cote);

    boolean existsByCote(TenantId tenant, Cote cote);
}
