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
 * de rangement : longueur de l'emplacement, marge de manipulation de la tablette.
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
                                int lengthMm, int fillableMm) {

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
            LinearCapacity.fillableMm(length, marginPercent));
    }

    /**
     * Refuse le rangement si la boîte ne tient pas : longueur restante de l'emplacement,
     * puis marge de manipulation de la tablette. Messages chiffrés, en centimètres.
     */
    public void requireRoomFor(TenantId tenant, StorageLocation position, PhysicalContainer box) {
        TabletteSpace space = tabletteOf(tenant, position, true, box.id());
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
