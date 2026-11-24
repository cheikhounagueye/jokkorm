package com.jokkorm.adapter.in.web.loan;

import com.jokkorm.domain.loan.LoanStatus;
import com.jokkorm.domain.loan.PhysicalLoan;
import java.time.Instant;
import java.util.UUID;

public record LoanResponse(
    UUID id,
    UUID containerId,
    /** Code imprimé sur le fantôme ({@code PRT-…}), reconnu par le mode magasin. */
    String ghostCode,
    String borrowerName,
    String borrowerDept,
    String purpose,
    LoanStatus status,
    boolean overdue,
    Instant requestedAt,
    String requestedBy,
    Instant expectedReturnAt,
    Instant outAt,
    String outBy,
    Instant returnedAt,
    String returnedBy,
    String cancelReason) {

    public static LoanResponse from(PhysicalLoan l) {
        return from(l, false);
    }

    public static LoanResponse from(PhysicalLoan l, boolean overdue) {
        return new LoanResponse(
            l.id(), l.containerId(), l.ghostCode(), l.borrowerName(),
            l.borrowerDept().orElse(null), l.purpose().orElse(null),
            l.status(), overdue,
            l.requestedAt(), l.requestedBy(), l.expectedReturnAt(),
            l.outAt().orElse(null), l.outBy().orElse(null),
            l.returnedAt().orElse(null), l.returnedBy().orElse(null),
            l.cancelReason().orElse(null));
    }
}
