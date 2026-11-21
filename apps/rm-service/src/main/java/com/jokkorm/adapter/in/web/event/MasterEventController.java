package com.jokkorm.adapter.in.web.event;

import com.jokkorm.adapter.in.web.record.RecordResponse;
import com.jokkorm.application.record.ApplyLegalHoldUseCase;
import com.jokkorm.application.record.HandleCoteAssignedEventUseCase;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.domain.record.Cote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hooks entrants depuis le maître (Maarch RM, Alfresco, Nuxeo, SI custom). Sécurité
 * HMAC signature arrive en phase 3 — en phase 2 les endpoints sont authentifiés OIDC
 * comme le reste de l'API.
 *
 * <p>Deux événements v1 :
 * <ul>
 *   <li>{@code POST /api/v1/events/master/cote-assigned} — provisionner une cote.</li>
 *   <li>{@code POST /api/v1/events/master/legal-hold} — gel / dégel contentieux.</li>
 * </ul>
 * Les deux endpoints sont idempotents : rejouer sans incident.
 */
@RestController
@RequestMapping("/api/v1/events/master")
@Tag(name = "Master events", description = "Hooks entrants depuis la GED/SAE maître.")
public class MasterEventController {

    private final HandleCoteAssignedEventUseCase handleCoteAssigned;
    private final ApplyLegalHoldUseCase applyLegalHold;

    public MasterEventController(
        HandleCoteAssignedEventUseCase handleCoteAssigned,
        ApplyLegalHoldUseCase applyLegalHold) {
        this.handleCoteAssigned = handleCoteAssigned;
        this.applyLegalHold = applyLegalHold;
    }

    @PostMapping("/cote-assigned")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Événement maître : nouvelle cote attribuée. Provisionne l'enveloppe physique.")
    public RecordResponse coteAssigned(@Valid @RequestBody CoteAssignedRequest request) {
        return RecordResponse.from(
            handleCoteAssigned.execute(TenantContext.current(), Cote.of(request.cote())));
    }

    @PostMapping("/legal-hold")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Événement maître : gel ou dégel contentieux sur une archive.")
    public RecordResponse legalHold(@Valid @RequestBody LegalHoldRequest request) {
        return RecordResponse.from(
            applyLegalHold.execute(
                TenantContext.current(), Cote.of(request.cote()), request.action()));
    }
}
