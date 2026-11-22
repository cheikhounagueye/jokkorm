package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.domain.physical.ContainerState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContainerBatchJpaRepository extends JpaRepository<ContainerBatchEntity, UUID> {

    Optional<ContainerBatchEntity> findByIdAndTenantId(UUID id, String tenantId);

    Page<ContainerBatchEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /** Count containers by state for a batch — single query, grouped. */
    @Query(value = """
        SELECT state, COUNT(*)
        FROM physical_container
        WHERE tenant_id = :tenantId AND batch_id = :batchId
        GROUP BY state
        """, nativeQuery = true)
    List<Object[]> countByState(@Param("tenantId") String tenantId, @Param("batchId") UUID batchId);

    /** Count containers that are in transit (no position) for a batch. */
    @Query(value = """
        SELECT COUNT(*)
        FROM physical_container
        WHERE tenant_id = :tenantId AND batch_id = :batchId AND position_id IS NULL
        """, nativeQuery = true)
    long countInTransit(@Param("tenantId") String tenantId, @Param("batchId") UUID batchId);

    /** Total containers in a batch (should equal batch.size, invariant). */
    @Query(value = """
        SELECT COUNT(*)
        FROM physical_container
        WHERE tenant_id = :tenantId AND batch_id = :batchId
        """, nativeQuery = true)
    long countTotal(@Param("tenantId") String tenantId, @Param("batchId") UUID batchId);
}
