package com.jokkorm.adapter.in.web.master;

import com.jokkorm.application.master.MasterImportService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.domain.master.MasterImportCandidate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Liste « à importer » et import automatique par profil (étape 2, règle n° 4). */
@RestController
@RequestMapping("/api/v1/master/imports")
@Tag(name = "Master", description = "GED/SAE maître du tenant (ADR-0003).")
public class MasterImportController {

    private final MasterImportService imports;

    public MasterImportController(MasterImportService imports) {
        this.imports = imports;
    }

    public record CandidateResponse(UUID id, String cote, String profileReference, String masterArchiveId,
                                    Instant depositedAt, String depositedBy, String status, boolean auto,
                                    String decidedBy, Instant decidedAt, String title, String masterPageUrl,
                                    boolean titleUnavailable) {
        static CandidateResponse from(MasterImportService.Item i) {
            return from(i.candidate(), i.title(), i.masterPageUrl(), i.titleUnavailable());
        }

        static CandidateResponse from(MasterImportCandidate c) {
            return from(c, null, null, false);
        }

        private static CandidateResponse from(MasterImportCandidate c, String title, String url, boolean unavailable) {
            return new CandidateResponse(c.id(), c.cote(), c.profileReference(), c.masterArchiveId(), c.depositedAt(),
                c.depositedBy(), c.status().name(), c.auto(), c.decidedBy(), c.decidedAt(), title, url, unavailable);
        }
    }

    public record AutoImportRequest(@NotNull Boolean autoImport) {
    }

    @GetMapping("/summary")
    @Operation(summary = "Compteurs de la liste « à importer » et règles d'import automatique par profil.",
        description = "available=false si la GED maître de l'espace ne publie pas de journal.")
    public MasterImportService.Summary summary() {
        return imports.summary(TenantContext.current());
    }

    @GetMapping
    @Operation(summary = "Archives déposées chez le maître, par état (archiviste).",
        description = "status=PENDING (à décider, titre lu en direct chez le maître), IMPORTED, IGNORED, UNREADABLE.")
    public List<CandidateResponse> list(@RequestParam(defaultValue = "PENDING") MasterImportCandidate.Status status,
                                        @RequestParam(defaultValue = "100") int limit) {
        return imports.list(status, limit).stream().map(CandidateResponse::from).toList();
    }

    @PostMapping("/{id}/import")
    @Operation(summary = "Importe une archive de la liste : la cote est revérifiée chez le maître. Audité.")
    public CandidateResponse importCandidate(@PathVariable UUID id,
                                             @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return CandidateResponse.from(imports.importCandidate(id, actor));
    }

    @PostMapping("/{id}/ignore")
    @Operation(summary = "« Pas de papier » : écarte l'archive de la liste (réversible). Audité.")
    public CandidateResponse ignore(@PathVariable UUID id,
                                    @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return CandidateResponse.from(imports.ignore(id, actor));
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "Remet dans la liste une archive écartée. Audité.")
    public CandidateResponse restore(@PathVariable UUID id,
                                     @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return CandidateResponse.from(imports.restore(id, actor));
    }

    @PutMapping("/profiles/{profile}")
    @Operation(summary = "Active ou retire l'import automatique d'un profil (responsable des archives). Audité.",
        description = "Ne vaut que pour les prochains dépôts ; les archives déjà en attente restent à décider.")
    public MasterImportService.ProfileView setAutoImport(
        @PathVariable String profile, @RequestBody @jakarta.validation.Valid AutoImportRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "responsable") String actor) {
        var rule = imports.setAutoImport(profile, request.autoImport(), actor);
        return new MasterImportService.ProfileView(rule.profileReference(), rule.autoImport(), rule.decidedBy(),
            rule.decidedAt(), 0);
    }

    @PostMapping("/profiles/{profile}/import-pending")
    @Operation(summary = "Importe d'un coup les archives en attente d'un profil (archiviste).")
    public MasterImportService.Bulk importPending(
        @PathVariable String profile,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return imports.importPendingOfProfile(profile, actor);
    }

    @PostMapping("/catch-up")
    @Operation(summary = "Relit les dépôts des N derniers jours dans le journal du maître (archiviste).",
        description = "Rejouable sans risque : un dépôt déjà traité n'est pas repris ; le curseur du veilleur ne bouge pas.")
    public MasterImportService.CatchUp catchUp(@RequestParam(defaultValue = "30") int days) {
        return imports.catchUp(days);
    }
}
