package com.jokkorm.adapter.in.web.common;

import com.jokkorm.application.AccessGuard;
import com.jokkorm.application.access.AccessRequestNotFoundException;
import com.jokkorm.application.loan.LoanConflictException;
import com.jokkorm.application.loan.LoanNotFoundException;
import com.jokkorm.application.physical.BatchNotFoundException;
import com.jokkorm.commons.role.ForbiddenRoleException;
import com.jokkorm.application.physical.PhysicalContainerConflictException;
import com.jokkorm.application.physical.PhysicalContainerNotFoundException;
import com.jokkorm.application.physical.StorageLocationConflictException;
import com.jokkorm.application.physical.StorageLocationInUseException;
import com.jokkorm.application.physical.StorageLocationNotFoundException;
import com.jokkorm.application.record.RecordNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mappe les exceptions métier vers {@code application/problem+json} (RFC 7807).
 *
 * <p>Famille d'URIs de type : {@code https://jokkorm.io/errors/<slug>}. Chaque slug est
 * documenté dans {@code docs/api/errors.md} (à créer en phase 3).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE = "https://jokkorm.io/errors/";
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<ProblemDetail> notFound(RecordNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "record-not-found", e.getMessage(), request);
    }

    @ExceptionHandler(StorageLocationNotFoundException.class)
    public ResponseEntity<ProblemDetail> storageLocationNotFound(
        StorageLocationNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "storage-location-not-found", e.getMessage(), request);
    }

    @ExceptionHandler(StorageLocationConflictException.class)
    public ResponseEntity<ProblemDetail> storageLocationConflict(
        StorageLocationConflictException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "storage-location-conflict", e.getMessage(), request);
    }

    @ExceptionHandler(StorageLocationInUseException.class)
    public ResponseEntity<ProblemDetail> storageLocationInUse(
        StorageLocationInUseException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "storage-location-in-use", e.getMessage(), request);
    }

    @ExceptionHandler(PhysicalContainerNotFoundException.class)
    public ResponseEntity<ProblemDetail> containerNotFound(
        PhysicalContainerNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "container-not-found", e.getMessage(), request);
    }

    @ExceptionHandler(PhysicalContainerConflictException.class)
    public ResponseEntity<ProblemDetail> containerConflict(
        PhysicalContainerConflictException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "container-conflict", e.getMessage(), request);
    }

    @ExceptionHandler(com.jokkorm.application.physical.RoomEquipmentNotFoundException.class)
    public ResponseEntity<ProblemDetail> roomEquipmentNotFound(
        com.jokkorm.application.physical.RoomEquipmentNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "room-equipment-not-found", e.getMessage(), request);
    }

    @ExceptionHandler(BatchNotFoundException.class)
    public ResponseEntity<ProblemDetail> batchNotFound(
        BatchNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "batch-not-found", e.getMessage(), request);
    }

    @ExceptionHandler(LoanNotFoundException.class)
    public ResponseEntity<ProblemDetail> loanNotFound(
        LoanNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "loan-not-found", e.getMessage(), request);
    }

    @ExceptionHandler(LoanConflictException.class)
    public ResponseEntity<ProblemDetail> loanConflict(
        LoanConflictException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "loan-conflict", e.getMessage(), request);
    }

    @ExceptionHandler(AccessRequestNotFoundException.class)
    public ResponseEntity<ProblemDetail> accessRequestNotFound(
        AccessRequestNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "access-request-not-found", e.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenRoleException.class)
    public ResponseEntity<ProblemDetail> forbiddenRole(
        ForbiddenRoleException e, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "forbidden-role", e.getMessage(), request);
    }

    @ExceptionHandler(AccessGuard.TenantMismatchException.class)
    public ResponseEntity<ProblemDetail> tenantMismatch(
        AccessGuard.TenantMismatchException e, HttpServletRequest request) {
        LOG.warn("Cross-tenant access blocked: {}", e.getMessage());
        return problem(HttpStatus.FORBIDDEN, "tenant-mismatch",
            "Cross-tenant access denied", request);
    }

    @ExceptionHandler(AccessGuard.AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accessDenied(
        AccessGuard.AccessDeniedException e, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, "access-denied", e.getMessage(), request);
    }

    @ExceptionHandler(com.jokkorm.application.record.LegalHoldException.class)
    public ResponseEntity<ProblemDetail> legalHold(
        com.jokkorm.application.record.LegalHoldException e, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, "legal-hold", e.getMessage(), request);
    }

    @ExceptionHandler(com.jokkorm.domain.master.MasterUnreachableException.class)
    public ResponseEntity<ProblemDetail> masterUnreachable(
        com.jokkorm.domain.master.MasterUnreachableException e, HttpServletRequest request) {
        // Le maître (Maarch RM…) n'a pas répondu de façon exploitable : 502, jamais « inconnue ».
        return problem(HttpStatus.BAD_GATEWAY, "master-unreachable", e.getMessage(), request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> illegalState(IllegalStateException e, HttpServletRequest request) {
        // Les transitions de statut invalides (destruction d'un FROZEN, …) remontent ici
        return problem(HttpStatus.CONFLICT, "illegal-state-transition", e.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> illegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-argument", e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(
        MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = e.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                f -> f.getField(),
                f -> f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage(),
                (a, b) -> a));
        ProblemDetail pd = baseProblem(HttpStatus.BAD_REQUEST, "validation-failed",
            "Request body validation failed", request);
        pd.setProperty("fieldErrors", fieldErrors);
        return response(pd);
    }

    private ResponseEntity<ProblemDetail> problem(
        HttpStatus status, String slug, String detail, HttpServletRequest request) {
        return response(baseProblem(status, slug, detail, request));
    }

    private ProblemDetail baseProblem(
        HttpStatus status, String slug, String detail, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(ERROR_BASE + slug));
        pd.setTitle(toTitle(slug));
        pd.setInstance(URI.create(request.getRequestURI()));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    private ResponseEntity<ProblemDetail> response(ProblemDetail pd) {
        return ResponseEntity.status(pd.getStatus()).contentType(PROBLEM_JSON).body(pd);
    }

    private String toTitle(String slug) {
        String[] parts = slug.split("-");
        StringBuilder sb = new StringBuilder(parts[0].substring(0, 1).toUpperCase())
            .append(parts[0].substring(1));
        for (int i = 1; i < parts.length; i++) {
            sb.append(' ').append(parts[i]);
        }
        return sb.toString();
    }
}
