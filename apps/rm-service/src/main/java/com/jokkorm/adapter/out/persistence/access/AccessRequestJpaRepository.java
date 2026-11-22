package com.jokkorm.adapter.out.persistence.access;

import com.jokkorm.domain.access.AccessRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessRequestJpaRepository extends JpaRepository<AccessRequestEntity, UUID> {
    Optional<AccessRequestEntity> findByIdAndTenantId(UUID id, String tenantId);
    Page<AccessRequestEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
    List<AccessRequestEntity> findByTenantIdAndRequesterIdOrderByCreatedAtDesc(String tenantId, String requesterId);
    List<AccessRequestEntity> findByTenantIdAndStatusOrderByCreatedAtAsc(String tenantId, AccessRequestStatus status);
}
