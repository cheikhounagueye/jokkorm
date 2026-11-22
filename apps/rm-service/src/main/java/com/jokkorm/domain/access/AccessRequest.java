package com.jokkorm.domain.access;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Demande d'accès à une boîte physique — workflow 4-eyes pattern 07.
 *
 * <p>Transitions gardées par état. Chaque action (qualifier, approuver, rejeter,
 * exécuter, compléter) vérifie l'état courant et refuse si inapproprié.
 */
public final class AccessRequest {

    private final UUID id;
    private final TenantId tenantId;
    private final RequestType type;
    private final UUID containerId;

    private final String requesterId;
    private final String requesterName;
    private final String requesterDept;

    private final String purpose;
    private final String piecesDescription;
    private final Instant expectedReturnAt;

    private AccessRequestStatus status;

    private Instant qualifiedAt;
    private String qualifiedBy;
    private String qualificationNotes;

    private Instant approvedAt;
    private String approvedBy;
    private String approvalNotes;

    private Instant fulfilledAt;
    private String fulfilledBy;
    private String fulfillmentNotes;

    private Instant completedAt;

    private String maarchResId;
    private String maarchUrl;

    private Instant rejectedAt;
    private String rejectedBy;
    private String rejectReason;

    private Instant cancelledAt;
    private String cancelReason;

    private UUID physicalLoanId;

    private final Instant createdAt;
    private Instant updatedAt;

    private AccessRequest(Builder b) {
        this.id = Objects.requireNonNull(b.id, "id");
        this.tenantId = Objects.requireNonNull(b.tenantId, "tenantId");
        this.type = Objects.requireNonNull(b.type, "type");
        this.containerId = Objects.requireNonNull(b.containerId, "containerId");
        this.requesterId = requireNonBlank(b.requesterId, "requesterId");
        this.requesterName = requireNonBlank(b.requesterName, "requesterName");
        this.requesterDept = b.requesterDept;
        this.purpose = requireNonBlank(b.purpose, "purpose");
        this.piecesDescription = b.piecesDescription;
        this.expectedReturnAt = b.expectedReturnAt;
        if (type == RequestType.BORROW_BOX && expectedReturnAt == null) {
            throw new IllegalArgumentException("BORROW_BOX requires expectedReturnAt");
        }
        if ((type == RequestType.EXTRACT_PIECES || type == RequestType.DIGITIZE_EXTRACT)
            && (piecesDescription == null || piecesDescription.isBlank())) {
            throw new IllegalArgumentException(type + " requires piecesDescription");
        }
        this.status = b.status != null ? b.status : AccessRequestStatus.DRAFT;
        this.qualifiedAt = b.qualifiedAt;
        this.qualifiedBy = b.qualifiedBy;
        this.qualificationNotes = b.qualificationNotes;
        this.approvedAt = b.approvedAt;
        this.approvedBy = b.approvedBy;
        this.approvalNotes = b.approvalNotes;
        this.fulfilledAt = b.fulfilledAt;
        this.fulfilledBy = b.fulfilledBy;
        this.fulfillmentNotes = b.fulfillmentNotes;
        this.completedAt = b.completedAt;
        this.maarchResId = b.maarchResId;
        this.maarchUrl = b.maarchUrl;
        this.rejectedAt = b.rejectedAt;
        this.rejectedBy = b.rejectedBy;
        this.rejectReason = b.rejectReason;
        this.cancelledAt = b.cancelledAt;
        this.cancelReason = b.cancelReason;
        this.physicalLoanId = b.physicalLoanId;
        this.createdAt = Objects.requireNonNull(b.createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(b.updatedAt, "updatedAt");
    }

    public static AccessRequest createDraft(
        TenantId tenant, RequestType type, UUID containerId,
        String requesterId, String requesterName, String requesterDept,
        String purpose, String piecesDescription, Instant expectedReturnAt,
        Instant now) {
        return new Builder()
            .id(UUID.randomUUID()).tenantId(tenant).type(type).containerId(containerId)
            .requester(requesterId, requesterName, requesterDept)
            .purpose(purpose).piecesDescription(piecesDescription)
            .expectedReturnAt(expectedReturnAt)
            .status(AccessRequestStatus.DRAFT)
            .createdAt(now).updatedAt(now)
            .build();
    }

    public void submit(Instant at) {
        requireStatus(AccessRequestStatus.DRAFT, "submit");
        this.status = AccessRequestStatus.SUBMITTED;
        this.updatedAt = at;
    }

    public void qualify(String actor, String notes, Instant at) {
        requireStatus(AccessRequestStatus.SUBMITTED, "qualify");
        this.status = AccessRequestStatus.QUALIFIED;
        this.qualifiedAt = at;
        this.qualifiedBy = requireNonBlank(actor, "qualifiedBy");
        this.qualificationNotes = notes;
        this.updatedAt = at;
    }

    public void approve(String actor, String notes, Instant at, boolean allowSelfApproval) {
        requireStatus(AccessRequestStatus.QUALIFIED, "approve");
        if (!allowSelfApproval && actor != null && actor.equalsIgnoreCase(qualifiedBy)) {
            throw new IllegalStateException(
                "4-eyes: approver (%s) must differ from qualifier (%s)"
                    .formatted(actor, qualifiedBy));
        }
        this.status = AccessRequestStatus.APPROVED;
        this.approvedAt = at;
        this.approvedBy = requireNonBlank(actor, "approvedBy");
        this.approvalNotes = notes;
        this.updatedAt = at;
    }

    public void reject(String actor, String reason, Instant at) {
        if (status != AccessRequestStatus.SUBMITTED && status != AccessRequestStatus.QUALIFIED) {
            throw new IllegalStateException(
                "Only SUBMITTED or QUALIFIED can be rejected (current=" + status + ")");
        }
        this.status = AccessRequestStatus.REJECTED;
        this.rejectedAt = at;
        this.rejectedBy = requireNonBlank(actor, "rejectedBy");
        this.rejectReason = (reason == null || reason.isBlank()) ? "non précisé" : reason.trim();
        this.updatedAt = at;
    }

    public void cancel(String reason, Instant at) {
        if (status != AccessRequestStatus.DRAFT && status != AccessRequestStatus.SUBMITTED) {
            throw new IllegalStateException(
                "Only DRAFT or SUBMITTED can be cancelled (current=" + status + ")");
        }
        this.status = AccessRequestStatus.CANCELLED;
        this.cancelledAt = at;
        this.cancelReason = (reason == null || reason.isBlank()) ? "non précisé" : reason.trim();
        this.updatedAt = at;
    }

    /** Transition APPROVED → FULFILLED. Lien PhysicalLoan si BORROW_BOX, lien Maarch si DIGITIZE. */
    public void fulfill(String actor, String notes,
                        UUID physicalLoanId, String maarchResId, String maarchUrl, Instant at) {
        requireStatus(AccessRequestStatus.APPROVED, "fulfill");
        this.status = AccessRequestStatus.FULFILLED;
        this.fulfilledAt = at;
        this.fulfilledBy = requireNonBlank(actor, "fulfilledBy");
        this.fulfillmentNotes = notes;
        this.physicalLoanId = physicalLoanId;
        this.maarchResId = maarchResId;
        this.maarchUrl = maarchUrl;
        this.updatedAt = at;
    }

    public void complete(Instant at) {
        requireStatus(AccessRequestStatus.FULFILLED, "complete");
        this.status = AccessRequestStatus.COMPLETED;
        this.completedAt = at;
        this.updatedAt = at;
    }

    private void requireStatus(AccessRequestStatus expected, String action) {
        if (status != expected) {
            throw new IllegalStateException(
                "Cannot %s from %s (expected %s)".formatted(action, status, expected));
        }
    }

    private static String requireNonBlank(String s, String field) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(field + " required");
        return s.trim();
    }

    // ───── Getters
    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public RequestType type() { return type; }
    public UUID containerId() { return containerId; }
    public String requesterId() { return requesterId; }
    public String requesterName() { return requesterName; }
    public Optional<String> requesterDept() { return Optional.ofNullable(requesterDept); }
    public String purpose() { return purpose; }
    public Optional<String> piecesDescription() { return Optional.ofNullable(piecesDescription); }
    public Optional<Instant> expectedReturnAt() { return Optional.ofNullable(expectedReturnAt); }
    public AccessRequestStatus status() { return status; }
    public Optional<Instant> qualifiedAt() { return Optional.ofNullable(qualifiedAt); }
    public Optional<String> qualifiedBy() { return Optional.ofNullable(qualifiedBy); }
    public Optional<String> qualificationNotes() { return Optional.ofNullable(qualificationNotes); }
    public Optional<Instant> approvedAt() { return Optional.ofNullable(approvedAt); }
    public Optional<String> approvedBy() { return Optional.ofNullable(approvedBy); }
    public Optional<String> approvalNotes() { return Optional.ofNullable(approvalNotes); }
    public Optional<Instant> fulfilledAt() { return Optional.ofNullable(fulfilledAt); }
    public Optional<String> fulfilledBy() { return Optional.ofNullable(fulfilledBy); }
    public Optional<String> fulfillmentNotes() { return Optional.ofNullable(fulfillmentNotes); }
    public Optional<Instant> completedAt() { return Optional.ofNullable(completedAt); }
    public Optional<String> maarchResId() { return Optional.ofNullable(maarchResId); }
    public Optional<String> maarchUrl() { return Optional.ofNullable(maarchUrl); }
    public Optional<Instant> rejectedAt() { return Optional.ofNullable(rejectedAt); }
    public Optional<String> rejectedBy() { return Optional.ofNullable(rejectedBy); }
    public Optional<String> rejectReason() { return Optional.ofNullable(rejectReason); }
    public Optional<Instant> cancelledAt() { return Optional.ofNullable(cancelledAt); }
    public Optional<String> cancelReason() { return Optional.ofNullable(cancelReason); }
    public Optional<UUID> physicalLoanId() { return Optional.ofNullable(physicalLoanId); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private UUID id; private TenantId tenantId; private RequestType type; private UUID containerId;
        private String requesterId, requesterName, requesterDept;
        private String purpose, piecesDescription;
        private Instant expectedReturnAt;
        private AccessRequestStatus status;
        private Instant qualifiedAt; private String qualifiedBy, qualificationNotes;
        private Instant approvedAt; private String approvedBy, approvalNotes;
        private Instant fulfilledAt; private String fulfilledBy, fulfillmentNotes;
        private Instant completedAt;
        private String maarchResId, maarchUrl;
        private Instant rejectedAt; private String rejectedBy, rejectReason;
        private Instant cancelledAt; private String cancelReason;
        private UUID physicalLoanId;
        private Instant createdAt, updatedAt;

        public Builder id(UUID v) { this.id = v; return this; }
        public Builder tenantId(TenantId v) { this.tenantId = v; return this; }
        public Builder type(RequestType v) { this.type = v; return this; }
        public Builder containerId(UUID v) { this.containerId = v; return this; }
        public Builder requester(String id, String name, String dept) {
            this.requesterId = id; this.requesterName = name; this.requesterDept = dept; return this;
        }
        public Builder purpose(String v) { this.purpose = v; return this; }
        public Builder piecesDescription(String v) { this.piecesDescription = v; return this; }
        public Builder expectedReturnAt(Instant v) { this.expectedReturnAt = v; return this; }
        public Builder status(AccessRequestStatus v) { this.status = v; return this; }
        public Builder qualifiedAt(Instant v) { this.qualifiedAt = v; return this; }
        public Builder qualifiedBy(String v) { this.qualifiedBy = v; return this; }
        public Builder qualificationNotes(String v) { this.qualificationNotes = v; return this; }
        public Builder approvedAt(Instant v) { this.approvedAt = v; return this; }
        public Builder approvedBy(String v) { this.approvedBy = v; return this; }
        public Builder approvalNotes(String v) { this.approvalNotes = v; return this; }
        public Builder fulfilledAt(Instant v) { this.fulfilledAt = v; return this; }
        public Builder fulfilledBy(String v) { this.fulfilledBy = v; return this; }
        public Builder fulfillmentNotes(String v) { this.fulfillmentNotes = v; return this; }
        public Builder completedAt(Instant v) { this.completedAt = v; return this; }
        public Builder maarchResId(String v) { this.maarchResId = v; return this; }
        public Builder maarchUrl(String v) { this.maarchUrl = v; return this; }
        public Builder rejectedAt(Instant v) { this.rejectedAt = v; return this; }
        public Builder rejectedBy(String v) { this.rejectedBy = v; return this; }
        public Builder rejectReason(String v) { this.rejectReason = v; return this; }
        public Builder cancelledAt(Instant v) { this.cancelledAt = v; return this; }
        public Builder cancelReason(String v) { this.cancelReason = v; return this; }
        public Builder physicalLoanId(UUID v) { this.physicalLoanId = v; return this; }
        public Builder createdAt(Instant v) { this.createdAt = v; return this; }
        public Builder updatedAt(Instant v) { this.updatedAt = v; return this; }
        public AccessRequest build() { return new AccessRequest(this); }
    }
}
