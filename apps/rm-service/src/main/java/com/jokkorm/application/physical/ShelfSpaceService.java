package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.LinearCapacity;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Place disponible sur une tablette, en millimètres (pattern 10). Seule source des règles
 * de rangement : longueur de l'emplacement, marge de manipulation de la tablette, et
 * (5T.B) hauteur libre et profondeur de la tablette, héritées de la travée sauf surcharge.
 * Une hauteur ou une profondeur non mesurée n'est pas contrôlée.
 */
@Service
public class ShelfSpaceService {

    private final StorageLocationRepository locations;
    private final PhysicalContainerRepository containers;
    private final BoxFormatCatalog catalog;
    private final int defaultTabletteMm;
    private final int marginPercent;

    public ShelfSpaceService(
        StorageLocationRepository locations,
        PhysicalContainerRepository containers,
        BoxFormatCatalog catalog,
        @Value("${jokkorm.physical.default-tablette-length-mm:1000}") int defaultTabletteMm,
        @Value("${jokkorm.physical.handling-margin-percent:5}") int marginPercent) {
        this.locations = locations;
        this.containers = containers;
        this.catalog = catalog;
        this.defaultTabletteMm = Math.max(StorageLocation.MIN_LENGTH_MM, defaultTabletteMm);
        this.marginPercent = Math.max(0, Math.min(50, marginPercent));
    }

    public int defaultTabletteMm() { return defaultTabletteMm; }

    public int marginPercent() { return marginPercent; }

    /** Une boîte détruite n'occupe plus de place (garde-fou pour les données anciennes). */
    public static boolean occupiesSpace(PhysicalContainer c) {
        return c.state() != ContainerState.DESTROYED;
    }

    /** État d'une tablette au moment d'un rangement ou d'un réglage. */
    public record TabletteSpace(StorageLocation tablette, List<StorageLocation> positions,
                                List<PhysicalContainer> boxes, BoxFormatCatalog.Spines spines,
                                int lengthMm, int fillableMm, Integer clearanceMm, Integer depthMm) {

        /**
         * Pourquoi cette boîte ne tient pas en hauteur ou en profondeur, ou vide si elle tient
         * (ou si la tablette, ou le format de la boîte, n'est pas mesuré).
         */
        public java.util.Optional<String> sizeProblem(PhysicalContainer box) {
            var format = spines.formatOf(box);
            if (format.isEmpty()) return java.util.Optional.empty();
            var f = format.get();
            String where = tablette == null ? "cet emplacement" : "la tablette " + tablette.path();
            if (clearanceMm != null && f.heightMm() > clearanceMm) {
                return java.util.Optional.of("La boîte %s (%s) fait %s de haut : %s n'a que %s de hauteur libre"
                    .formatted(box.barcode(), f.label(), LinearCapacity.cm(f.heightMm()), where, LinearCapacity.cm(clearanceMm)));
            }
            if (depthMm != null && f.depthMm() > depthMm) {
                return java.util.Optional.of("La boîte %s (%s) fait %s de profondeur : %s n'a que %s de profondeur"
                    .formatted(box.barcode(), f.label(), LinearCapacity.cm(f.depthMm()), where, LinearCapacity.cm(depthMm)));
            }
            return java.util.Optional.empty();
        }

        public int usedMm() {
            return boxes.stream().mapToInt(spines::of).sum();
        }

        public int usedMm(UUID positionId) {
            return boxes.stream().filter(b -> b.positionId().filter(positionId::equals).isPresent())
                .mapToInt(spines::of).sum();
        }

        public int positionLengthMm(StorageLocation position) {
            return LinearCapacity.positionLengthMm(position, lengthMm, positions.size());
        }
    }

    /**
     * Lit l'état de la tablette qui porte {@code position}. Avec {@code lock}, la ligne de la
     * tablette est verrouillée jusqu'à la fin de la transaction : deux scans simultanés sur
     * le dernier espace libre ne peuvent pas passer tous les deux.
     */
    public TabletteSpace tabletteOf(TenantId tenant, StorageLocation position, boolean lock,
                                    UUID excludeContainerId) {
        UUID parentId = position.parentId().orElse(null);
        StorageLocation tablette = parentId == null ? null
            : (lock ? locations.lockById(tenant, parentId) : locations.findById(tenant, parentId)).orElse(null);
        return build(tenant, tablette, tablette == null ? List.of(position) : null, excludeContainerId);
    }

    /** État d'une tablette donnée (lecture, sans verrou). */
    public TabletteSpace tablette(TenantId tenant, StorageLocation tablette) {
        return build(tenant, tablette, null, null);
    }

    private TabletteSpace build(TenantId tenant, StorageLocation tablette, List<StorageLocation> orphanPositions,
                                UUID excludeContainerId) {
        StorageLocation travee = tablette == null ? null
            : tablette.parentId().flatMap(id -> locations.findById(tenant, id)).orElse(null);

        List<StorageLocation> positions = orphanPositions != null ? orphanPositions
            : locations.findChildren(tenant, tablette.id()).stream()
                .filter(l -> l.kind() == StorageLocationKind.POSITION).toList();
        List<PhysicalContainer> boxes = containers
            .findByPositions(tenant, positions.stream().map(StorageLocation::id).toList()).stream()
            .filter(ShelfSpaceService::occupiesSpace)
            .filter(c -> excludeContainerId == null || !c.id().equals(excludeContainerId))
            .toList();

        int length = LinearCapacity.tabletteLengthMm(tablette, travee, defaultTabletteMm);
        return new TabletteSpace(tablette, positions, boxes, catalog.spines(tenant), length,
            LinearCapacity.fillableMm(length, marginPercent),
            effective(tablette, travee, true), effective(tablette, travee, false));
    }

    /**
     * Ce qui ne tiendrait plus si {@code node} (travée, tablette ou emplacement) recevait cette
     * longueur et ces dimensions. Lecture seule : l'import Excel (5T.C) vérifie ainsi une ligne
     * avant d'écrire quoi que ce soit, pour qu'une ligne refusée ne laisse aucune trace.
     */
    public List<String> problemsIf(TenantId tenant, StorageLocation node, Integer newLengthMm,
                                   com.jokkorm.domain.physical.PhysicalSpec newSpec) {
        var spec = newSpec == null ? com.jokkorm.domain.physical.PhysicalSpec.EMPTY : newSpec;
        switch (node.kind()) {
            case RACK -> {
                StorageLocation travee = node.parentId().flatMap(id -> locations.findById(tenant, id)).orElse(null);
                return rackProblems(tenant, node, newLengthMm, spec,
                    travee == null ? null : travee.lengthMm().orElse(null),
                    travee == null ? com.jokkorm.domain.physical.PhysicalSpec.EMPTY : travee.spec());
            }
            case SHELF -> {
                List<String> all = new java.util.ArrayList<>();
                for (StorageLocation rack : locations.findChildren(tenant, node.id())) {
                    if (rack.kind() != StorageLocationKind.RACK) continue;
                    all.addAll(rackProblems(tenant, rack, rack.lengthMm().orElse(null), rack.spec(), newLengthMm, spec));
                }
                return all;
            }
            case POSITION -> {
                if (newLengthMm == null) return List.of();
                TabletteSpace t = tabletteOf(tenant, node, false, null);
                List<String> out = new java.util.ArrayList<>();
                if (t.usedMm(node.id()) > newLengthMm) {
                    out.add("Emplacement %s : les boîtes rangées occupent %s, plus que %s"
                        .formatted(node.path(), LinearCapacity.cm(t.usedMm(node.id())), LinearCapacity.cm(newLengthMm)));
                }
                int explicit = t.positions().stream()
                    .mapToInt(p -> p.id().equals(node.id()) ? newLengthMm : p.lengthMm().orElse(0)).sum();
                if (explicit > t.lengthMm()) {
                    out.add("Tablette %s : ses emplacements totaliseraient %s pour %s"
                        .formatted(t.tablette() == null ? "?" : t.tablette().path(), LinearCapacity.cm(explicit),
                            LinearCapacity.cm(t.lengthMm())));
                }
                return out;
            }
            default -> {
                return List.of();
            }
        }
    }

    private List<String> rackProblems(TenantId tenant, StorageLocation rack, Integer rackLength,
                                      com.jokkorm.domain.physical.PhysicalSpec rackSpec, Integer traveeLength,
                                      com.jokkorm.domain.physical.PhysicalSpec traveeSpec) {
        TabletteSpace base = tablette(tenant, rack);
        int length = rackLength != null ? rackLength : traveeLength != null ? traveeLength : defaultTabletteMm;
        Integer clearance = rackSpec.clearanceMm() != null ? rackSpec.clearanceMm() : traveeSpec.clearanceMm();
        Integer depth = rackSpec.depthMm() != null ? rackSpec.depthMm() : traveeSpec.depthMm();
        TabletteSpace t = new TabletteSpace(rack, base.positions(), base.boxes(), base.spines(), length,
            LinearCapacity.fillableMm(length, marginPercent), clearance, depth);
        List<String> out = new java.util.ArrayList<>();
        t.boxes().stream().map(t::sizeProblem).flatMap(java.util.Optional::stream).findFirst().ifPresent(out::add);
        int explicit = t.positions().stream().mapToInt(p -> p.lengthMm().orElse(0)).sum();
        if (explicit > length) {
            out.add("Tablette %s : ses emplacements totaliseraient %s pour %s"
                .formatted(rack.path(), LinearCapacity.cm(explicit), LinearCapacity.cm(length)));
        }
        if (t.usedMm() > length) {
            out.add("Tablette %s : les boîtes rangées occupent déjà %s, plus que %s"
                .formatted(rack.path(), LinearCapacity.cm(t.usedMm()), LinearCapacity.cm(length)));
        }
        return out;
    }

    /** Hauteur libre ({@code clearance}) ou profondeur : celle de la tablette, sinon de sa travée. */
    static Integer effective(StorageLocation tablette, StorageLocation travee, boolean clearance) {
        Integer own = tablette == null ? null
            : clearance ? tablette.spec().clearanceMm() : tablette.spec().depthMm();
        if (own != null) return own;
        return travee == null ? null
            : clearance ? travee.spec().clearanceMm() : travee.spec().depthMm();
    }

    /**
     * Refuse le rangement si la boîte ne tient pas : longueur restante de l'emplacement,
     * puis marge de manipulation de la tablette. Messages chiffrés, en centimètres.
     */
    public void requireRoomFor(TenantId tenant, StorageLocation position, PhysicalContainer box) {
        TabletteSpace space = tabletteOf(tenant, position, true, box.id());
        var tooBig = space.sizeProblem(box);
        if (tooBig.isPresent()) {
            throw new PhysicalContainerConflictException(tooBig.get());
        }
        int spine = space.spines().of(box);

        int posFree = space.positionLengthMm(position) - space.usedMm(position.id());
        if (spine > posFree) {
            throw new PhysicalContainerConflictException(
                "Emplacement %s plein : il reste %s, la boîte %s fait %s de dos".formatted(
                    position.path(), LinearCapacity.cm(Math.max(0, posFree)), box.barcode(),
                    LinearCapacity.cm(spine)));
        }
        int rackFree = space.fillableMm() - space.usedMm();
        if (spine > rackFree) {
            throw new PhysicalContainerConflictException(
                ("Tablette %s : la marge de manipulation (%d %%) doit rester libre. "
                    + "Il reste %s rangeables, la boîte %s fait %s de dos").formatted(
                    space.tablette() == null ? position.path() : space.tablette().path(), marginPercent,
                    LinearCapacity.cm(Math.max(0, rackFree)), box.barcode(), LinearCapacity.cm(spine)));
        }
    }
}
