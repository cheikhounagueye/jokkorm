package com.jokkorm.application.physical;

import com.jokkorm.application.audit.AuditChainService;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditTargetType;
import com.jokkorm.domain.physical.FurnitureType;
import com.jokkorm.domain.physical.PhysicalSpec;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Générateur de topographie à gabarit (pattern 10 § 4.4, 5T.C) : « 2 épis × 5 rangs × 2 faces,
 * 6 tablettes de 100 cm, rayonnage fixe » crée 20 travées, 120 tablettes et 120 emplacements
 * en une action, mesures et épis compris.
 *
 * <ul>
 *   <li>Convention double face (pattern 10 § 9) : une travée par rang et par face, A et B.</li>
 *   <li>Codes : travées T01, T02… et épis E1, E2… à partir du premier numéro libre de la salle.</li>
 *   <li>Par défaut, une tablette = un emplacement.</li>
 *   <li>À blanc ({@code dryRun}) : le même plan, sans aucune écriture ni trace d'audit.</li>
 * </ul>
 */
@Service
public class GenerateShelvingUseCase {

    /** Au-delà, on découpe en plusieurs générations (garde-fou contre une faute de frappe). */
    public static final int MAX_TRAVEES = 500;
    public static final int MAX_NODES = 20_000;

    private static final Pattern TRAVEE_CODE = Pattern.compile("^T(\\d+)$");
    private static final Pattern AISLE_CODE = Pattern.compile("^E(\\d+)$");

    public record Template(
        int aisles, int ranksPerFace, int faces, int tablettesPerTravee, int positionsPerTablette,
        Integer lengthMm, FurnitureType furnitureType, Integer depthMm, Integer clearanceMm, Integer heightMm) {}

    /** Ce qui sera (ou a été) créé. */
    public record Plan(int travees, int tablettes, int positions, long installedMm,
                       List<String> aisleCodes, String firstTravee, String lastTravee, boolean applied) {}

    private record PlannedTravee(String code, String aisle, int rank, String side) {}

    private final StorageLocationRepository locations;
    private final ShelfSpaceService space;
    private final AuditChainService audit;
    private final Clock clock;

    public GenerateShelvingUseCase(StorageLocationRepository locations, ShelfSpaceService space,
                                   AuditChainService audit, Clock clock) {
        this.locations = locations;
        this.space = space;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Plan execute(UUID storehouseId, Template t, boolean dryRun, String actor) {
        RoleContext.requireAny(UserRole.ARCHIVIST, UserRole.ARCHIVES_HEAD);
        TenantId tenant = TenantContext.current();
        StorageLocation room = locations.lockById(tenant, storehouseId)
            .orElseThrow(() -> new StorageLocationNotFoundException("Salle %s introuvable".formatted(storehouseId)));
        if (room.kind() != StorageLocationKind.STOREHOUSE) {
            throw new IllegalArgumentException("Le générateur crée des travées dans une salle (magasin)");
        }
        validate(t);
        // Valide les mesures une fois, avant toute création (bornes du domaine).
        PhysicalSpec base = new PhysicalSpec(t.furnitureType(), t.depthMm(), t.clearanceMm(), t.heightMm(),
            t.faces(), null, null, null);
        if (t.lengthMm() != null && (t.lengthMm() < StorageLocation.MIN_LENGTH_MM || t.lengthMm() > StorageLocation.MAX_LENGTH_MM)) {
            throw new IllegalArgumentException("Longueur de travée hors bornes : " + t.lengthMm() + " mm");
        }

        List<StorageLocation> shelves = locations.findChildren(tenant, room.id()).stream()
            .filter(l -> l.kind() == StorageLocationKind.SHELF).toList();
        List<PlannedTravee> planned = plan(t, shelves);
        int travees = planned.size();
        int tablettes = travees * t.tablettesPerTravee();
        int positions = tablettes * t.positionsPerTablette();
        int length = t.lengthMm() != null ? t.lengthMm() : space.defaultTabletteMm();
        long installed = (long) tablettes * length;
        List<String> aisles = planned.stream().map(PlannedTravee::aisle).distinct().toList();
        Plan plan = new Plan(travees, tablettes, positions, installed, aisles,
            planned.get(0).code(), planned.get(travees - 1).code(), !dryRun);
        if (dryRun) return plan;

        Instant now = clock.instant();
        for (PlannedTravee p : planned) {
            StorageLocation shelf = StorageLocation.createChild(room, StorageLocationKind.SHELF, p.code(), null, now);
            if (t.lengthMm() != null) shelf.changeLength(t.lengthMm(), now);
            shelf.changeSpec(new PhysicalSpec(base.furnitureType(), base.depthMm(), base.clearanceMm(),
                base.heightMm(), base.faces(), p.aisle(), p.rank(), p.side()), now);
            shelf = locations.save(shelf);
            for (int r = 1; r <= t.tablettesPerTravee(); r++) {
                StorageLocation rack = locations.save(
                    StorageLocation.createChild(shelf, StorageLocationKind.RACK, String.valueOf(r), null, now));
                for (int e = 1; e <= t.positionsPerTablette(); e++) {
                    locations.save(StorageLocation.createChild(rack, StorageLocationKind.POSITION,
                        "%02d".formatted(e), null, now));
                }
            }
        }
        audit.record(AuditAction.STORAGE_LOCATIONS_GENERATED, AuditTargetType.STORAGE_LOCATION, room.id(), actor,
            "path=%s epis=%s travees=%d (%s..%s) tablettes=%d positions=%d longueur=%d faces=%d type=%s"
                .formatted(room.path(), aisles, travees, plan.firstTravee(), plan.lastTravee(), tablettes, positions,
                    length, t.faces(), t.furnitureType()));
        return plan;
    }

    private static void validate(Template t) {
        range(t.aisles(), 1, 50, "nombre d'épis");
        range(t.ranksPerFace(), 1, 100, "travées par face");
        if (t.faces() != 1 && t.faces() != 2) throw new IllegalArgumentException("faces : 1 ou 2");
        range(t.tablettesPerTravee(), 1, 20, "tablettes par travée");
        range(t.positionsPerTablette(), 1, 20, "emplacements par tablette");
        int travees = t.aisles() * t.ranksPerFace() * t.faces();
        if (travees > MAX_TRAVEES) {
            throw new IllegalArgumentException("%d travées demandées : maximum %d par génération".formatted(travees, MAX_TRAVEES));
        }
        long nodes = (long) travees * (1 + t.tablettesPerTravee() * (1L + t.positionsPerTablette()));
        if (nodes > MAX_NODES) {
            throw new IllegalArgumentException("%d emplacements à créer : maximum %d par génération".formatted(nodes, MAX_NODES));
        }
    }

    /** Numéros libres de la salle : on évite tout code déjà pris, pas seulement le plus grand. */
    private static List<PlannedTravee> plan(Template t, List<StorageLocation> shelves) {
        Set<String> takenCodes = new HashSet<>();
        Set<String> takenAisles = new HashSet<>();
        int maxT = 0;
        int maxE = 0;
        for (StorageLocation s : shelves) {
            takenCodes.add(s.code());
            Matcher m = TRAVEE_CODE.matcher(s.code());
            if (m.matches()) maxT = Math.max(maxT, Integer.parseInt(m.group(1)));
            String a = s.spec().aisleCode();
            if (a != null) {
                takenAisles.add(a);
                Matcher ma = AISLE_CODE.matcher(a);
                if (ma.matches()) maxE = Math.max(maxE, Integer.parseInt(ma.group(1)));
            }
        }
        int total = t.aisles() * t.ranksPerFace() * t.faces();
        int width = String.valueOf(maxT + total).length() < 2 ? 2 : String.valueOf(maxT + total).length();

        List<PlannedTravee> out = new ArrayList<>();
        int nextT = maxT + 1;
        int nextE = maxE + 1;
        for (int a = 0; a < t.aisles(); a++) {
            String aisle;
            do { aisle = "E" + nextE++; } while (takenAisles.contains(aisle));
            for (int f = 0; f < t.faces(); f++) {
                String side = t.faces() == 2 ? (f == 0 ? "A" : "B") : null;
                for (int r = 1; r <= t.ranksPerFace(); r++) {
                    String code;
                    do { code = "T" + String.format("%0" + width + "d", nextT++); } while (takenCodes.contains(code));
                    takenCodes.add(code);
                    out.add(new PlannedTravee(code, aisle, r, side));
                }
            }
        }
        return out;
    }

    private static void range(int v, int min, int max, String name) {
        if (v < min || v > max) {
            throw new IllegalArgumentException("%s : entre %d et %d, reçu %d".formatted(name, min, max, v));
        }
    }
}
