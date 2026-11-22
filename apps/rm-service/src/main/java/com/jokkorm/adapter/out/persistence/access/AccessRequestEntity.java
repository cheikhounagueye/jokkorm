package com.jokkorm.adapter.out.persistence.access;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.access.AccessRequest;
import com.jokkorm.domain.access.AccessRequestStatus;
import com.jokkorm.domain.access.RequestType;
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
@Table(name = "access_request")
public class AccessRequestEntity {

    @Id @Column(name = "id", nullable = false) private UUID id;
    @Column(name = "tenant_id", length = 64, nullable = false) private String tenantId;
    @Enumerated(EnumType.STRING) @Column(name = "request_type", length = 32, nullable = false) private RequestType requestType;
    @Column(name = "container_id", nullable = false) private UUID containerId;
    @Enumerated(EnumType.STRING) @Column(name = "status", length = 32, nullable = false) private AccessRequestStatus status;
    @Column(name = "requester_id", length = 255, nullable = false) private String requesterId;
    @Column(name = "requester_name", length = 255, nullable = false) private String requesterName;
    @Column(name = "requester_dept", length = 255) private String requesterDept;
    @Column(name = "purpose", length = 500, nullable = false) private String purpose;
    @Column(name = "pieces_description", columnDefinition = "TEXT") private String piecesDescription;
    @Column(name = "expected_return_at") private Instant expectedReturnAt;
    @Column(name = "qualified_at") private Instant qualifiedAt;
    @Column(name = "qualified_by", length = 255) private String qualifiedBy;
    @Column(name = "qualification_notes", columnDefinition = "TEXT") private String qualificationNotes;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "approved_by", length = 255) private String approvedBy;
    @Column(name = "approval_notes", columnDefinition = "TEXT") private String approvalNotes;
    @Column(name = "fulfilled_at") private Instant fulfilledAt;
    @Column(name = "fulfilled_by", length = 255) private String fulfilledBy;
    @Column(name = "fulfillment_notes", columnDefinition = "TEXT") private String fulfillmentNotes;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "maarch_res_id", length = 128) private String maarchResId;
    @Column(name = "maarch_url", length = 1024) private String maarchUrl;
    @Column(name = "rejected_at") private Instant rejectedAt;
    @Column(name = "rejected_by", length = 255) private String rejectedBy;
    @Column(name = "reject_reason", length = 500) private String rejectReason;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "cancel_reason", length = 500) private String cancelReason;
    @Column(name = "physical_loan_id") private UUID physicalLoanId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(name = "version", nullable = false) private long version;

    protected AccessRequestEntity() {}

    public static AccessRequestEntity fromDomain(AccessRequest r) {
        AccessRequestEntity e = new AccessRequestEntity();
        e.id = r.id(); e.tenantId = r.tenantId().value();
        e.requestType = r.type(); e.containerId = r.containerId();
        e.status = r.status();
        e.requesterId = r.requesterId(); e.requesterName = r.requesterName();
        e.requesterDept = r.requesterDept().orElse(null);
        e.purpose = r.purpose();
        e.piecesDescription = r.piecesDescription().orElse(null);
        e.expectedReturnAt = r.expectedReturnAt().orElse(null);
        e.qualifiedAt = r.qualifiedAt().orElse(null);
        e.qualifiedBy = r.qualifiedBy().orElse(null);
        e.qualificationNotes = r.qualificationNotes().orElse(null);
        e.approvedAt = r.approvedAt().orElse(null);
        e.approvedBy = r.approvedBy().orElse(null);
        e.approvalNotes = r.approvalNotes().orElse(null);
        e.fulfilledAt = r.fulfilledAt().orElse(null);
        e.fulfilledBy = r.fulfilledBy().orElse(null);
        e.fulfillmentNotes = r.fulfillmentNotes().orElse(null);
        e.completedAt = r.completedAt().orElse(null);
        e.maarchResId = r.maarchResId().orElse(null);
        e.maarchUrl = r.maarchUrl().orElse(null);
        e.rejectedAt = r.rejectedAt().orElse(null);
        e.rejectedBy = r.rejectedBy().orElse(null);
        e.rejectReason = r.rejectReason().orElse(null);
        e.cancelledAt = r.cancelledAt().orElse(null);
        e.cancelReason = r.cancelReason().orElse(null);
        e.physicalLoanId = r.physicalLoanId().orElse(null);
        e.createdAt = r.createdAt(); e.updatedAt = r.updatedAt();
        return e;
    }

    public AccessRequest toDomain() {
        return AccessRequest.builder()
            .id(id).tenantId(TenantId.of(tenantId)).type(requestType).containerId(containerId)
            .requester(requesterId, requesterName, requesterDept)
            .purpose(purpose).piecesDescription(piecesDescription).expectedReturnAt(expectedReturnAt)
            .status(status)
            .qualifiedAt(qualifiedAt).qualifiedBy(qualifiedBy).qualificationNotes(qualificationNotes)
            .approvedAt(approvedAt).approvedBy(approvedBy).approvalNotes(approvalNotes)
            .fulfilledAt(fulfilledAt).fulfilledBy(fulfilledBy).fulfillmentNotes(fulfillmentNotes)
            .completedAt(completedAt)
            .maarchResId(maarchResId).maarchUrl(maarchUrl)
            .rejectedAt(rejectedAt).rejectedBy(rejectedBy).rejectReason(rejectReason)
            .cancelledAt(cancelledAt).cancelReason(cancelReason)
            .physicalLoanId(physicalLoanId)
            .createdAt(createdAt).updatedAt(updatedAt)
            .build();
    }

    public void copyMutableFrom(AccessRequest r) {
        this.status = r.status();
        this.qualifiedAt = r.qualifiedAt().orElse(null);
        this.qualifiedBy = r.qualifiedBy().orElse(null);
        this.qualificationNotes = r.qualificationNotes().orElse(null);
        this.approvedAt = r.approvedAt().orElse(null);
        this.approvedBy = r.approvedBy().orElse(null);
        this.approvalNotes = r.approvalNotes().orElse(null);
        this.fulfilledAt = r.fulfilledAt().orElse(null);
        this.fulfilledBy = r.fulfilledBy().orElse(null);
        this.fulfillmentNotes = r.fulfillmentNotes().orElse(null);
        this.completedAt = r.completedAt().orElse(null);
        this.maarchResId = r.maarchResId().orElse(null);
        this.maarchUrl = r.maarchUrl().orElse(null);
        this.rejectedAt = r.rejectedAt().orElse(null);
        this.rejectedBy = r.rejectedBy().orElse(null);
        this.rejectReason = r.rejectReason().orElse(null);
        this.cancelledAt = r.cancelledAt().orElse(null);
        this.cancelReason = r.cancelReason().orElse(null);
        this.physicalLoanId = r.physicalLoanId().orElse(null);
        this.updatedAt = r.updatedAt();
    }

    public String tenantId() { return tenantId; }
}
