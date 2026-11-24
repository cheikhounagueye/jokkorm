package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.adapter.in.web.record.RecordResponse;
import com.jokkorm.application.record.ConditionRecordsUseCase;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conditionnement (étude du 24 novembre 2026, lot 1) : la liste des dossiers « À conditionner »
 * et leur mise en boîte, plusieurs à la fois, dans une boîte rangée ou encore en zone tampon.
 */
@RestController
@RequestMapping("/api/v1/physical/conditioning")
@Tag(name = "Conditionnement", description = "Dossiers à mettre en boîte et mise en boîte groupée.")
public class ConditioningController {

    private final ConditionRecordsUseCase condition;

    public ConditioningController(ConditionRecordsUseCase condition) {
        this.condition = condition;
    }

    public record PendingPage(List<RecordResponse> items, long total, int page, int size) {}

    public record ConditioningRequest(@NotBlank String containerBarcode, @NotEmpty List<String> cotes) {}

    public record ConditioningResponse(
        PhysicalContainerResponse container, long recordCount, List<RecordResponse> stored) {}

    @GetMapping("/pending")
    @Operation(
        summary = "Dossiers à conditionner : dans aucune boîte, enregistrés ou gelés, les plus anciens d'abord.",
        description = "Un dossier gelé y figure (son papier attend quelque part) mais ne peut pas être mis en "
            + "boîte avant la levée du gel. `total` sert de compteur au menu.")
    public PendingPage pending(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD, UserRole.AUDITOR);
        Page<PhysicalArchiveRecord> p = condition.pending(
            PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200))));
        return new PendingPage(p.content().stream().map(RecordResponse::from).toList(), p.totalElements(),
            p.page(), p.size());
    }

    @PostMapping
    @Operation(
        summary = "Mettre des dossiers dans une boîte, d'un geste (tout ou rien).",
        description = "La boîte peut être rangée ou en zone tampon. Au moindre refus rien n'est écrit : 409 "
            + "`conditioning-refused` avec `refusals` (cote, raison, message ; cote vide = la boîte : fermée, "
            + "en prêt, trop pleine). Une entrée d'audit RECORD_STORED par dossier.")
    public ConditioningResponse conditionRecords(
        @Valid @RequestBody ConditioningRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        ConditionRecordsUseCase.Result r = condition.execute(request.containerBarcode(), request.cotes(), actor);
        return new ConditioningResponse(PhysicalContainerResponse.from(r.container()), r.recordCount(),
            r.stored().stream().map(RecordResponse::from).toList());
    }
}
