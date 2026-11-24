package com.jokkorm.adapter.out.persistence.loan;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.loan.LoanStatus;
import com.jokkorm.domain.loan.PhysicalLoan;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "physical_loan")
public class PhysicalLoanEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", length = 64, nullable = false) private String tenantId;
    @Column(name = "container_id", nullable = false) private UUID containerId;
    @Column(name = "ghost_code", length = 16, nullable = false, updatable = false) private String ghostCode;
    @Column(name = "borrower_name", length = 255, nullable = false) private String borrowerName;
    @Column(name = "borrower_dept", length = 255) private String borrowerDept;
    @Column(name = "purpose", length = 500) private String purpose;
    @Enumerated(EnumType.STRING) @Column(name = "status", length = 32, nullable = false) private LoanStatus status;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "requested_by", length = 255, nullable = false) private String requestedBy;
    @Column(name = "expected_return_at", nullable = false) private Instant expectedReturnAt;
    @Column(name = "out_at") private Instant outAt;
    @Column(name = "out_by", length = 255) private String outBy;
    @Column(name = "returned_at") private Instant returnedAt;
    @Column(name = "returned_by", length = 255) private String returnedBy;
    @Column(name = "cancel_reason", length = 500) private String cancelReason;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private long version;

    protected PhysicalLoanEntity() {}

    public static PhysicalLoanEntity fromDomain(PhysicalLoan l) {
        PhysicalLoanEntity e = new PhysicalLoanEntity();
        e.id = l.id();
        e.tenantId = l.tenantId().value();
        e.containerId = l.containerId();
        e.ghostCode = l.ghostCode();
        e.borrowerName = l.borrowerName();
        e.borrowerDept = l.borrowerDept().orElse(null);
        e.purpose = l.purpose().orElse(null);
        e.status = l.status();
        e.requestedAt = l.requestedAt();
        e.requestedBy = l.requestedBy();
        e.expectedReturnAt = l.expectedReturnAt();
        e.outAt = l.outAt().orElse(null);
        e.outBy = l.outBy().orElse(null);
        e.returnedAt = l.returnedAt().orElse(null);
        e.returnedBy = l.returnedBy().orElse(null);
        e.cancelReason = l.cancelReason().orElse(null);
        e.updatedAt = l.updatedAt();
        return e;
    }

    public PhysicalLoan toDomain() {
        return PhysicalLoan.rehydrate(
            id, TenantId.of(tenantId), containerId, ghostCode,
            borrowerName, borrowerDept, purpose,
            status, requestedAt, requestedBy, expectedReturnAt,
            outAt, outBy, returnedAt, returnedBy, cancelReason, updatedAt);
    }

    public void copyMutableStateFrom(PhysicalLoan l) {
        this.status = l.status();
        this.outAt = l.outAt().orElse(null);
        this.outBy = l.outBy().orElse(null);
        this.returnedAt = l.returnedAt().orElse(null);
        this.returnedBy = l.returnedBy().orElse(null);
        this.cancelReason = l.cancelReason().orElse(null);
        this.updatedAt = l.updatedAt();
    }

    public String tenantId() { return tenantId; }
}
