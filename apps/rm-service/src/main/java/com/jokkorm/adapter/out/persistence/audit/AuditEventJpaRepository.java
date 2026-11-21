package com.jokkorm.adapter.out.persistence.audit;

import com.jokkorm.domain.audit.AuditTargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, UUID> {

    List<AuditEventEntity> findByTenantIdAndTargetTypeAndTargetIdOrderByTsDesc(
        String tenantId, AuditTargetType targetType, UUID targetId, PageRequest page);

    List<AuditEventEntity> findByTenantIdAndTargetTypeAndTargetIdOrderByTsAsc(
        String tenantId, AuditTargetType targetType, UUID targetId);

    default Optional<AuditEventEntity> findLast(
        String tenantId, AuditTargetType targetType, UUID targetId) {
        return findByTenantIdAndTargetTypeAndTargetIdOrderByTsDesc(
            tenantId, targetType, targetId, PageRequest.of(0, 1)).stream().findFirst();
    }
}
