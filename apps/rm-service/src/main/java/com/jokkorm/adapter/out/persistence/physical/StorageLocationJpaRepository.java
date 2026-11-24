package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.domain.physical.StorageLocationKind;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StorageLocationJpaRepository extends JpaRepository<StorageLocationEntity, UUID> {

    Optional<StorageLocationEntity> findByIdAndTenantId(UUID id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from StorageLocationEntity e where e.id = :id and e.tenantId = :tenantId")
    Optional<StorageLocationEntity> lockByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") String tenantId);

    Optional<StorageLocationEntity> findByTenantIdAndPath(String tenantId, String path);

    Optional<StorageLocationEntity> findByTenantIdAndScanCode(String tenantId, String scanCode);

    /** starts_with plutôt que LIKE : les codes peuvent contenir « _ », joker de LIKE. */
    @Query(value = """
        SELECT * FROM storage_location
        WHERE tenant_id = :tenantId AND kind = 'POSITION'
          AND (path = :path OR starts_with(path, :path || '/'))
        ORDER BY path
        """, nativeQuery = true)
    List<StorageLocationEntity> findPositionsUnder(@Param("tenantId") String tenantId, @Param("path") String path);

    List<StorageLocationEntity> findByTenantIdAndParentIdOrderByCodeAsc(String tenantId, UUID parentId);

    List<StorageLocationEntity> findByTenantIdAndKindAndParentIdIsNullOrderByCodeAsc(
        String tenantId, StorageLocationKind kind);

    boolean existsByTenantIdAndParentIdAndCode(String tenantId, UUID parentId, String code);

    boolean existsByTenantIdAndKindAndParentIdIsNullAndCode(
        String tenantId, StorageLocationKind kind, String code);
}
