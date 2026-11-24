package com.jokkorm.domain.loan;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Prêt d'une boîte physique (phase 5E). Référence stable : le {@code containerId}
 * (une boîte peut être prêtée plusieurs fois dans sa vie, mais une seule fois à
 * la fois — l'invariant est garanti par l'application layer qui refuse une
 * nouvelle demande si un prêt actif existe).
 *
 * <p>Le fantôme (carton posé à la place de la boîte sortie) porte le {@link #ghostCode()} du
 * prêt, jamais le code-barres de la boîte : scanner un fantôme ne doit pas passer pour la boîte
 * (étude du 24 novembre 2026, D10). Le code est attribué à la demande et ne change jamais.
 */
public final class PhysicalLoan {

    public static final String GHOST_CODE_PREFIX = "PRT-";
    private static final java.util.regex.Pattern GHOST_CODE =
        com.jokkorm.domain.common.ShortCode.pattern(GHOST_CODE_PREFIX);

    private final UUID id;
    private final TenantId tenantId;
    private final UUID containerId;
    private final String ghostCode;
    private final String borrowerName;
    private final String borrowerDept;
    private final String purpose;
    private LoanStatus status;
    private final Instant requestedAt;
    private final String requestedBy;
    private final Instant expectedReturnAt;
    private Instant outAt;
    private String outBy;
    private Instant returnedAt;
    private String returnedBy;
    private String cancelReason;
    private Instant updatedAt;

    private PhysicalLoan(
        UUID id, TenantId tenantId, UUID containerId, String ghostCode,
        String borrowerName, String borrowerDept, String purpose,
        LoanStatus status, Instant requestedAt, String requestedBy,
        Instant expectedReturnAt,
        Instant outAt, String outBy,
        Instant returnedAt, String returnedBy,
        String cancelReason, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.containerId = Objects.requireNonNull(containerId, "containerId");
        if (ghostCode == null || !GHOST_CODE.matcher(ghostCode).matches()) {
            throw new IllegalArgumentException("ghost code '%s' invalid : expected PRT-XXXXXXXX".formatted(ghostCode));
        }
        this.ghostCode = ghostCode;
        if (borrowerName == null || borrowerName.isBlank()) {
            throw new IllegalArgumentException("borrowerName required");
        }
        this.borrowerName = borrowerName.trim();
        this.borrowerDept = borrowerDept == null ? null : borrowerDept.trim();
        this.purpose = purpose == null ? null : purpose.trim();
        this.status = Objects.requireNonNull(status, "status");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new IllegalArgumentException("requestedBy required");
        }
        this.requestedBy = requestedBy;
        this.expectedReturnAt = Objects.requireNonNull(expectedReturnAt, "expectedReturnAt");
        if (!expectedReturnAt.isAfter(requestedAt)) {
            throw new IllegalArgumentException("expectedReturnAt must be after requestedAt");
        }
        this.outAt = outAt;
        this.outBy = outBy;
        this.returnedAt = returnedAt;
        this.returnedBy = returnedBy;
        this.cancelReason = cancelReason;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static PhysicalLoan request(
        TenantId tenant, UUID containerId,
        String borrowerName, String borrowerDept, String purpose,
        Instant expectedReturnAt, String requestedBy, Instant at) {
        return new PhysicalLoan(
            UUID.randomUUID(), tenant, containerId,
            com.jokkorm.domain.common.ShortCode.random(GHOST_CODE_PREFIX),
            borrowerName, borrowerDept, purpose,
            LoanStatus.REQUESTED, at, requestedBy, expectedReturnAt,
            null, null, null, null, null, at);
    }

    public static PhysicalLoan rehydrate(
        UUID id, TenantId tenantId, UUID containerId, String ghostCode,
        String borrowerName, String borrowerDept, String purpose,
        LoanStatus status, Instant requestedAt, String requestedBy,
        Instant expectedReturnAt,
        Instant outAt, String outBy,
        Instant returnedAt, String returnedBy,
        String cancelReason, Instant updatedAt) {
        return new PhysicalLoan(id, tenantId, containerId, ghostCode,
            borrowerName, borrowerDept, purpose,
            status, requestedAt, requestedBy, expectedReturnAt,
            outAt, outBy, returnedAt, returnedBy, cancelReason, updatedAt);
    }

    /** Marque le prêt comme sorti (fantôme imprimé, boîte remise à l'emprunteur). */
    public void markOut(String actor, Instant at) {
        if (status != LoanStatus.REQUESTED) {
            throw new IllegalStateException(
                "Only REQUESTED loans can be marked OUT (current=" + status + ")");
        }
        this.status = LoanStatus.OUT;
        this.outAt = at;
        this.outBy = actor;
        this.updatedAt = at;
    }

    /** Marque le prêt comme rendu (boîte remise en place, fantôme retiré). */
    public void markReturned(String actor, Instant at) {
        if (!status.isActive()) {
            throw new IllegalStateException(
                "Only OUT/OVERDUE loans can be returned (current=" + status + ")");
        }
        this.status = LoanStatus.RETURNED;
        this.returnedAt = at;
        this.returnedBy = actor;
        this.updatedAt = at;
    }

    /** Annulation avant sortie (demande erronée, emprunteur se désiste). */
    public void cancel(String reason, Instant at) {
        if (status != LoanStatus.REQUESTED) {
            throw new IllegalStateException(
                "Only REQUESTED loans can be cancelled (current=" + status + ")");
        }
        this.status = LoanStatus.CANCELLED;
        this.cancelReason = (reason == null || reason.isBlank()) ? "non précisé" : reason.trim();
        this.updatedAt = at;
    }

    /** Transition virtuelle OUT→OVERDUE si expectedReturnAt dépassé (calculée à la lecture). */
    public boolean isOverdue(Instant now) {
        return status == LoanStatus.OUT && now.isAfter(expectedReturnAt);
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public UUID containerId() { return containerId; }
    /** Code imprimé sur le fantôme ({@code PRT-XXXXXXXX}), reconnu au scan. */
    public String ghostCode() { return ghostCode; }
    public String borrowerName() { return borrowerName; }
    public Optional<String> borrowerDept() { return Optional.ofNullable(borrowerDept); }
    public Optional<String> purpose() { return Optional.ofNullable(purpose); }
    public LoanStatus status() { return status; }
    public Instant requestedAt() { return requestedAt; }
    public String requestedBy() { return requestedBy; }
    public Instant expectedReturnAt() { return expectedReturnAt; }
    public Optional<Instant> outAt() { return Optional.ofNullable(outAt); }
    public Optional<String> outBy() { return Optional.ofNullable(outBy); }
    public Optional<Instant> returnedAt() { return Optional.ofNullable(returnedAt); }
    public Optional<String> returnedBy() { return Optional.ofNullable(returnedBy); }
    public Optional<String> cancelReason() { return Optional.ofNullable(cancelReason); }
    public Instant updatedAt() { return updatedAt; }
}
