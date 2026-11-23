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
public class MarkLoanReturnedUseCase {

    private final PhysicalLoanRepository loans;
    private final AuditChainService audit;
    private final Clock clock;
    private final com.jokkorm.application.master.MasterSyncService masterSync;

    public MarkLoanReturnedUseCase(PhysicalLoanRepository loans, AuditChainService audit, Clock clock,
        com.jokkorm.application.master.MasterSyncService masterSync) {
        this.masterSync = masterSync;
        this.loans = loans;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public PhysicalLoan execute(UUID loanId, String actor) {
        TenantId tenant = TenantContext.current();
        PhysicalLoan loan = loans.findById(tenant, loanId)
            .orElseThrow(() -> new LoanNotFoundException("Loan %s not found".formatted(loanId)));
        loan.markReturned(actor, clock.instant());
        PhysicalLoan saved = loans.save(loan);
        masterSync.touchContainer(tenant, loan.containerId());
        audit.record(AuditAction.LOAN_RETURNED, AuditTargetType.LOAN, saved.id(), actor,
            "container=%s".formatted(loan.containerId()));
        return saved;
    }
}
