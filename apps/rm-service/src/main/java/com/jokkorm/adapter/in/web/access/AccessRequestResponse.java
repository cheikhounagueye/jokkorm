package com.jokkorm.adapter.in.web.access;

import com.jokkorm.domain.access.AccessRequest;
import com.jokkorm.domain.access.AccessRequestStatus;
import com.jokkorm.domain.access.RequestType;
import java.time.Instant;
import java.util.UUID;

public record AccessRequestResponse(
    UUID id,
    RequestType type,
    UUID containerId,
    AccessRequestStatus status,
    String requesterId,
    String requesterName,
    String requesterDept,
    String purpose,
    String piecesDescription,
    Instant expectedReturnAt,
    Instant qualifiedAt, String qualifiedBy, String qualificationNotes,
    Instant approvedAt, String approvedBy, String approvalNotes,
    Instant fulfilledAt, String fulfilledBy, String fulfillmentNotes,
    Instant completedAt,
    String maarchResId, String maarchUrl,
    Instant rejectedAt, String rejectedBy, String rejectReason,
    Instant cancelledAt, String cancelReason,
    UUID physicalLoanId,
    Instant createdAt, Instant updatedAt) {

    public static AccessRequestResponse from(AccessRequest r) {
        return new AccessRequestResponse(
            r.id(), r.type(), r.containerId(), r.status(),
            r.requesterId(), r.requesterName(), r.requesterDept().orElse(null),
            r.purpose(), r.piecesDescription().orElse(null), r.expectedReturnAt().orElse(null),
            r.qualifiedAt().orElse(null), r.qualifiedBy().orElse(null), r.qualificationNotes().orElse(null),
            r.approvedAt().orElse(null), r.approvedBy().orElse(null), r.approvalNotes().orElse(null),
            r.fulfilledAt().orElse(null), r.fulfilledBy().orElse(null), r.fulfillmentNotes().orElse(null),
            r.completedAt().orElse(null),
            r.maarchResId().orElse(null), r.maarchUrl().orElse(null),
            r.rejectedAt().orElse(null), r.rejectedBy().orElse(null), r.rejectReason().orElse(null),
            r.cancelledAt().orElse(null), r.cancelReason().orElse(null),
            r.physicalLoanId().orElse(null),
            r.createdAt(), r.updatedAt());
    }
}
