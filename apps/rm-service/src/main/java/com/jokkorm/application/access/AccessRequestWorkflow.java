package com.jokkorm.application.access;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.application.loan.MarkLoanOutUseCase;
import com.jokkorm.application.loan.RequestLoanUseCase;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.access.AccessRequest;
import com.jokkorm.domain.access.AccessRequestRepository;
import com.jokkorm.domain.access.AccessRequestStatus;
import com.jokkorm.domain.access.MasterContentLink;
import com.jokkorm.domain.access.RequestType;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.loan.PhysicalLoan;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.application.physical.PhysicalContainerNotFoundException;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestration du workflow access_request (phase 5P). Regroupe les transitions
 * en un seul service pour cohérence transactionnelle et audit chaîné.
 */
@Service
public class AccessRequestWorkflow {

    private static final Logger LOG = LoggerFactory.getLogger(AccessRequestWorkflow.class);

    private final AccessRequestRepository requests;
    private final PhysicalContainerRepository containers;
    private final QualificationChecksService checksService;
    private final RequestLoanUseCase requestLoan;
    private final MarkLoanOutUseCase markLoanOut;
    private final SaeMasterPort master;
    private final AuditChainService audit;
    private final Clock clock;
    private final boolean allowSelfApproval;
    private final com.jokkorm.application.record.LegalHoldGuard legalHolds;

    public AccessRequestWorkflow(
        AccessRequestRepository requests,
        PhysicalContainerRepository containers,
        QualificationChecksService checksService,
        RequestLoanUseCase requestLoan,
        MarkLoanOutUseCase markLoanOut,
        SaeMasterPort master,
        AuditChainService audit,
        Clock clock,
        @Value("${jokkorm.access.allow-self-approval:false}") boolean allowSelfApproval,
        com.jokkorm.application.record.LegalHoldGuard legalHolds) {
        this.legalHolds = legalHolds;
        this.requests = requests;
        this.containers = containers;
        this.checksService = checksService;
        this.requestLoan = requestLoan;
        this.markLoanOut = markLoanOut;
        this.master = master;
        this.audit = audit;
        this.clock = clock;
        this.allowSelfApproval = allowSelfApproval;
    }

    @Transactional
    public AccessRequest createDraft(RequestType type, UUID containerId,
                                      String requesterId, String requesterName, String requesterDept,
                                      String purpose, String piecesDescription, Instant expectedReturnAt) {
        RoleContext.requireAny(UserRole.REQUESTER, UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        containers.findById(tenant, containerId).orElseThrow(() ->
            new PhysicalContainerNotFoundException("Container " + containerId + " not found"));

        AccessRequest r = AccessRequest.createDraft(
            tenant, type, containerId, requesterId, requesterName, requesterDept,
            purpose, piecesDescription, expectedReturnAt, clock.instant());
        AccessRequest saved = requests.save(r);
        auditEvent(saved, AuditAction.ACCESS_REQUEST_CREATED, requesterId,
            "type=%s container=%s".formatted(type, containerId));
        return saved;
    }

    @Transactional
    public AccessRequest submit(UUID id, String actor) {
        AccessRequest r = loadOrThrow(id);
        requireRequesterOrArchivist(r, actor);
        r.submit(clock.instant());
        AccessRequest saved = requests.save(r);
        auditEvent(saved, AuditAction.ACCESS_REQUEST_SUBMITTED, actor, "");
        return saved;
    }

    @Transactional
    public QualifyResult qualify(UUID id, String notes, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        AccessRequest r = loadOrThrow(id);
        List<QualificationCheck> checks = checksService.run(r);
        boolean blocked = checks.stream().anyMatch(QualificationCheck::isBlocking);
        if (blocked) {
            return new QualifyResult(r, checks, false, "Au moins un contrôle bloquant a échoué");
        }
        r.qualify(actor, notes, clock.instant());
        AccessRequest saved = requests.save(r);
        auditEvent(saved, AuditAction.ACCESS_REQUEST_QUALIFIED, actor, notes == null ? "" : notes);
        return new QualifyResult(saved, checks, true, null);
    }

    @Transactional
    public AccessRequest approve(UUID id, String notes, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVES_HEAD);
        AccessRequest r = loadOrThrow(id);
        r.approve(actor, notes, clock.instant(), allowSelfApproval);
        AccessRequest saved = requests.save(r);
        auditEvent(saved, AuditAction.ACCESS_REQUEST_APPROVED, actor, notes == null ? "" : notes);
        return saved;
    }

    @Transactional
    public AccessRequest reject(UUID id, String reason, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        AccessRequest r = loadOrThrow(id);
        r.reject(actor, reason, clock.instant());
        AccessRequest saved = requests.save(r);
        auditEvent(saved, AuditAction.ACCESS_REQUEST_REJECTED, actor,
            "reason=" + (reason == null ? "" : reason));
        return saved;
    }

    @Transactional
    public AccessRequest cancel(UUID id, String reason, String actor) {
        AccessRequest r = loadOrThrow(id);
        if (!actor.equalsIgnoreCase(r.requesterId())
            && !RoleContext.hasAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD)) {
            throw new com.jokkorm.commons.role.ForbiddenRoleException(
                "Only requester or admin can cancel");
        }
        r.cancel(reason, clock.instant());
        AccessRequest saved = requests.save(r);
        auditEvent(saved, AuditAction.ACCESS_REQUEST_CANCELLED, actor,
            "reason=" + (reason == null ? "" : reason));
        return saved;
    }

    /**
     * APPROVED → FULFILLED.
     *
     * @param masterReference pour une numérisation seulement : où retrouver le scan chez le maître
     *                        (pattern 07 — JokkoRM ne garde jamais le fichier ; l'archiviste le
     *                        dépose dans le maître puis saisit sa référence)
     * @param masterUrl       lien facultatif vers ce scan dans l'interface du maître
     */
    @Transactional
    public FulfillResult fulfill(UUID id, String notes, String masterReference, String masterUrl, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST);
        AccessRequest r = loadOrThrow(id);
        boolean linkGiven = (masterReference != null && !masterReference.isBlank())
            || (masterUrl != null && !masterUrl.isBlank());
        if (linkGiven && r.type() != RequestType.DIGITIZE_EXTRACT) {
            throw new IllegalArgumentException(
                "Une référence Maarch RM ne s'indique que pour une demande de numérisation.");
        }
        var container = containers.findById(TenantContext.current(), r.containerId())
            .orElseThrow(() -> new PhysicalContainerNotFoundException(
                "Container " + r.containerId() + " not found"));

        UUID loanId = null;
        String maarchResId = null, maarchUrl = null;

        switch (r.type()) {
            case BORROW_BOX -> {
                // Crée un PhysicalLoan + marque OUT immédiatement (fantôme généré à part)
                PhysicalLoan loan = requestLoan.execute(
                    container.id(), r.requesterName(), r.requesterDept().orElse(null),
                    r.purpose(), r.expectedReturnAt().orElseThrow(), actor);
                markLoanOut.execute(loan.id(), actor);
                loanId = loan.id();
            }
            case EXTRACT_PIECES -> {
                // Pas de mouvement de boîte, mais des pièces sortent : gel revérifié au moment de la remise.
                legalHolds.requireNoHold(TenantContext.current(), container.id(), container.barcode());
            }
            case DIGITIZE_EXTRACT -> {
                // Le scan vit chez le maître (accès, conservation, élimination) : JokkoRM n'en
                // garde que la référence, et un lien seulement s'il pointe vers le maître.
                MasterContentLink link = MasterContentLink.of(masterReference, masterUrl,
                    master.contentLinkOrigin(TenantContext.current()).orElse(null));
                maarchResId = link.reference();
                maarchUrl = link.url();
            }
        }

        r.fulfill(actor, notes, loanId, maarchResId, maarchUrl, clock.instant());
        AccessRequest saved = requests.save(r);
        auditEvent(saved, AuditAction.ACCESS_REQUEST_FULFILLED, actor,
            "type=%s loan=%s maarch=%s".formatted(r.type(), loanId, maarchResId));
        return new FulfillResult(saved, loanId);
    }

    @Transactional
    public AccessRequest complete(UUID id, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST);
        AccessRequest r = loadOrThrow(id);
        r.complete(clock.instant());
        AccessRequest saved = requests.save(r);
        auditEvent(saved, AuditAction.ACCESS_REQUEST_COMPLETED, actor, "");
        return saved;
    }

    private AccessRequest loadOrThrow(UUID id) {
        return requests.findById(TenantContext.current(), id).orElseThrow(() ->
            new AccessRequestNotFoundException("AccessRequest " + id + " not found"));
    }

    private void requireRequesterOrArchivist(AccessRequest r, String actor) {
        if (actor.equalsIgnoreCase(r.requesterId())) return;
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
    }

    private void auditEvent(AccessRequest r, AuditAction action, String actor, String context) {
        audit.record(action, AuditTargetType.ACCESS_REQUEST, r.id(), actor, context);
    }

    public record QualifyResult(AccessRequest request, List<QualificationCheck> checks,
                                 boolean qualified, String blockingReason) {}

    public record FulfillResult(AccessRequest request, UUID createdLoanId) {}
}
