package com.jokkorm.adapter.in.web.physical;

import com.jokkorm.application.physical.GetRoomViewUseCase;
import com.jokkorm.application.physical.RoomEquipmentUseCase;
import com.jokkorm.application.physical.RoomView;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.EquipmentKind;
import com.jokkorm.domain.physical.RoomEquipment;
import com.jokkorm.domain.physical.StorageLocation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vue « salle d'archives » pour la visite 3D (phase 5S).
 *
 * <p>Confidentialité (pattern 08) : code-barres et libellé de boîte ne sont renvoyés
 * qu'aux rôles ARCHIVIST / ARCHIVES_HEAD / AUDITOR. Un libellé révèle le contenu.
 */
@RestController
@RequestMapping("/api/v1/physical/rooms")
@Tag(name = "Rooms", description = "Vue d'un magasin : structure, boîtes, occupation, équipements.")
public class RoomController {

    private final GetRoomViewUseCase viewUseCase;
    private final RoomEquipmentUseCase equipmentUseCase;
    private final com.jokkorm.application.physical.RoomSpecUseCase specUseCase;
    private final com.jokkorm.application.physical.GenerateShelvingUseCase generator;

    public RoomController(GetRoomViewUseCase viewUseCase, RoomEquipmentUseCase equipmentUseCase,
                          com.jokkorm.application.physical.RoomSpecUseCase specUseCase,
                          com.jokkorm.application.physical.GenerateShelvingUseCase generator) {
        this.specUseCase = specUseCase;
        this.generator = generator;
        this.viewUseCase = viewUseCase;
        this.equipmentUseCase = equipmentUseCase;
    }

    @GetMapping("/{storehouseId}")
    @Operation(summary = "Structure complète d'un magasin avec boîtes, prêts actifs, équipements et occupation.")
    public RoomResponse view(@PathVariable UUID storehouseId) {
        boolean reveal = RoleContext.hasAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD, UserRole.AUDITOR);
        return RoomResponse.from(viewUseCase.execute(storehouseId), reveal);
    }

    @GetMapping("/{storehouseId}/spec")
    @Operation(summary = "Dimensions, limites et consignes de la salle, et contrôle du mobilier (avertissements).")
    public com.jokkorm.application.physical.RoomSpecUseCase.View spec(@PathVariable UUID storehouseId) {
        return specUseCase.get(storehouseId);
    }

    @org.springframework.web.bind.annotation.PutMapping("/{storehouseId}/spec")
    @Operation(summary = "Saisit les dimensions de la salle (mm, m², kg/m², °C, %HR). Archiviste.",
        description = "Toutes les valeurs sont facultatives ; aucune n'est préremplie (pattern 10 § 6).")
    public com.jokkorm.application.physical.RoomSpecUseCase.View setSpec(
        @PathVariable UUID storehouseId,
        @org.springframework.web.bind.annotation.RequestBody com.jokkorm.application.physical.RoomSpecUseCase.Input body,
        @org.springframework.web.bind.annotation.RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return specUseCase.set(storehouseId, body, actor);
    }

    @PostMapping("/{storehouseId}/generate")
    @Operation(summary = "Générateur à gabarit : crée des épis de travées, tablettes et emplacements (5T.C).",
        description = "dryRun=true : renvoie le plan (codes, totaux) sans rien écrire. Archiviste. "
            + "Convention double face : une travée par rang et par face (A/B).")
    public com.jokkorm.application.physical.GenerateShelvingUseCase.Plan generate(
        @PathVariable UUID storehouseId,
        @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean dryRun,
        @org.springframework.web.bind.annotation.RequestBody com.jokkorm.application.physical.GenerateShelvingUseCase.Template body,
        @org.springframework.web.bind.annotation.RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        return generator.execute(storehouseId, body, dryRun, actor);
    }

    public record AddEquipmentRequest(
        @NotNull EquipmentKind kind,
        String label,
        @DecimalMin("0.0") @DecimalMax("1.0") double x,
        @DecimalMin("0.0") @DecimalMax("1.0") double z,
        LocalDate lastInspectionOn,
        LocalDate nextInspectionOn) {}

    @PostMapping("/{storehouseId}/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place un équipement (extincteur, détecteur, issue, thermo-hygromètre) dans le magasin.")
    public EquipmentResponse addEquipment(
        @PathVariable UUID storehouseId,
        @Valid @RequestBody AddEquipmentRequest body,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        RoomEquipment saved = equipmentUseCase.add(storehouseId, body.kind(), body.label(), body.x(), body.z(),
            body.lastInspectionOn(), body.nextInspectionOn(), actor);
        return EquipmentResponse.from(saved, LocalDate.now());
    }

    @DeleteMapping("/{storehouseId}/equipment/{equipmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retire un équipement du magasin.")
    public void removeEquipment(
        @PathVariable UUID storehouseId,
        @PathVariable UUID equipmentId,
        @RequestHeader(value = "X-Actor", defaultValue = "archiviste") String actor) {
        equipmentUseCase.remove(storehouseId, equipmentId, actor);
    }

    // ─── DTO ────────────────────────────────────────────────────────────────

    /**
     * Occupation en longueur (pattern 10). {@code capacity} = équivalent en boîtes du format
     * par défaut, gardé pour les écrans qui comptent en « places ».
     */
    public record Occupancy(int boxes, int capacity, double rate, int usedMm, int lengthMm, int placeableMm) {
        static Occupancy from(RoomView.Occupancy o) {
            return new Occupancy(o.boxes(), o.places(), Math.round(o.rate() * 1000) / 1000.0,
                o.usedMm(), o.lengthMm(), o.placeableMm());
        }
    }

    public record Node(UUID id, String code, String label, String path) {
        static Node from(StorageLocation l) {
            return new Node(l.id(), l.code(), l.label(), l.path());
        }
    }

    public record BoxDto(UUID id, String barcode, String label, ContainerState state, int spineMm,
                         String formatCode, boolean onLoan, boolean overdue, Instant expectedReturnAt) {}

    public record PositionDto(Node node, Occupancy occupancy, List<BoxDto> boxes) {}

    public record RackDto(Node node, Occupancy occupancy, List<PositionDto> positions) {}

    public record ShelfDto(Node node, Occupancy occupancy, List<RackDto> racks) {}

    public record EquipmentResponse(UUID id, EquipmentKind kind, String label, double x, double z,
                                    boolean ceilingMounted, LocalDate lastInspectionOn,
                                    LocalDate nextInspectionOn, boolean inspectionOverdue) {
        static EquipmentResponse from(RoomEquipment e, LocalDate today) {
            return new EquipmentResponse(e.id(), e.kind(), e.label().orElse(null), e.x(), e.z(),
                e.kind().ceilingMounted(), e.lastInspectionOn().orElse(null),
                e.nextInspectionOn().orElse(null), e.inspectionOverdue(today));
        }
    }

    public record RoomResponse(Node room, Occupancy occupancy, int positions, int boxesOnLoan,
                               boolean detailsRevealed, List<ShelfDto> shelves,
                               List<EquipmentResponse> equipment, int defaultSpineMm, int marginPercent) {

        static RoomResponse from(RoomView v, boolean reveal) {
            int[] positions = {0};
            int[] onLoan = {0};
            List<ShelfDto> shelves = v.shelves().stream().map(s -> new ShelfDto(
                Node.from(s.shelf()), Occupancy.from(s.occupancy()),
                s.racks().stream().map(r -> new RackDto(
                    Node.from(r.rack()), Occupancy.from(r.occupancy()),
                    r.positions().stream().map(p -> {
                        positions[0]++;
                        return new PositionDto(Node.from(p.position()), Occupancy.from(p.occupancy()),
                            p.boxes().stream().map(b -> {
                                if (b.onLoan()) onLoan[0]++;
                                var c = b.container();
                                return new BoxDto(c.id(),
                                    reveal ? c.barcode() : null,
                                    reveal ? c.label() : null,
                                    c.state(), b.spineMm(), b.formatCode(),
                                    b.onLoan(), b.overdue(), b.expectedReturnAt());
                            }).toList());
                    }).toList())).toList())).toList();
            List<EquipmentResponse> equipment = v.equipment().stream()
                .map(e -> EquipmentResponse.from(e, v.today())).toList();
            return new RoomResponse(Node.from(v.storehouse()), Occupancy.from(v.occupancy()),
                positions[0], onLoan[0], reveal, shelves, equipment, v.defaultSpineMm(), v.marginPercent());
        }
    }
}
