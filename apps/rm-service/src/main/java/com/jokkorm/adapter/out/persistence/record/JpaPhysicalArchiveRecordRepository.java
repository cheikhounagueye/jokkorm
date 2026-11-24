package com.jokkorm.adapter.out.persistence.record;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Adapter JPA pour {@link PhysicalArchiveRecordRepository}. Fait le pont entre domaine
 * et {@link PhysicalArchiveRecordJpaRepository}.
 */
@Repository
public class JpaPhysicalArchiveRecordRepository implements PhysicalArchiveRecordRepository {

    private final PhysicalArchiveRecordJpaRepository jpa;

    public JpaPhysicalArchiveRecordRepository(PhysicalArchiveRecordJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PhysicalArchiveRecord save(PhysicalArchiveRecord record) {
        PhysicalArchiveRecordEntity existing = jpa.findById(record.id()).orElse(null);
        PhysicalArchiveRecordEntity saved;
        if (existing == null) {
            saved = jpa.save(PhysicalArchiveRecordEntity.fromDomain(record));
        } else {
            if (!existing.tenantId().equals(record.tenantId().value())) {
                throw new IllegalStateException(
                    "Tenant mismatch on save: entity=%s domain=%s"
                        .formatted(existing.tenantId(), record.tenantId().value()));
            }
            existing.copyMutableStateFrom(record);
            saved = jpa.save(existing);
        }
        return saved.toDomain();
    }

    @Override
    public Optional<PhysicalArchiveRecord> findById(TenantId tenant, UUID id) {
        return jpa.findByIdAndTenantId(id, tenant.value())
            .map(PhysicalArchiveRecordEntity::toDomain);
    }

    @Override
    public Optional<PhysicalArchiveRecord> findByCote(TenantId tenant, Cote cote) {
        return jpa.findByTenantIdAndCote(tenant.value(), cote.value())
            .map(PhysicalArchiveRecordEntity::toDomain);
    }

    @Override
    public java.util.List<PhysicalArchiveRecord> findByContainer(TenantId tenant, UUID containerId) {
        return jpa.findByTenantIdAndContainerIdOrderByCote(tenant.value(), containerId).stream()
            .map(PhysicalArchiveRecordEntity::toDomain)
            .toList();
    }

    @Override
    public long countByContainer(TenantId tenant, UUID containerId) {
        return jpa.countByTenantIdAndContainerId(tenant.value(), containerId);
    }

    @Override
    public Page<PhysicalArchiveRecord> findToCondition(TenantId tenant, PageRequest pageRequest) {
        var page = jpa.findByTenantIdAndContainerIdIsNullAndStatusInOrderByRegisteredAtAscCoteAsc(
            tenant.value(), List.of(PhysicalArchiveStatus.REGISTERED, PhysicalArchiveStatus.FROZEN),
            toPageable(pageRequest));
        return map(page, pageRequest);
    }

    @Override
    public boolean existsByCote(TenantId tenant, Cote cote) {
        return jpa.existsByTenantIdAndCote(tenant.value(), cote.value());
    }

    @Override
    public Page<PhysicalArchiveRecord> findAll(TenantId tenant, PageRequest pageRequest) {
        var page = jpa.findByTenantIdOrderByRegisteredAtDesc(
            tenant.value(), toPageable(pageRequest));
        return map(page, pageRequest);
    }

    @Override
    public Page<PhysicalArchiveRecord> findAllByStatus(
        TenantId tenant, PhysicalArchiveStatus status, PageRequest pageRequest) {
        if (status == null) {
            return findAll(tenant, pageRequest);
        }
        var page = jpa.findByTenantIdAndStatusOrderByRegisteredAtDesc(
            tenant.value(), status, toPageable(pageRequest));
        return map(page, pageRequest);
    }

    private static Pageable toPageable(PageRequest r) {
        return org.springframework.data.domain.PageRequest.of(r.page(), r.size());
    }

    private static Page<PhysicalArchiveRecord> map(
        org.springframework.data.domain.Page<PhysicalArchiveRecordEntity> spring,
        PageRequest request) {
        List<PhysicalArchiveRecord> content = spring.getContent().stream()
            .map(PhysicalArchiveRecordEntity::toDomain)
            .toList();
        return new Page<>(content, request.page(), request.size(), spring.getTotalElements());
    }
}
