package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MasterAlertRepository {

    /** Enregistre le signalement s'il n'existe pas déjà pour cet événement et ce type. Vrai si créé. */
    boolean insertIfAbsent(MasterAlert alert);

    /** Les plus récents d'abord. {@code onlyOpenActions} : seulement ceux qui demandent une action, non traités. */
    List<MasterAlert> list(TenantId tenant, boolean onlyOpenActions, int limit);

    int countOpenActions(TenantId tenant);

    /** Marque traité ; faux si inconnu ou déjà traité. */
    boolean acknowledge(TenantId tenant, UUID id, String by, Instant at);
}
