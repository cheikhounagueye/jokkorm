package com.jokkorm.adapter.out.persistence.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.physical.ContainerBatch;
import com.jokkorm.domain.physical.ContainerBatchRepository;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.PhysicalContainer;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public class JpaContainerBatchRepository implements ContainerBatchRepository {

    private final ContainerBatchJpaRepository jpa;
    private final PhysicalContainerJpaRepository containerJpa;

    public JpaContainerBatchRepository(
        ContainerBatchJpaRepository jpa,
        PhysicalContainerJpaRepository containerJpa) {
        this.jpa = jpa;
        this.containerJpa = containerJpa;
    }

    @Override
    public ContainerBatch save(ContainerBatch batch) {
        ContainerBatchEntity existing = jpa.findById(batch.id()).orElse(null);
        ContainerBatchEntity saved;
        if (existing == null) {
            saved = jpa.save(ContainerBatchEntity.fromDomain(batch));
        } else {
            if (!existing.tenantId().equals(batch.tenantId().value())) {
                throw new IllegalStateException(
                    "Tenant mismatch on batch save: entity=%s domain=%s"
                        .formatted(existing.tenantId(), batch.tenantId().value()));
            }
            existing.copyMutableStateFrom(batch);
            saved = jpa.save(existing);
        }
        return saved.toDomain();
    }

    @Override
    public Optional<ContainerBatch> findById(TenantId tenant, UUID id) {
        return jpa.findByIdAndTenantId(id, tenant.value())
            .map(ContainerBatchEntity::toDomain);
    }

    @Override
    public Page<ContainerBatch> findAll(TenantId tenant, PageRequest pageRequest) {
        var page = jpa.findByTenantIdOrderByCreatedAtDesc(
            tenant.value(),
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
        List<ContainerBatch> content = page.getContent().stream()
            .map(ContainerBatchEntity::toDomain).toList();
        return new Page<>(content, pageRequest.page(), pageRequest.size(), page.getTotalElements());
    }

    @Override
    public List<PhysicalContainer> listContainers(TenantId tenant, UUID batchId) {
        return containerJpa.findAll().stream()
            .filter(e -> tenant.value().equals(e.tenantId())
                && e.toDomain().batchId().map(b -> b.equals(batchId)).orElse(false))
            .sorted((a, b) -> {
                Integer sa = a.toDomain().sequenceInBatch().orElse(Integer.MAX_VALUE);
                Integer sb = b.toDomain().sequenceInBatch().orElse(Integer.MAX_VALUE);
                return sa.compareTo(sb);
            })
            .map(PhysicalContainerEntity::toDomain)
            .toList();
    }

    @Override
    public BatchCounts countContainersByState(TenantId tenant, UUID batchId) {
        long total = jpa.countTotal(tenant.value(), batchId);
        long inTransit = jpa.countInTransit(tenant.value(), batchId);
        long storedEmpty = 0, open = 0, sealed = 0, destroyed = 0;
        for (Object[] row : jpa.countByState(tenant.value(), batchId)) {
            String stateStr = (String) row[0];
            long count = ((Number) row[1]).longValue();
            ContainerState state = ContainerState.valueOf(stateStr);
            switch (state) {
                case EMPTY -> storedEmpty = count - inTransit; // approx — EMPTY includes in-transit
                case OPEN -> open = count;
                case SEALED -> sealed = count;
                case DESTROYED -> destroyed = count;
            }
        }
        if (storedEmpty < 0) storedEmpty = 0;
        return new BatchCounts(total, inTransit, storedEmpty, open, sealed, destroyed);
    }
}
