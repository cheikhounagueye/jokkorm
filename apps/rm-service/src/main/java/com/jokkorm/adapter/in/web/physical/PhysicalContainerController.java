package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.BarcodeImageGenerator;
import com.jokkorm.application.physical.BarcodeImageGenerator.Symbology;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.FindContainerByBarcodeUseCase;
import com.jokkorm.application.physical.ListContainersAtPositionUseCase;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.physical.SetContainerExternalReferenceUseCase;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Conteneurs physiques (phase 5B). CRUD minimal + mise en position.
 *
 * <ul>
 *   <li>{@code POST /api/v1/physical/containers} — crée une boîte vide.</li>
 *   <li>{@code PUT /api/v1/physical/containers/{id}/position} — range (ou déplace) la boîte.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/physical/containers")
@Tag(name = "Physical containers", description = "Conteneurs physiques (boîtes d'archives).")
public class PhysicalContainerController {

    private final CreatePhysicalContainerUseCase create;
    private final PlaceContainerUseCase place;
    private final ListContainersAtPositionUseCase listAtPosition;
    private final FindContainerByBarcodeUseCase findByBarcode;
    private final BarcodeImageGenerator imageGenerator;
    private final SetContainerExternalReferenceUseCase setExternalRef;
    private final com.jokkorm.application.physical.ChangeContainerFormatUseCase changeFormat;

    public PhysicalContainerController(
        CreatePhysicalContainerUseCase create,
        PlaceContainerUseCase place,
        ListContainersAtPositionUseCase listAtPosition,
        FindContainerByBarcodeUseCase findByBarcode,
        BarcodeImageGenerator imageGenerator,
        SetContainerExternalReferenceUseCase setExternalRef,
        com.jokkorm.application.physical.ChangeContainerFormatUseCase changeFormat) {
        this.create = create;
        this.place = place;
        this.listAtPosition = listAtPosition;
        this.findByBarcode = findByBarcode;
        this.imageGenerator = imageGenerator;
        this.setExternalRef = setExternalRef;
        this.changeFormat = changeFormat;
    }

    public record ExternalReferenceRequest(String externalReference) {}

    @PutMapping("/{id}/external-reference")
    @Operation(
        summary = "Fixe/efface la référence externe prestataire (ex. PRO ARCHIVES #19944).",
        description = "Référence libre (max 128 chars). Null ou vide efface. Idempotent "
            + "sur valeur inchangée. Audit CONTAINER_EXTERNAL_REFERENCE_SET avec old→new.")
    public PhysicalContainerResponse setExternalReference(
        @PathVariable UUID id,
        @RequestBody ExternalReferenceRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return PhysicalContainerResponse.from(
            setExternalRef.execute(id, request.externalReference(), actor));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Crée une boîte vide. Code-barres généré automatiquement (phase 5K).",
        description = "Le code-barres au format BOX-YYMMDD-xxxxxx est généré côté serveur "
            + "(Crockford base32, triable chronologiquement, unique par tenant). L'appelant "
            + "ne peut pas le choisir pour garantir la cohérence du format et l'unicité.")
    public PhysicalContainerResponse create(@Valid @RequestBody CreatePhysicalContainerRequest request) {
        return PhysicalContainerResponse.from(
            create.execute(request.label(), request.capacity(), request.boxFormatId()));
    }

    @PutMapping("/{id}/position")
    @Operation(
        summary = "Range (ou déplace) la boîte sur une position (feuille de la topographie).",
        description = "Contrôle en longueur (dos de la boîte vs place restante) et marge de "
            + "manipulation de la tablette. 409 si la boîte ne tient pas, si elle est en prêt "
            + "ou détruite. Audit CONTAINER_PLACED ou CONTAINER_MOVED (from → to).")
    public PhysicalContainerResponse place(
        @PathVariable UUID id,
        @Valid @RequestBody PlaceContainerRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return PhysicalContainerResponse.from(place.execute(id, request.positionId(), actor));
    }

    public record ChangeFormatRequest(@jakarta.validation.constraints.NotNull UUID boxFormatId) {}

    @PutMapping("/{id}/format")
    @Operation(
        summary = "Corrige le format d'une boîte (dos 8, 10, 15, 20…).",
        description = "Si la boîte est rangée, le nouveau dos doit tenir sur son emplacement. "
            + "Audit CONTAINER_FORMAT_CHANGED.")
    public PhysicalContainerResponse changeFormat(
        @PathVariable UUID id,
        @Valid @RequestBody ChangeFormatRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return PhysicalContainerResponse.from(changeFormat.execute(id, request.boxFormatId(), actor));
    }

    @GetMapping
    @Operation(summary = "Liste les boîtes rangées à une position donnée (positionId requis en phase 5H).")
    public List<PhysicalContainerResponse> listAtPosition(
        @Parameter(description = "UUID de la POSITION (feuille de la topographie)")
        @RequestParam UUID positionId) {
        return listAtPosition.execute(positionId).stream()
            .map(PhysicalContainerResponse::from)
            .toList();
    }

    @GetMapping("/by-barcode")
    @Operation(
        summary = "Lookup par code-barres — endpoint principal pour la douchette (phase 5K).",
        description = "Scanne un code-barres BOX-YYMMDD-xxxxxx avec un lecteur USB/Bluetooth, "
            + "le frontend envoie la chaîne lue à cet endpoint, retourne la boîte complète "
            + "(état, position, capacité) ou 404 container-not-found.")
    public PhysicalContainerResponse findByBarcode(
        @Parameter(description = "Code-barres lu par la douchette")
        @RequestParam String barcode) {
        return PhysicalContainerResponse.from(findByBarcode.execute(barcode));
    }

    @GetMapping(value = "/barcode-image", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(
        summary = "Rendu PNG d'un code-barres pour impression sur étiquette (phase 5L).",
        description = "Le contenu du barcode (BOX-YYMMDD-xxxxxx) est immuable, seul le "
            + "rendu visuel est configurable : symbologie CODE128 (universel 1D douchette) "
            + "ou QR (2D smartphone), dimensions en pixels, texte humain visible. Vérifie "
            + "d'abord que la boîte existe pour ne pas générer d'étiquette fantôme.")
    public ResponseEntity<byte[]> barcodeImage(
        @Parameter(description = "Code-barres de la boîte (doit exister en base)")
        @RequestParam String barcode,
        @Parameter(description = "Symbologie : CODE128 (recommandé douchette 1D) ou QR (smartphone)")
        @RequestParam(defaultValue = "CODE128") Symbology symbology,
        @Parameter(description = "Largeur en pixels [100, 2000]")
        @RequestParam(defaultValue = "600") int widthPx,
        @Parameter(description = "Hauteur en pixels [100, 2000] — ignoré pour QR (carré)")
        @RequestParam(defaultValue = "180") int heightPx,
        @Parameter(description = "Afficher le contenu en texte humain sous le barcode")
        @RequestParam(defaultValue = "true") boolean showLabel) {
        // Vérifie l'existence pour éviter de rendre des étiquettes de barcodes inexistants
        findByBarcode.execute(barcode);
        byte[] png = imageGenerator.render(barcode, symbology, widthPx, heightPx, showLabel);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setCacheControl("public, max-age=3600");
        return new ResponseEntity<>(png, headers, HttpStatus.OK);
    }
}
