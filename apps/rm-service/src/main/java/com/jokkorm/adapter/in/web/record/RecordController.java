package com.jokkorm.adapter.in.web.record;

import com.jokkorm.application.record.FindRecordByCoteUseCase;
import com.jokkorm.application.record.ListRecordsUseCase;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lecture des enveloppes physiques pour le tenant courant. Voir OpenAPI
 * {@code docs/api/openapi.yaml}.
 *
 * <p>Deux endpoints en v1 :
 * <ul>
 *   <li>{@code GET /api/v1/records} — liste paginée, filtre statut optionnel.</li>
 *   <li>{@code GET /api/v1/records/by-cote?cote=X} — détail par cote. Query param
 *       plutôt que path variable : la cote contient souvent {@code /} (ex.
 *       {@code DMG/MP/2024/047}), que Tomcat et Spring Security StrictHttpFirewall
 *       refusent par défaut en path. Query param règle le problème sans hack config.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/records")
@Tag(name = "Records", description = "Enveloppes physiques des archives (lecture).")
public class RecordController {

    private final ListRecordsUseCase listRecords;
    private final FindRecordByCoteUseCase findByCote;

    public RecordController(ListRecordsUseCase listRecords, FindRecordByCoteUseCase findByCote) {
        this.listRecords = listRecords;
        this.findByCote = findByCote;
    }

    @GetMapping
    @Operation(summary = "Liste paginée des enveloppes physiques pour le tenant courant.")
    public RecordsPageResponse list(
        @Parameter(description = "Numéro de page (0-based)", example = "0")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Taille de page [1, 200]", example = "20")
        @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Filtre statut optionnel")
        @RequestParam(required = false) PhysicalArchiveStatus status) {
        return RecordsPageResponse.from(
            listRecords.execute(TenantContext.current(), status, PageRequest.of(page, size)));
    }

    @GetMapping("/by-cote")
    @Operation(summary = "Détail d'une enveloppe physique par sa cote (émise par le maître).")
    public RecordResponse findByCote(
        @Parameter(description = "Cote émise par le maître (slashes OK en query param)",
                   example = "DMG/MP/2024/047")
        @RequestParam String cote) {
        return RecordResponse.from(findByCote.execute(TenantContext.current(), Cote.of(cote)));
    }
}
