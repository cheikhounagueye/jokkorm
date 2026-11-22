package com.jokkorm.application.loan;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.loan.PhysicalLoan;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarkLoanOutUseCase {

    private final PhysicalLoanRepository loans;
    private final AuditChainService audit;
    private final Clock clock;

    public MarkLoanOutUseCase(PhysicalLoanRepository loans, AuditChainService audit, Clock clock) {
        this.loans = loans;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalLoan execute(UUID loanId, String actor) {
        TenantId tenant = TenantContext.current();
        PhysicalLoan loan = loans.findById(tenant, loanId)
            .orElseThrow(() -> new LoanNotFoundException("Loan %s not found".formatted(loanId)));
        loan.markOut(actor, clock.instant());
        PhysicalLoan saved = loans.save(loan);
        audit.record(AuditAction.LOAN_OUT, AuditTargetType.LOAN, saved.id(), actor,
            "container=%s borrower='%s'".formatted(loan.containerId(), loan.borrowerName()));
        return saved;
    }
}
