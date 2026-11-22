package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.adapter.in.web.record.RecordsPageResponse;
import com.jokkorm.application.physical.CreateContainerBatchUseCase;
import com.jokkorm.application.physical.GetBatchHealthUseCase;
import com.jokkorm.application.physical.ListContainerBatchesUseCase;
import com.jokkorm.domain.common.PageRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lots de provisionnement (phase 5M). Voir docs/pattern/06-provisioning-masse-boites.md.
 */
@RestController
@RequestMapping("/api/v1/physical/batches")
@Tag(name = "Container batches", description = "Lots de provisionnement en masse de boîtes.")
public class ContainerBatchController {

    private final CreateContainerBatchUseCase createUseCase;
    private final GetBatchHealthUseCase healthUseCase;
    private final ListContainerBatchesUseCase listUseCase;

    public ContainerBatchController(
        CreateContainerBatchUseCase createUseCase,
        GetBatchHealthUseCase healthUseCase,
        ListContainerBatchesUseCase listUseCase) {
        this.createUseCase = createUseCase;
        this.healthUseCase = healthUseCase;
        this.listUseCase = listUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Crée un lot de N boîtes en masse (1 transaction).",
        description = "Génère N barcodes uniques (BOX-YYMMDD-xxxxxx), crée N containers "
            + "en transit (positionId=null, state=EMPTY), avec numérotation séquentielle "
            + "sequence_in_batch 1..N. Audit CONTAINER_BATCH_CREATED. Limite 1000 par lot.")
    public BatchCreatedResponse create(
        @Valid @RequestBody CreateContainerBatchRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        var result = createUseCase.execute(
            request.reference(), request.size(), request.labelPattern(),
            request.defaultCapacity(), actor);
        return new BatchCreatedResponse(
            ContainerBatchResponse.from(result.batch()),
            result.containers().stream()
                .map(PhysicalContainerResponse::from)
                .toList());
    }

    @GetMapping
    @Operation(summary = "Liste paginée des lots du tenant courant.")
    public List<ContainerBatchResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        return listUseCase.execute(PageRequest.of(page, size)).content().stream()
            .map(ContainerBatchResponse::from)
            .toList();
    }

    @GetMapping("/{batchId}/health")
    @Operation(
        summary = "Stats de santé d'un lot (progress deploiement, états containers).",
        description = "Transitionne auto le lot vers FULLY_DEPLOYED si plus aucun container "
            + "en transit. Utilisé par l'UI pour afficher une barre de progression.")
    public BatchHealthResponse health(@PathVariable UUID batchId) {
        return BatchHealthResponse.from(healthUseCase.execute(batchId));
    }

    public record BatchCreatedResponse(
        ContainerBatchResponse batch,
        List<PhysicalContainerResponse> containers) {}
}
