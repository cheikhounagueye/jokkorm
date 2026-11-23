package com.jokkorm.adapter.in.web.master;

import com.jokkorm.adapter.out.master.MasterAdapter;
import com.jokkorm.adapter.out.master.RoutingSaeMasterPort;
import com.jokkorm.application.record.ImportFromMasterUseCase;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.domain.record.Cote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Le maître du tenant courant : qui il est, et l'import d'une archive depuis lui. */
@RestController
@RequestMapping("/api/v1/master")
@Tag(name = "Master", description = "GED/SAE maître du tenant (ADR-0003).")
public class MasterController {

    private final RoutingSaeMasterPort routing;
    private final ImportFromMasterUseCase importFromMaster;
    private final com.jokkorm.application.master.MasterSyncService sync;

    public MasterController(RoutingSaeMasterPort routing, ImportFromMasterUseCase importFromMaster,
                            com.jokkorm.application.master.MasterSyncService sync) {
        this.sync = sync;
        this.routing = routing;
        this.importFromMaster = importFromMaster;
    }

    public record MasterInfo(String tenant, String kind, String displayName) {}

    @GetMapping("/info")
    @Operation(summary = "Maître qui sert le tenant courant (noop, demo, maarchrm…).")
    public MasterInfo info() {
        var tenant = TenantContext.current();
        MasterAdapter adapter = routing.adapterFor(tenant);
        return new MasterInfo(tenant.value(), adapter.kind(), adapter.displayName());
    }

    /** État de l'envoi de l'état physique vers le maître, pour une cote. */
    public record SyncStatus(String cote, String status, int attempts, java.time.Instant lastSentAt,
                             java.time.Instant lastAttemptAt, java.time.Instant nextAttemptAt, String lastError,
                             String lastPayload) {}

    @GetMapping("/sync")
    @Operation(summary = "Où en est l'affichage de l'état physique dans le maître pour cette cote (tranche 2a).",
        description = "204 si le maître de l'espace n'affiche pas l'état physique ou si rien n'a encore été envoyé.")
    public ResponseEntity<SyncStatus> syncStatus(@org.springframework.web.bind.annotation.RequestParam String cote) {
        return sync.status(Cote.of(cote.trim()))
            .map(s -> ResponseEntity.ok(new SyncStatus(s.cote(), s.status().name(), s.attempts(), s.lastSentAt(),
                s.lastAttemptAt(), s.nextAttemptAt(), s.lastError(), s.lastPayload())))
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    public record ImportRequest(@NotBlank String cote) {}

    public record ImportResponse(UUID recordId, String cote, String title, boolean created) {}

    @PostMapping("/import")
    @Operation(summary = "Fait entrer dans JokkoRM une archive connue du maître (archiviste).",
        description = "Vérifie la cote chez le maître puis crée l'enveloppe physique (idempotent, audité). "
            + "404 si la cote est inconnue du maître, 502 si le maître ne répond pas.")
    public ResponseEntity<ImportResponse> importArchive(
        @Valid @RequestBody ImportRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        var result = importFromMaster.execute(Cote.of(request.cote().trim()), actor);
        var body = new ImportResponse(result.record().id(), result.record().cote().value(),
            result.master().title(), result.created());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(body);
    }
}
