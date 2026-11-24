package com.jokkorm.adapter.out.persistence.loan;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.loan.LoanStatus;
import com.jokkorm.domain.loan.PhysicalLoan;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class JpaPhysicalLoanRepository implements PhysicalLoanRepository {

    private static final List<LoanStatus> ACTIVE =
        List.of(LoanStatus.REQUESTED, LoanStatus.OUT, LoanStatus.OVERDUE);
    private static final List<LoanStatus> OUT_OR_OVERDUE =
        List.of(LoanStatus.OUT, LoanStatus.OVERDUE);

    private final PhysicalLoanJpaRepository jpa;

    public JpaPhysicalLoanRepository(PhysicalLoanJpaRepository jpa) { this.jpa = jpa; }

    @Override
    public PhysicalLoan save(PhysicalLoan loan) {
        PhysicalLoanEntity existing = jpa.findById(loan.id()).orElse(null);
        PhysicalLoanEntity saved;
        if (existing == null) {
            saved = jpa.save(PhysicalLoanEntity.fromDomain(loan));
        } else {
            if (!existing.tenantId().equals(loan.tenantId().value())) {
                throw new IllegalStateException("Tenant mismatch on loan save");
            }
            existing.copyMutableStateFrom(loan);
            saved = jpa.save(existing);
        }
        return saved.toDomain();
    }

    @Override
    public Optional<PhysicalLoan> findById(TenantId tenant, UUID id) {
        return jpa.findByIdAndTenantId(id, tenant.value()).map(PhysicalLoanEntity::toDomain);
    }

    @Override
    public Optional<PhysicalLoan> findByGhostCode(TenantId tenant, String ghostCode) {
        return jpa.findByTenantIdAndGhostCode(tenant.value(), ghostCode).map(PhysicalLoanEntity::toDomain);
    }

    @Override
    public Optional<PhysicalLoan> findActiveByContainer(TenantId tenant, UUID containerId) {
        return jpa.findFirstByTenantIdAndContainerIdAndStatusIn(tenant.value(), containerId, ACTIVE)
            .map(PhysicalLoanEntity::toDomain);
    }

    @Override
    public Page<PhysicalLoan> findAll(TenantId tenant, PageRequest pageRequest) {
        var page = jpa.findByTenantIdOrderByRequestedAtDesc(
            tenant.value(),
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size()));
        List<PhysicalLoan> content = page.getContent().stream()
            .map(PhysicalLoanEntity::toDomain).toList();
        return new Page<>(content, pageRequest.page(), pageRequest.size(), page.getTotalElements());
    }

    @Override
    public List<PhysicalLoan> findActive(TenantId tenant) {
        return jpa.findByTenantIdAndStatusInOrderByExpectedReturnAtAsc(tenant.value(), OUT_OR_OVERDUE)
            .stream().map(PhysicalLoanEntity::toDomain).toList();
    }
}
