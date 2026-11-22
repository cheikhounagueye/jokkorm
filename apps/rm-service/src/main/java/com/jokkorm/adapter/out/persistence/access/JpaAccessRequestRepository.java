package com.jokkorm.adapter.out.persistence.access;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.access.AccessRequest;
import com.jokkorm.domain.access.AccessRequestRepository;
import com.jokkorm.domain.access.AccessRequestStatus;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAccessRequestRepository implements AccessRequestRepository {

    private final AccessRequestJpaRepository jpa;

    public JpaAccessRequestRepository(AccessRequestJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public AccessRequest save(AccessRequest r) {
        AccessRequestEntity existing = jpa.findById(r.id()).orElse(null);
        AccessRequestEntity saved;
        if (existing == null) {
            saved = jpa.save(AccessRequestEntity.fromDomain(r));
        } else {
            if (!existing.tenantId().equals(r.tenantId().value())) {
                throw new IllegalStateException("Tenant mismatch on access_request save");
            }
            existing.copyMutableFrom(r);
            saved = jpa.save(existing);
        }
        return saved.toDomain();
    }

    @Override
    public Optional<AccessRequest> findById(TenantId tenant, UUID id) {
        return jpa.findByIdAndTenantId(id, tenant.value()).map(AccessRequestEntity::toDomain);
    }

    @Override
    public Page<AccessRequest> findAll(TenantId tenant, PageRequest pageRequest) {
        var page = jpa.findByTenantIdOrderByCreatedAtDesc(
            tenant.value(),
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
        List<AccessRequest> content = page.getContent().stream()
            .map(AccessRequestEntity::toDomain).toList();
        return new Page<>(content, pageRequest.page(), pageRequest.size(), page.getTotalElements());
    }

    @Override
    public List<AccessRequest> findByRequester(TenantId tenant, String requesterId) {
        return jpa.findByTenantIdAndRequesterIdOrderByCreatedAtDesc(tenant.value(), requesterId)
            .stream().map(AccessRequestEntity::toDomain).toList();
    }

    @Override
    public List<AccessRequest> findSubmitted(TenantId tenant) {
        return findByStatus(tenant, AccessRequestStatus.SUBMITTED);
    }

    @Override
    public List<AccessRequest> findQualified(TenantId tenant) {
        return findByStatus(tenant, AccessRequestStatus.QUALIFIED);
    }

    @Override
    public List<AccessRequest> findApproved(TenantId tenant) {
        return findByStatus(tenant, AccessRequestStatus.APPROVED);
    }

    private List<AccessRequest> findByStatus(TenantId tenant, AccessRequestStatus status) {
        return jpa.findByTenantIdAndStatusOrderByCreatedAtAsc(tenant.value(), status)
            .stream().map(AccessRequestEntity::toDomain).toList();
    }
}
