package com.jokkorm.adapter.in.web.master;

import com.jokkorm.application.master.MasterWatchService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.domain.master.MasterAlert;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Veilleur du journal du maître : son état et ses signalements (étape 1). */
@RestController
@RequestMapping("/api/v1/master")
@Tag(name = "Master", description = "GED/SAE maître du tenant (ADR-0003).")
public class MasterWatchController {

    private final MasterWatchService watcher;

    public MasterWatchController(MasterWatchService watcher) {
        this.watcher = watcher;
    }

    public record AlertResponse(UUID id, String kind, boolean requiresAction, String cote, Instant occurredAt,
                                String actor, String detail, Instant createdAt, Instant acknowledgedAt,
                                String acknowledgedBy) {
        static AlertResponse from(MasterAlert a) {
            return new AlertResponse(a.id(), a.kind().name(), a.kind().requiresAction(), a.cote(), a.occurredAt(),
                a.actor(), a.detail(), a.createdAt(), a.acknowledgedAt(), a.acknowledgedBy());
        }
    }

    @GetMapping("/watch")
    @Operation(summary = "État du veilleur du journal du maître pour l'espace courant.")
    public MasterWatchService.Status status() {
        return watcher.status(TenantContext.current());
    }

    @GetMapping("/alerts")
    @Operation(summary = "Signalements du veilleur, les plus récents d'abord.",
        description = "scope=open : seulement ceux qui demandent une action et ne sont pas traités ; scope=all : tous.")
    public List<AlertResponse> alerts(@RequestParam(defaultValue = "open") String scope,
                                      @RequestParam(defaultValue = "100") int limit) {
        return watcher.alerts(TenantContext.current(), !"all".equals(scope), limit).stream()
            .map(AlertResponse::from).toList();
    }

    @PostMapping("/alerts/{id}/acknowledge")
    @Operation(summary = "Marque un signalement comme traité (archiviste). Audité.",
        description = "204 si fait, 404 si inconnu ou déjà traité.")
    public ResponseEntity<Void> acknowledge(@PathVariable UUID id,
                                            @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        return watcher.acknowledge(TenantContext.current(), id, actor)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}
