package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhysicalContainerRepository {

    PhysicalContainer save(PhysicalContainer container);

    Optional<PhysicalContainer> findById(TenantId tenant, UUID id);

    Optional<PhysicalContainer> findByBarcode(TenantId tenant, String barcode);

    boolean existsByBarcode(TenantId tenant, String barcode);

    /**
     * Verrou de transaction sur les libellés de boîtes d'un espace : sérialise les créations
     * pour que le contrôle « un libellé, une seule boîte » ne laisse pas passer deux doublons
     * simultanés. Libéré à la fin de la transaction.
     */
    void lockLabels(TenantId tenant);

    /** Boîtes non détruites portant ce libellé, casse et espaces en trop ignorés. */
    List<PhysicalContainer> findActiveByLabel(TenantId tenant, String label, int limit);

    /** Boîtes non détruites dont le libellé commence par {@code prefix}, casse et espaces ignorés. */
    List<PhysicalContainer> findActiveByLabelPrefix(TenantId tenant, String prefix, int limit);

    long countActiveByLabelPrefix(TenantId tenant, String prefix);

    /** Boîtes rangées à une position donnée. */
    List<PhysicalContainer> findByPosition(TenantId tenant, UUID positionId);

    /** Boîtes rangées sur un ensemble de positions, en une requête (vue d'une salle entière). */
    List<PhysicalContainer> findByPositions(TenantId tenant, java.util.Collection<UUID> positionIds);

    Page<PhysicalContainer> findAll(TenantId tenant, PageRequest pageRequest);

    /**
     * Phase 5O — boîtes en zone tampon (positionId=null) créées avant {@code threshold},
     * groupées par lot pour l'alerte "depuis N jours".
     */
    List<StaleBufferEntry> findStaleBufferByBatch(TenantId tenant, Instant threshold);

    /** Agrégat retourné par {@link #findStaleBufferByBatch} : 1 ligne par lot stale. */
    record StaleBufferEntry(
        UUID batchId,
        String batchReference,
        long staleCount,
        Instant oldestCreatedAt) {}
}
