package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.domain.physical.StorageLocationKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageLocationJpaRepository extends JpaRepository<StorageLocationEntity, UUID> {

    Optional<StorageLocationEntity> findByIdAndTenantId(UUID id, String tenantId);

    Optional<StorageLocationEntity> findByTenantIdAndPath(String tenantId, String path);

    List<StorageLocationEntity> findByTenantIdAndParentIdOrderByCodeAsc(String tenantId, UUID parentId);

    List<StorageLocationEntity> findByTenantIdAndKindAndParentIdIsNullOrderByCodeAsc(
        String tenantId, StorageLocationKind kind);

    boolean existsByTenantIdAndParentIdAndCode(String tenantId, UUID parentId, String code);

    boolean existsByTenantIdAndKindAndParentIdIsNullAndCode(
        String tenantId, StorageLocationKind kind, String code);
}
