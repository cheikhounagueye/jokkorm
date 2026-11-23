package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.List;
import java.util.Optional;

public interface MasterWatchStateRepository {

    Optional<MasterWatchState> find(TenantId tenant);

    void save(MasterWatchState state);

    /** Tous les tenants connus : le veilleur garde ceux dont le maître publie un journal. */
    List<TenantId> tenants();
}
