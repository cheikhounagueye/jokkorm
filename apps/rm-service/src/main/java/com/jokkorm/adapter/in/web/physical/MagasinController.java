package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.MagasinScanService;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.physical.PositionLabelsPdfGenerator;
import com.jokkorm.application.physical.PositionLabelsPdfGenerator.PositionLabel;
import com.jokkorm.application.physical.ReceiveContainerUseCase;
import com.jokkorm.application.physical.RemoveContainerFromShelfUseCase;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Mode magasin (pattern 12) : les gestes de l'archiviste au scan, depuis un iPhone ou une douchette.
 *
 * <ul>
 *   <li>identifier — 0 geste : qu'est-ce que je viens de scanner ?</li>
 *   <li>arrivée — 1 scan : la boîte ;</li>
 *   <li>placer — 2 scans : la boîte puis l'emplacement ;</li>
 *   <li>retirer — 3 scans : la boîte, son emplacement, la boîte encore (le 3e confirme).</li>
 * </ul>
 * Les gestes sont réservés à l'archiviste et au responsable des archives ; le code-barres d'une
 * boîte ne se montre qu'au personnel des archives (pattern 08).
 */
@RestController
@RequestMapping("/api/v1/physical/magasin")
@Tag(name = "Mode magasin", description = "Gestes au scan : identifier, arrivée, placer, retirer ; étiquettes d'emplacement.")
public class MagasinController {

    private final MagasinScanService scan;
    private final ReceiveContainerUseCase receive;
    private final PlaceContainerUseCase place;
    private final RemoveContainerFromShelfUseCase remove;
    private final PositionLabelsPdfGenerator labels;

    public MagasinController(
        MagasinScanService scan,
        ReceiveContainerUseCase receive,
        PlaceContainerUseCase place,
        RemoveContainerFromShelfUseCase remove,
        PositionLabelsPdfGenerator labels) {
        this.scan = scan;
        this.receive = receive;
        this.place = place;
        this.remove = remove;
        this.labels = labels;
    }

    public record PositionView(UUID id, String path, String scanCode, String address) {}

    /** {@code kind} = CONTAINER ou POSITION ; seul le bloc correspondant est rempli. */
    public record ScanResponse(
        String code,
        String kind,
        PhysicalContainerResponse container,
        String containerAddress,
        boolean containerOnLoan,
        PositionView position) {}

    public record ArrivalRequest(@NotBlank String barcode) {}

    public record ArrivalResponse(PhysicalContainerResponse container, boolean alreadyReceived) {}

    public record PlacementRequest(@NotNull UUID containerId, @NotNull UUID positionId) {}

    public record RemovalRequest(@NotNull UUID containerId, @NotNull UUID positionId) {}

    @GetMapping("/scan")
    @Operation(
        summary = "Identifie un code scanné : boîte (BOX-…) ou emplacement (EMP-…).",
        description = "Cherche le code dans les boîtes ET les emplacements (pas de devinette sur le préfixe). "
            + "404 scan-code-unknown si inconnu dans l'espace ; 409 si le code désigne les deux.")
    public ScanResponse scan(@RequestParam String code) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD, UserRole.AUDITOR);
        MagasinScanService.ScanResult r = scan.resolve(code);
        if (r.container() != null) {
            return new ScanResponse(r.code(), "CONTAINER",
                PhysicalContainerResponse.from(r.container().container()),
                r.container().address(), r.container().onLoan(), null);
        }
        var p = r.position();
        return new ScanResponse(r.code(), "POSITION", null, null, false,
            new PositionView(p.position().id(), p.position().path(), p.position().scanCode().orElse(null), p.address()));
    }

    /** {@code recordCount} : dossiers dans la boîte (jauge, à comparer à {@code container.capacity}). */
    public record ContainerHit(PhysicalContainerResponse container, String address, boolean onLoan, long recordCount) {}

    @GetMapping("/containers")
    @Operation(
        summary = "Retrouver une boîte : libellé (contient) ou code-barres (commence par).",
        description = "Boîtes non détruites, les plus récemment modifiées d'abord (q vide = les dernières). "
            + "Avec l'adresse si la boîte est rangée et le nombre de dossiers qu'elle contient. "
            + "Réservé au personnel des archives (pattern 08).")
    public List<ContainerHit> searchContainers(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "20") int limit) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD, UserRole.AUDITOR);
        return scan.search(q, limit).stream()
            .map(h -> new ContainerHit(PhysicalContainerResponse.from(h.box().container()), h.box().address(),
                h.box().onLoan(), h.recordCount()))
            .toList();
    }

    @PostMapping("/arrivals")
    @Operation(
        summary = "Arrivée (1 scan) : la boîte est reçue en zone tampon.",
        description = "Garde qui l'a reçue et quand. 409 si la boîte est rangée (« Retirer » d'abord), en prêt "
            + "ou détruite. Pointer deux fois de suite ne change rien (alreadyReceived=true). Audit CONTAINER_RECEIVED.")
    public ArrivalResponse arrival(
        @Valid @RequestBody ArrivalRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        ReceiveContainerUseCase.Result r = receive.execute(request.barcode(), actor);
        return new ArrivalResponse(PhysicalContainerResponse.from(r.container()), r.alreadyReceived());
    }

    @PostMapping("/placements")
    @Operation(
        summary = "Placer (2 scans) : la boîte puis l'emplacement.",
        description = "Mêmes règles que le rangement depuis l'écran (place en longueur, marge de la tablette, "
            + "boîte en prêt ou détruite refusée). Audit CONTAINER_PLACED ou CONTAINER_MOVED.")
    public PhysicalContainerResponse placement(
        @Valid @RequestBody PlacementRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        return PhysicalContainerResponse.from(place.execute(request.containerId(), request.positionId(), actor));
    }

    @PostMapping("/removals")
    @Operation(
        summary = "Retirer (3 scans) : la boîte, son emplacement, la boîte encore.",
        description = "La boîte repasse en zone tampon et libère sa place. 409 container-not-at-position si "
            + "l'emplacement scanné n'est pas celui enregistré (adresse enregistrée dans currentAddress). "
            + "Audit CONTAINER_REMOVED ; la GED maître est prévenue.")
    public PhysicalContainerResponse removal(
        @Valid @RequestBody RemovalRequest request,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        return PhysicalContainerResponse.from(remove.execute(request.containerId(), request.positionId(), actor));
    }

    @GetMapping("/locations/{nodeId}/position-labels")
    @Operation(summary = "Emplacements à étiqueter sous un nœud (lui compris) : code et adresse.")
    public List<PositionLabel> positionLabels(@PathVariable UUID nodeId) {
        return labels.list(nodeId);
    }

    @GetMapping(value = "/locations/{nodeId}/position-labels.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(
        summary = "Planche d'étiquettes d'emplacement (Avery L7162, 16 par A4) : QR + Code128 + adresse.",
        description = "Une étiquette par emplacement sous le nœud. Le code ne change jamais : réimprimer ne "
            + "demande pas de recoller les autres. Audit POSITION_LABELS_PRINTED.")
    public ResponseEntity<byte[]> positionLabelsPdf(
        @PathVariable UUID nodeId,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        byte[] pdf = labels.generate(nodeId, actor);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.builder("attachment")
            .filename("etiquettes-emplacements-" + nodeId + ".pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
