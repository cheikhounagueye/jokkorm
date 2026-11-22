package com.jokkorm.application.loan;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.loan.PhysicalLoan;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListActiveLoansUseCase {

    private final PhysicalLoanRepository loans;
    private final Clock clock;

    public ListActiveLoansUseCase(PhysicalLoanRepository loans, Clock clock) {
        this.loans = loans;
        this.clock = clock;
    }

    /** Résultat enrichi avec le flag overdue calculé à la volée. */
    public record ActiveLoan(PhysicalLoan loan, boolean overdue) {}

    @Transactional(readOnly = true)
    public List<ActiveLoan> execute() {
        TenantId tenant = TenantContext.current();
        var now = clock.instant();
        return loans.findActive(tenant).stream()
            .map(l -> new ActiveLoan(l, l.isOverdue(now)))
            .toList();
    }
}
