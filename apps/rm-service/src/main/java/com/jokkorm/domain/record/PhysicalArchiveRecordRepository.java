package com.jokkorm.domain.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
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

    /** Archives rangées dans une boîte (pour savoir ce qui sortirait avec elle). */
    java.util.List<PhysicalArchiveRecord> findByContainer(TenantId tenant, UUID containerId);

    /** Liste paginée triée par date d'enregistrement décroissante. */
    Page<PhysicalArchiveRecord> findAll(TenantId tenant, PageRequest pageRequest);

    /**
     * Même pagination, filtré sur un statut précis. {@code null} = pas de filtre.
     */
    Page<PhysicalArchiveRecord> findAllByStatus(
        TenantId tenant, PhysicalArchiveStatus status, PageRequest pageRequest);
}
