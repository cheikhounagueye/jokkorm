package com.jokkorm.adapter.out.persistence.loan;

import com.jokkorm.domain.loan.LoanStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhysicalLoanJpaRepository extends JpaRepository<PhysicalLoanEntity, UUID> {

    Optional<PhysicalLoanEntity> findByIdAndTenantId(UUID id, String tenantId);

    Optional<PhysicalLoanEntity> findFirstByTenantIdAndContainerIdAndStatusIn(
        String tenantId, UUID containerId, List<LoanStatus> statuses);

    Page<PhysicalLoanEntity> findByTenantIdOrderByRequestedAtDesc(String tenantId, Pageable pageable);

    List<PhysicalLoanEntity> findByTenantIdAndStatusInOrderByExpectedReturnAtAsc(
        String tenantId, List<LoanStatus> statuses);
}
