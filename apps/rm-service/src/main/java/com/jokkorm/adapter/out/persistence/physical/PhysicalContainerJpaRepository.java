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

    /** {@code q} déjà échappé pour LIKE (\\, %, _). */
    @Query(value = """
        SELECT * FROM physical_container
        WHERE tenant_id = :tenantId AND state <> 'DESTROYED'
          AND (:q = '' OR upper(barcode) LIKE upper(:q) || '%' OR label ILIKE '%' || :q || '%')
        ORDER BY updated_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<PhysicalContainerEntity> search(@Param("tenantId") String tenantId, @Param("q") String q, @Param("limit") int limit);

    Optional<PhysicalContainerEntity> findByTenantIdAndBarcode(String tenantId, String barcode);

    boolean existsByTenantIdAndBarcode(String tenantId, String barcode);

    /** Verrou de transaction par espace (règle « un libellé, une seule boîte »). */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    Integer advisoryXactLock(@Param("key") String key);

    // Libellé normalisé : minuscules, blancs réduits à un espace, bords retirés.
    @Query(value = """
        SELECT * FROM physical_container
        WHERE tenant_id = :tenantId
          AND state <> 'DESTROYED'
          AND btrim(regexp_replace(lower(label), '\\s+', ' ', 'g'))
            = btrim(regexp_replace(lower(:label), '\\s+', ' ', 'g'))
        ORDER BY created_at
        LIMIT :limit
        """, nativeQuery = true)
    List<PhysicalContainerEntity> findActiveByNormalizedLabel(
        @Param("tenantId") String tenantId, @Param("label") String label, @Param("limit") int limit);

    // Le préfixe arrive déjà nettoyé et se termine par « · » + espace : pas de btrim dessus.
    @Query(value = """
        SELECT * FROM physical_container
        WHERE tenant_id = :tenantId
          AND state <> 'DESTROYED'
          AND left(btrim(regexp_replace(lower(label), '\\s+', ' ', 'g')),
                   char_length(regexp_replace(lower(:prefix), '\\s+', ' ', 'g')))
            = regexp_replace(lower(:prefix), '\\s+', ' ', 'g')
        ORDER BY created_at
        LIMIT :limit
        """, nativeQuery = true)
    List<PhysicalContainerEntity> findActiveByNormalizedLabelPrefix(
        @Param("tenantId") String tenantId, @Param("prefix") String prefix, @Param("limit") int limit);

    @Query(value = """
        SELECT count(*) FROM physical_container
        WHERE tenant_id = :tenantId
          AND state <> 'DESTROYED'
          AND left(btrim(regexp_replace(lower(label), '\\s+', ' ', 'g')),
                   char_length(regexp_replace(lower(:prefix), '\\s+', ' ', 'g')))
            = regexp_replace(lower(:prefix), '\\s+', ' ', 'g')
        """, nativeQuery = true)
    long countActiveByNormalizedLabelPrefix(@Param("tenantId") String tenantId, @Param("prefix") String prefix);

    List<PhysicalContainerEntity> findByTenantIdAndPositionIdInOrderByBarcodeAsc(
        String tenantId, java.util.Collection<UUID> positionIds);

    List<PhysicalContainerEntity> findByTenantIdAndPositionIdOrderByBarcodeAsc(
        String tenantId, UUID positionId);

    Page<PhysicalContainerEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Phase 5O — boîtes toujours en zone tampon (position_id IS NULL) entrées dans la zone avant
     * {@code threshold}, groupées par batch_id (celles sans batch_id sont exclues
     * car elles ont été créées unitairement, hors scénario d'alerte lot).
     * Entrée dans la zone = le plus récent de la création, du pointage d'arrivée et du dernier
     * mouvement (une boîte retirée de sa tablette repart de zéro, pattern 12).
     * Retourne Object[] { batch_id (UUID), reference (String), count (Long), oldest_created_at (Timestamp) }
     * ordonnés par plus vieux d'abord.
     */
    @Query(value = """
        SELECT c.batch_id,
               b.reference,
               COUNT(*) as stale_count,
               MIN(GREATEST(c.created_at, c.received_at, c.last_moved_at)) as oldest
        FROM physical_container c
        JOIN container_batch b ON b.id = c.batch_id
        WHERE c.tenant_id = :tenantId
          AND c.position_id IS NULL
          AND c.state <> 'DESTROYED'
          AND GREATEST(c.created_at, c.received_at, c.last_moved_at) < :threshold
          AND c.batch_id IS NOT NULL
        GROUP BY c.batch_id, b.reference
        ORDER BY MIN(GREATEST(c.created_at, c.received_at, c.last_moved_at)) ASC
        """, nativeQuery = true)
    List<Object[]> findStaleBufferGroupedByBatch(
        @Param("tenantId") String tenantId,
        @Param("threshold") Instant threshold);
}
