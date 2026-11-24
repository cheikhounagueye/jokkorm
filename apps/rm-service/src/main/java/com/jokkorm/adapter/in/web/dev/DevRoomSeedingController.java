package com.jokkorm.adapter.in.web.dev;

import com.jokkorm.application.loan.MarkLoanOutUseCase;
import com.jokkorm.application.loan.RequestLoanUseCase;
import com.jokkorm.application.physical.BoxFormatCatalog;
import com.jokkorm.application.physical.CreateContainerBatchUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.physical.ShelfSpaceService;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.BoxFormatRepository;
import com.jokkorm.domain.physical.EquipmentKind;
import com.jokkorm.domain.physical.LinearCapacity;
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
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Salles de démonstration (phases 5S et 5T). DEV uniquement.
 *
 * <ul>
 *   <li>{@code SALLE-A} — 6 travées × 4 tablettes × 6 positions de 60 cm (boîtes dos 10),
 *       telle que créée en 5S, longueurs explicites.</li>
 *   <li>{@code SALLE-B} — à l'échelle réelle (pattern 10) : travées de 1 m, 5 tablettes,
 *       un emplacement par tablette, formats mélangés dos 8 / 10 / 15 / 20. Contient une
 *       tablette pleine jusqu'à la marge et une tablette où il ne reste que 5 cm.</li>
 * </ul>
 * Idempotent salle par salle : une salle déjà présente n'est pas touchée.
 */
@RestController
@RequestMapping("/api/v1/dev")
@Tag(name = "Dev seeding", description = "Endpoints DEV UNIQUEMENT — désactivés en prod.")
@ConditionalOnProperty(name = "jokkorm.dev.seeding-enabled", havingValue = "true")
public class DevRoomSeedingController {

    private static final Logger LOG = LoggerFactory.getLogger(DevRoomSeedingController.class);
    private static final String ACTOR = "seed-demo";

    /** Salle A : code, thème, taux de remplissage visé. */
    private static final Object[][] SHELVES_A = {
        {"T01", "Ressources humaines",      0.90},
        {"T02", "Finances & comptabilité",  0.72},
        {"T03", "Marchés publics",          0.60},
        {"T04", "Affaires juridiques",      0.45},
        {"T05", "Direction & courrier",     0.30},
        {"T06", "Extension — réserve",      0.06},
    };
    private static final int RACKS_A = 4;
    private static final int POSITIONS_A = 6;
    private static final int POSITION_A_MM = 600;

    /** Salle B : code, thème, taux visé. Travées de 1 m, 5 tablettes. */
    private static final Object[][] SHELVES_B = {
        {"T01", "Comptabilité 2015-2019",  0.93},
        {"T02", "Paie & personnel",        0.84},
        {"T03", "Marchés 2018-2022",       0.68},
        {"T04", "Contentieux",             0.50},
        {"T05", "Courrier départ",         0.32},
        {"T06", "Réserve",                 0.00},
    };
    private static final int RACKS_B = 5;
    private static final int TRAVEE_B_MM = 1000;

    private final StorageLocationRepository locations;
    private final CreateStorageLocationUseCase createLocation;
    private final CreateContainerBatchUseCase createBatch;
    private final PlaceContainerUseCase place;
    private final PhysicalContainerRepository containers;
    private final RequestLoanUseCase requestLoan;
    private final MarkLoanOutUseCase markLoanOut;
    private final RoomEquipmentRepository equipment;
    private final BoxFormatCatalog catalog;
    private final BoxFormatRepository formats;
    private final ShelfSpaceService space;
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
        BoxFormatCatalog catalog,
        BoxFormatRepository formats,
        ShelfSpaceService space,
        Clock clock) {
        this.locations = locations;
        this.createLocation = createLocation;
        this.createBatch = createBatch;
        this.place = place;
        this.containers = containers;
        this.requestLoan = requestLoan;
        this.markLoanOut = markLoanOut;
        this.equipment = equipment;
        this.catalog = catalog;
        this.formats = formats;
        this.space = space;
        this.clock = clock;
    }

    public record RoomSeedResult(String code, UUID roomId, boolean alreadySeeded, int positions, int boxes,
                                 int loans, int equipment) {}

    @PostMapping("/seed-demo-room")
    @Operation(summary = "Crée les salles de démo SALLE-A et SALLE-B. Idempotent salle par salle.")
    @Transactional
    public List<RoomSeedResult> seedDemoRoom() {
        TenantId tenant = TenantContext.current();
        StorageLocation site = locations.findRoots(tenant).stream()
            .filter(l -> l.kind() == StorageLocationKind.SITE)
            .findFirst()
            .orElseGet(() -> createLocation.createSite("DKR", "Dakar centre"));
        catalog.defaultFormat(tenant);
        return List.of(seedRoomA(tenant, site), seedRoomB(tenant, site));
    }

    // ─── Salle A (5S) ───────────────────────────────────────────────────────

    private RoomSeedResult seedRoomA(TenantId tenant, StorageLocation site) {
        Optional<StorageLocation> existing = child(tenant, site, "SALLE-A");
        if (existing.isPresent()) return new RoomSeedResult("SALLE-A", existing.get().id(), true, 0, 0, 0, 0);

        StorageLocation room = createLocation.createChild(
            site.id(), StorageLocationKind.STOREHOUSE, "SALLE-A", "Salle d'archives A — sous-sol");
        Random rnd = new Random(42);
        int positionCount = 0;
        int boxCount = 0;
        int fillableRack = LinearCapacity.fillableMm(RACKS_A == 0 ? 0 : POSITIONS_A * POSITION_A_MM,
            space.marginPercent());
        List<PhysicalContainer> openBoxes = new ArrayList<>();

        for (Object[] spec : SHELVES_A) {
            String code = (String) spec[0];
            String theme = (String) spec[1];
            double fill = (double) spec[2];
            StorageLocation shelf = createLocation.createChild(room.id(), StorageLocationKind.SHELF, code, theme);

            Map<UUID, Integer> wanted = new LinkedHashMap<>();
            for (int r = 1; r <= RACKS_A; r++) {
                StorageLocation rack = withLength(createLocation.createChild(
                    shelf.id(), StorageLocationKind.RACK, String.valueOf(r), "Tablette " + r),
                    POSITIONS_A * POSITION_A_MM);
                int rackUsed = 0;
                for (int p = 1; p <= POSITIONS_A; p++) {
                    StorageLocation position = withLength(createLocation.createChild(
                        rack.id(), StorageLocationKind.POSITION, "%02d".formatted(p), null), POSITION_A_MM);
                    positionCount++;
                    int places = POSITION_A_MM / 100;
                    long n = Math.round(places * (fill + rnd.nextGaussian() * 0.22));
                    int boxes = (int) Math.max(0, Math.min(places, n));
                    boxes = Math.min(boxes, (fillableRack - rackUsed) / 100);
                    rackUsed += boxes * 100;
                    wanted.put(position.id(), boxes);
                }
            }

            int total = wanted.values().stream().mapToInt(Integer::intValue).sum();
            if (total == 0) continue;
            var batch = createBatch.execute("Démo salle A — " + code, total, theme, 50, spine(tenant, 100), ACTOR);
            var boxes = batch.containers().iterator();
            for (Map.Entry<UUID, Integer> e : wanted.entrySet()) {
                for (int i = 0; i < e.getValue(); i++) {
                    placeWithState(boxes.next(), e.getKey(), rnd, openBoxes);
                    boxCount++;
                }
            }
        }

        int loans = seedLoans(openBoxes, 0, 3);
        int equipmentCount = seedEquipment(tenant, room.id());
        LOG.info("[dev-seed] Salle {} : {} positions, {} boîtes, {} prêts", room.path(), positionCount, boxCount, loans);
        return new RoomSeedResult("SALLE-A", room.id(), false, positionCount, boxCount, loans, equipmentCount);
    }

    // ─── Salle B (5T, échelle réelle) ───────────────────────────────────────

    private RoomSeedResult seedRoomB(TenantId tenant, StorageLocation site) {
        Optional<StorageLocation> existing = child(tenant, site, "SALLE-B");
        if (existing.isPresent()) return new RoomSeedResult("SALLE-B", existing.get().id(), true, 0, 0, 0, 0);

        StorageLocation room = createLocation.createChild(
            site.id(), StorageLocationKind.STOREHOUSE, "SALLE-B", "Salle d'archives B — formats mélangés");
        Random rnd = new Random(7);
        int fillable = LinearCapacity.fillableMm(TRAVEE_B_MM, space.marginPercent());
        int[] spines = {100, 100, 100, 100, 100, 150, 150, 150, 80, 200};
        int positionCount = 0;
        int boxCount = 0;
        List<PhysicalContainer> openBoxes = new ArrayList<>();

        for (Object[] spec : SHELVES_B) {
            String code = (String) spec[0];
            String theme = (String) spec[1];
            double fill = (double) spec[2];
            StorageLocation shelf = withLength(
                createLocation.createChild(room.id(), StorageLocationKind.SHELF, code, theme), TRAVEE_B_MM);

            // Plan : (position, dos) dans l'ordre de rangement, en respectant longueur et marge.
            List<Map.Entry<UUID, Integer>> plan = new ArrayList<>();
            for (int r = 1; r <= RACKS_B; r++) {
                StorageLocation rack = createLocation.createChild(
                    shelf.id(), StorageLocationKind.RACK, String.valueOf(r), "Tablette " + r);
                StorageLocation position = createLocation.createChild(
                    rack.id(), StorageLocationKind.POSITION, "01", null);
                positionCount++;

                List<Integer> load = new ArrayList<>();
                if (code.equals("T01") && r == 1) {
                    load = List.of(150, 150, 150, 150, 150, 100, 100);   // 950 mm : marge atteinte
                } else if (code.equals("T02") && r == 1) {
                    load = List.of(150, 150, 150, 150, 100, 100, 100);   // 900 mm : reste 5 cm
                } else {
                    double target = Math.max(0, Math.min(0.95, fill + rnd.nextGaussian() * 0.12)) * TRAVEE_B_MM;
                    int used = 0;
                    while (true) {
                        int s = spines[rnd.nextInt(spines.length)];
                        if (used + s > Math.min(target, fillable)) break;
                        load.add(s);
                        used += s;
                    }
                }
                load.forEach(s -> plan.add(Map.entry(position.id(), s)));
            }

            Map<Integer, List<Map.Entry<UUID, Integer>>> bySpine = plan.stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue, LinkedHashMap::new, Collectors.toList()));
            Map<Integer, java.util.Iterator<PhysicalContainer>> boxesBySpine = new LinkedHashMap<>();
            for (var e : bySpine.entrySet()) {
                // Un lot par format : le dos entre dans le libellé commun, qui reste unique dans l'espace.
                var batch = createBatch.execute("Démo salle B — %s — dos %d".formatted(code, e.getKey() / 10),
                    e.getValue().size(), "%s — dos %d".formatted(theme, e.getKey() / 10), 50,
                    spine(tenant, e.getKey()), ACTOR);
                boxesBySpine.put(e.getKey(), batch.containers().iterator());
            }
            for (var step : plan) {
                placeWithState(boxesBySpine.get(step.getValue()).next(), step.getKey(), rnd, openBoxes);
                boxCount++;
            }
        }

        int loans = seedLoans(openBoxes, 1, 1);
        int equipmentCount = seedEquipment(tenant, room.id());
        LOG.info("[dev-seed] Salle {} : {} positions, {} boîtes, {} prêts", room.path(), positionCount, boxCount, loans);
        return new RoomSeedResult("SALLE-B", room.id(), false, positionCount, boxCount, loans, equipmentCount);
    }

    // ─── Outils ─────────────────────────────────────────────────────────────

    private Optional<StorageLocation> child(TenantId tenant, StorageLocation site, String code) {
        return locations.findChildren(tenant, site.id()).stream().filter(l -> l.code().equals(code)).findFirst();
    }

    private StorageLocation withLength(StorageLocation node, int mm) {
        node.changeLength(mm, clock.instant());
        return locations.save(node);
    }

    private UUID spine(TenantId tenant, int spineMm) {
        return formats.findAll(tenant).stream()
            .filter(f -> f.active() && f.spineMm() == spineMm)
            .map(BoxFormat::id)
            .findFirst()
            .orElseGet(() -> catalog.defaultFormat(tenant).id());
    }

    private void placeWithState(PhysicalContainer box, UUID positionId, Random rnd, List<PhysicalContainer> open) {
        PhysicalContainer placed = place.execute(box.id(), positionId, ACTOR);
        double state = rnd.nextDouble();
        if (state < 0.55) {
            placed.markHasContent(clock.instant());
            open.add(containers.save(placed));
        } else if (state < 0.78) {
            placed.markHasContent(clock.instant());
            placed.seal(clock.instant());
            containers.save(placed);
        }
    }

    private int seedLoans(List<PhysicalContainer> openBoxes, int firstBorrower, int count) {
        String[][] borrowers = {
            {"Awa Diop", "DRH", "Audit RH 2026-Q4"},
            {"Mouhamadou Fall", "Juridique", "Contentieux marché fournitures"},
            {"Fatou Ndiaye", "Finances", "Contrôle de la Cour des comptes"},
        };
        int created = 0;
        for (int i = 0; i < count && i * 37 < openBoxes.size(); i++) {
            String[] b = borrowers[(firstBorrower + i) % borrowers.length];
            PhysicalContainer box = openBoxes.get(i * 37);
            Instant due = clock.instant().plus(Duration.ofDays(7L + 7L * i));
            var loan = requestLoan.execute(box.id(), b[0], b[1], b[2], due, ACTOR);
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
