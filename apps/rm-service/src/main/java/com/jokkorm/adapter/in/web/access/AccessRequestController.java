package com.jokkorm.adapter.in.web.access;

import com.jokkorm.application.access.AccessRequestWorkflow;
import com.jokkorm.application.access.QualificationCheck;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.domain.access.AccessRequestRepository;
import com.jokkorm.domain.access.RequestType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/access-requests")
@Tag(name = "Access requests", description = "Demandes utilisateurs workflow 4-eyes (pattern 07).")
public class AccessRequestController {

    private final AccessRequestWorkflow workflow;
    private final AccessRequestRepository requests;
    private final com.jokkorm.application.access.QualificationChecksService checksService;

    /** Résultat de qualification exposé en JSON (l'agrégat métier ne se sérialise pas). */
    public record QualifyResponse(AccessRequestResponse request, List<QualificationCheck> checks,
                                  boolean qualified, String blockingReason) {}

    /** Résultat de remise exposé en JSON. */
    public record FulfillResponse(AccessRequestResponse request, UUID createdLoanId) {}

    public AccessRequestController(AccessRequestWorkflow workflow, AccessRequestRepository requests,
                                   com.jokkorm.application.access.QualificationChecksService checksService) {
        this.checksService = checksService;
        this.workflow = workflow;
        this.requests = requests;
    }

    public record CreateRequest(
        @NotNull RequestType type,
        @NotNull UUID containerId,
        @NotBlank String requesterName,
        String requesterDept,
        @NotBlank String purpose,
        String piecesDescription,
        Instant expectedReturnAt) {}

    public record ReasonRequest(String reason) {}
    public record NotesRequest(String notes) {}
    public record FulfillRequest(String notes, /** base64 PDF for DIGITIZE_EXTRACT */ String digitizedPdfBase64) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Crée une demande DRAFT. Status initial DRAFT.")
    public AccessRequestResponse create(
        @Valid @RequestBody CreateRequest body,
        @RequestHeader(value = "X-Actor", defaultValue = "requester") String actor) {
        return AccessRequestResponse.from(workflow.createDraft(
            body.type(), body.containerId(),
            actor, body.requesterName(), body.requesterDept(),
            body.purpose(), body.piecesDescription(), body.expectedReturnAt()));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "DRAFT → SUBMITTED. Par le demandeur.")
    public AccessRequestResponse submit(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Actor", defaultValue = "requester") String actor) {
        return AccessRequestResponse.from(workflow.submit(id, actor));
    }

    @PostMapping("/{id}/qualify")
    @Operation(summary = "SUBMITTED → QUALIFIED. Archiviste. Checks auto, bloque si contrôle bloquant échoue.")
    public QualifyResponse qualify(
        @PathVariable UUID id,
        @RequestBody(required = false) NotesRequest body,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        var result = workflow.qualify(id, body == null ? null : body.notes(), actor);
        return new QualifyResponse(AccessRequestResponse.from(result.request()), result.checks(),
            result.qualified(), result.blockingReason());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "QUALIFIED → APPROVED. Responsable des archives. 4-eyes strict.")
    public AccessRequestResponse approve(
        @PathVariable UUID id,
        @RequestBody(required = false) NotesRequest body,
        @RequestHeader(value = "X-Actor", defaultValue = "responsable-archives") String actor) {
        return AccessRequestResponse.from(
            workflow.approve(id, body == null ? null : body.notes(), actor));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "SUBMITTED ou QUALIFIED → REJECTED. Archiviste ou responsable, motif obligatoire.")
    public AccessRequestResponse reject(
        @PathVariable UUID id,
        @RequestBody(required = false) ReasonRequest body,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return AccessRequestResponse.from(
            workflow.reject(id, body == null ? null : body.reason(), actor));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "DRAFT ou SUBMITTED → CANCELLED. Par le demandeur ou un admin.")
    public AccessRequestResponse cancel(
        @PathVariable UUID id,
        @RequestBody(required = false) ReasonRequest body,
        @RequestHeader(value = "X-Actor", defaultValue = "requester") String actor) {
        return AccessRequestResponse.from(
            workflow.cancel(id, body == null ? null : body.reason(), actor));
    }

    @PostMapping("/{id}/fulfill")
    @Operation(summary = "APPROVED → FULFILLED. Archiviste. Pour DIGITIZE : digitizedPdfBase64 obligatoire.")
    public FulfillResponse fulfill(
        @PathVariable UUID id,
        @RequestBody(required = false) FulfillRequest body,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        byte[] pdf = (body == null || body.digitizedPdfBase64() == null) ? null
            : Base64.getDecoder().decode(body.digitizedPdfBase64());
        var result = workflow.fulfill(id, body == null ? null : body.notes(), pdf, actor);
        return new FulfillResponse(AccessRequestResponse.from(result.request()), result.createdLoanId());
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "FULFILLED → COMPLETED. Archiviste.")
    public AccessRequestResponse complete(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return AccessRequestResponse.from(workflow.complete(id, actor));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Lecture d'une demande (filtrage visibilité par rôle).")
    public AccessRequestResponse get(@PathVariable UUID id,
        @RequestHeader(value = "X-Actor", defaultValue = "requester") String actor) {
        var r = requests.findById(TenantContext.current(), id)
            .orElseThrow(() -> new com.jokkorm.application.access.AccessRequestNotFoundException(
                "AccessRequest " + id + " not found"));
        // REQUESTER ne voit que les siennes
        if (!RoleContext.hasAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD, UserRole.AUDITOR)
            && !actor.equalsIgnoreCase(r.requesterId())) {
            throw new com.jokkorm.commons.role.ForbiddenRoleException(
                "You can only see your own requests");
        }
        return AccessRequestResponse.from(r);
    }

    @GetMapping("/mine")
    @Operation(summary = "Mes demandes (REQUESTER).")
    public List<AccessRequestResponse> mine(
        @RequestHeader(value = "X-Actor", defaultValue = "requester") String actor) {
        return requests.findByRequester(TenantContext.current(), actor).stream()
            .map(AccessRequestResponse::from).toList();
    }

    @GetMapping("/to-qualify")
    @Operation(summary = "File SUBMITTED à qualifier (ARCHIVIST).")
    public List<AccessRequestResponse> toQualify() {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        return requests.findSubmitted(TenantContext.current()).stream()
            .map(AccessRequestResponse::from).toList();
    }

    @GetMapping("/to-approve")
    @Operation(summary = "File QUALIFIED à approuver (ARCHIVES_HEAD).")
    public List<AccessRequestResponse> toApprove() {
        RoleContext.requireAny(UserRole.ARCHIVES_HEAD);
        return requests.findQualified(TenantContext.current()).stream()
            .map(AccessRequestResponse::from).toList();
    }

    @GetMapping("/to-fulfill")
    @Operation(summary = "File APPROVED à exécuter (ARCHIVIST).")
    public List<AccessRequestResponse> toFulfill() {
        RoleContext.requireAny(UserRole.ARCHIVIST);
        return requests.findApproved(TenantContext.current()).stream()
            .map(AccessRequestResponse::from).toList();
    }

    @GetMapping("/{id}/checks")
    @Operation(summary = "Lance les checks de qualification sans transitionner (preview).")
    public List<QualificationCheck> checks(@PathVariable UUID id) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        var r = requests.findById(TenantContext.current(), id)
            .orElseThrow(() -> new com.jokkorm.application.access.AccessRequestNotFoundException(
                "AccessRequest " + id + " not found"));
        return checksService.run(r);
    }
}
