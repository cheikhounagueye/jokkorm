package com.jokkorm.application.loan;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.physical.PhysicalContainerNotFoundException;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.loan.PhysicalLoan;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RequestLoanUseCase {

    private final PhysicalLoanRepository loans;
    private final PhysicalContainerRepository containers;
    private final AuditChainService audit;
    private final Clock clock;
    private final com.jokkorm.application.record.LegalHoldGuard legalHolds;

    public RequestLoanUseCase(
        PhysicalLoanRepository loans,
        PhysicalContainerRepository containers,
        AuditChainService audit,
        Clock clock,
        com.jokkorm.application.record.LegalHoldGuard legalHolds) {
        this.legalHolds = legalHolds;
        this.loans = loans;
        this.containers = containers;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalLoan execute(
        UUID containerId, String borrowerName, String borrowerDept, String purpose,
        Instant expectedReturnAt, String requestedBy) {
        TenantId tenant = TenantContext.current();

        var container = containers.findById(tenant, containerId)
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "Container %s not found".formatted(containerId)));

        if (container.state() == com.jokkorm.domain.physical.ContainerState.DESTROYED) {
            throw new LoanConflictException(
                "Container %s is destroyed, cannot be loaned".formatted(container.barcode()));
        }

        legalHolds.requireNoHold(tenant, containerId, container.barcode());

        loans.findActiveByContainer(tenant, containerId).ifPresent(existing -> {
            throw new LoanConflictException(
                "Container %s already has an active loan (status=%s, borrower=%s)"
                    .formatted(container.barcode(), existing.status(), existing.borrowerName()));
        });

        PhysicalLoan loan = PhysicalLoan.request(
            tenant, containerId, borrowerName, borrowerDept, purpose,
            expectedReturnAt, requestedBy, clock.instant());
        PhysicalLoan saved = loans.save(loan);

        audit.record(
            AuditAction.LOAN_REQUESTED,
            AuditTargetType.LOAN,
            saved.id(),
            requestedBy,
            "container=%s borrower='%s' due=%s"
                .formatted(container.barcode(), borrowerName, expectedReturnAt));
        return saved;
    }
}
