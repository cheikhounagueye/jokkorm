package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.AveryTemplate;
import com.jokkorm.application.physical.BarcodeImageGenerator.Symbology;
import com.jokkorm.application.physical.BatchLabelsPdfGenerator;
import com.jokkorm.application.physical.BufferZoneAlertUseCase;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final BatchLabelsPdfGenerator pdfGenerator;
    private final BufferZoneAlertUseCase bufferAlert;

    public ContainerBatchController(
        CreateContainerBatchUseCase createUseCase,
        GetBatchHealthUseCase healthUseCase,
        ListContainerBatchesUseCase listUseCase,
        BatchLabelsPdfGenerator pdfGenerator,
        BufferZoneAlertUseCase bufferAlert) {
        this.createUseCase = createUseCase;
        this.healthUseCase = healthUseCase;
        this.listUseCase = listUseCase;
        this.pdfGenerator = pdfGenerator;
        this.bufferAlert = bufferAlert;
    }

    @GetMapping("/alerts/buffer-zone")
    @Operation(
        summary = "Alertes zone tampon : boîtes jamais rangées depuis > N jours (phase 5O).",
        description = "Seuil paramétrable via jokkorm.physical.buffer-zone-alert-days "
            + "(défaut 14). Retourne par lot le nombre de boîtes stale et la plus "
            + "ancienne. hasAlerts=false si rien à signaler.")
    public BufferZoneAlertUseCase.Alert bufferZoneAlerts() {
        return bufferAlert.execute();
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
            request.defaultCapacity(), request.boxFormatId(), actor);
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

    @org.springframework.web.bind.annotation.GetMapping(value = "/{batchId}/labels.pdf",
        produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(
        summary = "PDF A4 multi-pages avec toutes les étiquettes d'un lot (phase 5M.2).",
        description = "Génère les étiquettes selon un template Avery (L7160 = 21/A4, "
            + "L7159 = 24/A4, L7162 = 16/A4 ; l'ancien nom AVERY_L7169 reste accepté), symbologie CODE128 ou QR, prêt à imprimer. "
            + "Marque le lot comme PRINTED + audit CONTAINER_BATCH_PRINTED. Rejoué = "
            + "lastPrintedAt mis à jour, pas de doublon côté containers.")
    public ResponseEntity<byte[]> labelsPdf(
        @PathVariable UUID batchId,
        @Parameter(description = "Template Avery")
        @RequestParam(defaultValue = "AVERY_L7160") AveryTemplate template,
        @Parameter(description = "Symbologie des codes-barres")
        @RequestParam(defaultValue = "CODE128") Symbology symbology,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        byte[] pdf = pdfGenerator.generate(batchId, template, symbology, actor);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
            org.springframework.http.ContentDisposition.builder("attachment")
                .filename("labels-" + batchId + ".pdf")
                .build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    public record BatchCreatedResponse(
        ContainerBatchResponse batch,
        List<PhysicalContainerResponse> containers) {}
}
