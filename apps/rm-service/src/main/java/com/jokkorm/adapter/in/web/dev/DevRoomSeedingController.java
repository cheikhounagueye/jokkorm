package com.jokkorm.adapter.in.web.dev;

import com.jokkorm.application.loan.MarkLoanOutUseCase;
import com.jokkorm.application.loan.RequestLoanUseCase;
import com.jokkorm.application.physical.CreateContainerBatchUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.EquipmentKind;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.RoomEquipment;
import com.jokkorm.domain.physical.RoomEquipmentRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Salle de démonstration pour la visite 3D (phase 5S). DEV uniquement.
 *
 * <p>Crée, sous le premier site du tenant, un magasin {@code SALLE-A} de 6 travées ×
 * 4 tablettes × 6 positions, des lots de boîtes dédiés (le lot démo existant n'est pas
 * touché), un remplissage varié, quelques prêts et les équipements de sécurité.
 * Idempotent : si {@code SALLE-A} existe déjà, ne fait rien et renvoie son id.
 */
@RestController
@RequestMapping("/api/v1/dev")
@Tag(name = "Dev seeding", description = "Endpoints DEV UNIQUEMENT — désactivés en prod.")
@ConditionalOnProperty(name = "jokkorm.dev.seeding-enabled", havingValue = "true")
public class DevRoomSeedingController {

    private static final Logger LOG = LoggerFactory.getLogger(DevRoomSeedingController.class);
    private static final String ROOM_CODE = "SALLE-A";
    private static final String ACTOR = "seed-demo";

    /** Code, thème, taux de remplissage visé. */
    private static final Object[][] SHELVES = {
        {"T01", "Ressources humaines",      0.90},
        {"T02", "Finances & comptabilité",  0.72},
        {"T03", "Marchés publics",          0.60},
        {"T04", "Affaires juridiques",      0.45},
        {"T05", "Direction & courrier",     0.30},
        {"T06", "Extension — réserve",      0.06},
    };
    private static final int RACKS = 4;
    private static final int POSITIONS = 6;

    private final StorageLocationRepository locations;
    private final CreateStorageLocationUseCase createLocation;
    private final CreateContainerBatchUseCase createBatch;
    private final PlaceContainerUseCase place;
    private final PhysicalContainerRepository containers;
    private final RequestLoanUseCase requestLoan;
    private final MarkLoanOutUseCase markLoanOut;
    private final RoomEquipmentRepository equipment;
    private final Clock clock;

    public DevRoomSeedingController(
        StorageLocationRepository locations,
        CreateStorageLocationUseCase createLocation,
        CreateContainerBatchUseCase createBatch,
        PlaceContainerUseCase place,
        PhysicalContainerRepository containers,
        RequestLoanUseCase requestLoan,
        MarkLoanOutUseCase markLoanOut,
        RoomEquipmentRepository equipment,
        Clock clock) {
        this.locations = locations;
        this.createLocation = createLocation;
        this.createBatch = createBatch;
        this.place = place;
        this.containers = containers;
        this.requestLoan = requestLoan;
        this.markLoanOut = markLoanOut;
        this.equipment = equipment;
        this.clock = clock;
    }

    public record RoomSeedResult(UUID roomId, boolean alreadySeeded, int positions, int boxes,
                                 int loans, int equipment) {}

    @PostMapping("/seed-demo-room")
    @Operation(summary = "Crée la salle de démo SALLE-A (travées, boîtes, prêts, équipements). Idempotent.")
    @Transactional
    public RoomSeedResult seedDemoRoom() {
        TenantId tenant = TenantContext.current();

        StorageLocation site = locations.findRoots(tenant).stream()
            .filter(l -> l.kind() == StorageLocationKind.SITE)
            .findFirst()
            .orElseGet(() -> createLocation.createSite("DKR", "Dakar centre"));

        Optional<StorageLocation> existing = locations.findChildren(tenant, site.id()).stream()
            .filter(l -> l.code().equals(ROOM_CODE))
            .findFirst();
        if (existing.isPresent()) {
            return new RoomSeedResult(existing.get().id(), true, 0, 0, 0, 0);
        }

        StorageLocation room = createLocation.createChild(
            site.id(), StorageLocationKind.STOREHOUSE, ROOM_CODE, "Salle d'archives A — sous-sol");

        Random rnd = new Random(42);
        int positionCount = 0;
        int boxCount = 0;
        List<PhysicalContainer> openBoxes = new ArrayList<>();

        for (Object[] spec : SHELVES) {
            String code = (String) spec[0];
            String theme = (String) spec[1];
            double fill = (double) spec[2];
            StorageLocation shelf = createLocation.createChild(room.id(), StorageLocationKind.SHELF, code, theme);

            Map<UUID, Integer> wanted = new LinkedHashMap<>();
            for (int r = 1; r <= RACKS; r++) {
                StorageLocation rack = createLocation.createChild(
                    shelf.id(), StorageLocationKind.RACK, String.valueOf(r), "Tablette " + r);
                for (int p = 1; p <= POSITIONS; p++) {
                    StorageLocation position = createLocation.createChild(
                        rack.id(), StorageLocationKind.POSITION, "%02d".formatted(p), null);
                    positionCount++;
                    int capacity = StorageLocation.DEFAULT_POSITION_CAPACITY;
                    long n = Math.round(capacity * (fill + rnd.nextGaussian() * 0.22));
                    wanted.put(position.id(), (int) Math.max(0, Math.min(capacity, n)));
                }
            }

            int total = wanted.values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0) continue;
            var batch = createBatch.execute("Démo salle A — " + code, total, theme, 50, ACTOR);
            var boxes = batch.containers().iterator();
            for (Map.Entry<UUID, Integer> e : wanted.entrySet()) {
                for (int i = 0; i < e.getValue(); i++) {
                    PhysicalContainer placed = place.execute(boxes.next().id(), e.getKey());
                    double state = rnd.nextDouble();
                    if (state < 0.55) {
                        placed.markHasContent(clock.instant());
                        openBoxes.add(containers.save(placed));
                    } else if (state < 0.78) {
                        placed.markHasContent(clock.instant());
                        placed.seal(clock.instant());
                        containers.save(placed);
                    }
                    boxCount++;
                }
            }
        }

        int loans = seedLoans(openBoxes);
        int equipmentCount = seedEquipment(tenant, room.id());

        LOG.info("[dev-seed] Salle {} : {} positions, {} boîtes, {} prêts, {} équipements",
            room.path(), positionCount, boxCount, loans, equipmentCount);
        return new RoomSeedResult(room.id(), false, positionCount, boxCount, loans, equipmentCount);
    }

    private int seedLoans(List<PhysicalContainer> openBoxes) {
        String[][] borrowers = {
            {"Awa Diop", "DRH", "Audit RH 2026-Q4"},
            {"Mouhamadou Fall", "Juridique", "Contentieux marché fournitures"},
            {"Fatou Ndiaye", "Finances", "Contrôle de la Cour des comptes"},
        };
        int created = 0;
        for (int i = 0; i < borrowers.length && i * 37 < openBoxes.size(); i++) {
            PhysicalContainer box = openBoxes.get(i * 37);
            Instant due = clock.instant().plus(Duration.ofDays(7L + 7L * i));
            var loan = requestLoan.execute(box.id(), borrowers[i][0], borrowers[i][1], borrowers[i][2], due, ACTOR);
            markLoanOut.execute(loan.id(), ACTOR);
            created++;
        }
        return created;
    }

    private int seedEquipment(TenantId tenant, UUID roomId) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        List<RoomEquipment> items = List.of(
            RoomEquipment.place(tenant, roomId, EquipmentKind.FIRE_EXTINGUISHER, "Extincteur CO2 — E1",
                0.0, 0.18, today.minusMonths(8), today.plusMonths(4), now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.FIRE_EXTINGUISHER, "Extincteur eau pulvérisée — E2",
                1.0, 0.18, today.minusMonths(8), today.plusMonths(4), now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.FIRE_EXTINGUISHER, "Extincteur CO2 — E3",
                0.0, 0.78, today.minusMonths(6), today.plusMonths(6), now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.FIRE_EXTINGUISHER, "Extincteur CO2 — E4",
                1.0, 0.78, today.minusMonths(13), today.minusDays(12), now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.EMERGENCY_EXIT, "Entrée principale",
                0.5, 0.0, null, null, now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.EMERGENCY_EXIT, "Issue de secours",
                1.0, 0.95, null, null, now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.THERMO_HYGROMETER, "Thermo-hygromètre — 19 °C / 50 %HR",
                0.0, 0.5, today.minusMonths(2), today.plusMonths(10), now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.SMOKE_DETECTOR, "Détecteur D1",
                0.3, 0.25, today.minusMonths(3), today.plusMonths(9), now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.SMOKE_DETECTOR, "Détecteur D2",
                0.7, 0.25, today.minusMonths(3), today.plusMonths(9), now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.SMOKE_DETECTOR, "Détecteur D3",
                0.3, 0.7, today.minusMonths(3), today.plusMonths(9), now),
            RoomEquipment.place(tenant, roomId, EquipmentKind.SMOKE_DETECTOR, "Détecteur D4",
                0.7, 0.7, today.minusMonths(3), today.plusMonths(9), now));
        items.forEach(equipment::save);
        return items.size();
    }
}
