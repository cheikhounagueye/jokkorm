package com.jokkorm.domain.master;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Copie locale en lecture seule des données de rangement du maître (D3). */
public interface MasterRecordSnapshotRepository {

    Optional<MasterRecordSnapshot> find(TenantId tenant, UUID recordId);

    /** Copies des dossiers demandés qui en ont une (les autres sont absents de la table). */
    Map<UUID, MasterRecordSnapshot> findAll(TenantId tenant, Collection<UUID> recordIds);

    /** Crée ou remplace la copie du dossier. */
    void save(MasterRecordSnapshot snapshot);

    /** Dossier encore suivi (ni détruit ni versé) dont la copie manque ou date d'avant {@code before}. */
    record Stale(UUID recordId, Cote cote) {}

    List<Stale> findStale(TenantId tenant, Instant before, int limit);

    /** Tous les espaces (relecture périodique). */
    List<TenantId> tenants();
}
