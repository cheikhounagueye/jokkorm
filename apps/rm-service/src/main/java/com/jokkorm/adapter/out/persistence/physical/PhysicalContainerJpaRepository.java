package com.jokkorm.adapter.out.persistence.physical;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhysicalContainerJpaRepository extends JpaRepository<PhysicalContainerEntity, UUID> {

    Optional<PhysicalContainerEntity> findByIdAndTenantId(UUID id, String tenantId);

    Optional<PhysicalContainerEntity> findByTenantIdAndBarcode(String tenantId, String barcode);

    boolean existsByTenantIdAndBarcode(String tenantId, String barcode);

    List<PhysicalContainerEntity> findByTenantIdAndPositionIdOrderByBarcodeAsc(
        String tenantId, UUID positionId);

    Page<PhysicalContainerEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Phase 5O — boîtes toujours en zone tampon (position_id IS NULL) créées avant
     * {@code threshold}, groupées par batch_id (celles sans batch_id sont exclues
     * car elles ont été créées unitairement, hors scénario d'alerte lot).
     * Retourne Object[] { batch_id (UUID), reference (String), count (Long), oldest_created_at (Timestamp) }
     * ordonnés par plus vieux d'abord.
     */
    @Query(value = """
        SELECT c.batch_id,
               b.reference,
               COUNT(*) as stale_count,
               MIN(c.created_at) as oldest
        FROM physical_container c
        JOIN container_batch b ON b.id = c.batch_id
        WHERE c.tenant_id = :tenantId
          AND c.position_id IS NULL
          AND c.state <> 'DESTROYED'
          AND c.created_at < :threshold
          AND c.batch_id IS NOT NULL
        GROUP BY c.batch_id, b.reference
        ORDER BY MIN(c.created_at) ASC
        """, nativeQuery = true)
    List<Object[]> findStaleBufferGroupedByBatch(
        @Param("tenantId") String tenantId,
        @Param("threshold") Instant threshold);
}
