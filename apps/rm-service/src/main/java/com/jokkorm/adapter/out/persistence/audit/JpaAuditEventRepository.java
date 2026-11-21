package com.jokkorm.adapter.out.persistence.audit;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditEvent;
import com.jokkorm.domain.audit.AuditEventRepository;
import com.jokkorm.domain.audit.AuditTargetType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAuditEventRepository implements AuditEventRepository {

    private final AuditEventJpaRepository jpa;

    public JpaAuditEventRepository(AuditEventJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public AuditEvent save(AuditEvent event) {
        return jpa.save(AuditEventEntity.fromDomain(event)).toDomain();
    }

    @Override
    public Optional<AuditEvent> findLast(TenantId tenant, AuditTargetType targetType, UUID targetId) {
        return jpa.findLast(tenant.value(), targetType, targetId)
            .map(AuditEventEntity::toDomain);
    }

    @Override
    public List<AuditEvent> findChain(TenantId tenant, AuditTargetType targetType, UUID targetId) {
        return jpa.findByTenantIdAndTargetTypeAndTargetIdOrderByTsAsc(
                tenant.value(), targetType, targetId)
            .stream().map(AuditEventEntity::toDomain).toList();
    }
}
