package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.Optional;
import java.util.UUID;

/** Port de persistance des caractéristiques de salle (une par magasin). */
public interface RoomSpecRepository {

    Optional<RoomSpec> findByStorehouse(TenantId tenant, UUID storehouseId);

    RoomSpec save(RoomSpec spec, java.time.Instant at);
}
