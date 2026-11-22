package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.List;
import java.util.UUID;

/**
 * Port — append-only. La table SQL porte un trigger qui refuse UPDATE et DELETE
 * (voir migration 0007). L'application n'expose que {@code save} et des lectures.
 */
public interface PhysicalMovementRepository {

    PhysicalMovement save(PhysicalMovement movement);

    /** Timeline chronologique (ordre ascendant) des mouvements pour un record donné. */
    List<PhysicalMovement> findByRecord(TenantId tenant, UUID recordId);
}
