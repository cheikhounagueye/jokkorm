package com.jokkorm.adapter.in.web.search;

import com.jokkorm.application.search.MasterSearchService;
import com.jokkorm.application.search.MasterSearchService.EnrichedResult;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.domain.master.ArchiveSearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recherche métier (pattern 08). Endpoint principal pour le formulaire REQUESTER.
 *
 * <p>Confidentialité : le {@code containerBarcode} et le {@code containerId} sont
 * retournés mais masqués côté REQUESTER (le frontend ne les affiche pas). Pour
 * ARCHIVIST / ARCHIVES_HEAD, ils sont visibles dans la fiche demande.
 */
@RestController
@RequestMapping("/api/v1/master-search")
@Tag(name = "Master search", description = "Recherche métier des archives chez le maître (Maarch).")
public class MasterSearchController {

    private final MasterSearchService service;

    public MasterSearchController(MasterSearchService service) {
        this.service = service;
    }

    public record SearchRequest(
        String freeText,
        String service,
        String typologie,
        String classificationPath,
        LocalDate dateFrom,
        LocalDate dateTo,
        String cote,
        Integer limit) {}

    public record SearchResponseItem(
        String cote,
        String title,
        String service,
        String typologie,
        String classificationPath,
        LocalDate dateStart,
        LocalDate dateEnd,
        Integer duaRemainingYears,
        Boolean communicable,
        boolean availableInPhysical,
        // Côté REQUESTER, ces champs doivent être ignorés par l'UI ; côté ARCHIVIST, ils servent
        UUID containerId,
        String containerBarcode,
        String containerLabel) {

        static SearchResponseItem from(EnrichedResult e, boolean maskPhysical) {
            var m = e.master();
            return new SearchResponseItem(
                m.cote(), m.title(), m.service(), m.typologie(), m.classificationPath(),
                m.dateStart(), m.dateEnd(), m.duaRemainingYears(), m.communicable(),
                e.availableInPhysical(),
                maskPhysical ? null : e.containerId(),
                maskPhysical ? null : e.containerBarcode(),
                maskPhysical ? null : e.containerLabel());
        }
    }

    @PostMapping
    @Operation(summary = "Recherche métier (pattern 08). Au moins un critère obligatoire.")
    public List<SearchResponseItem> search(@RequestBody SearchRequest body) {
        var criteria = new ArchiveSearchCriteria(
            body.freeText(), body.service(), body.typologie(), body.classificationPath(),
            body.dateFrom(), body.dateTo(), body.cote(),
            body.limit() == null ? 20 : body.limit());
        boolean maskPhysical = !RoleContext.hasAny(
            UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD, UserRole.AUDITOR);
        return service.search(criteria).stream()
            .map(e -> SearchResponseItem.from(e, maskPhysical))
            .toList();
    }
}
