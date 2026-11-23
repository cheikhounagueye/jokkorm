package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.LinearCapacity;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Place restante d'un emplacement ou d'une tablette, et nombre de boîtes de chaque format
 * actif qui tiennent encore (« reste 46 cm = 4 boîtes dos 10 ou 3 dos 15 »). Mêmes règles
 * que le rangement : ce qui est annoncé ici est ce qui sera accepté.
 */
@Service
public class GetStorageSpaceUseCase {

    private final StorageLocationRepository locations;
    private final ShelfSpaceService space;
    private final BoxFormatCatalog catalog;

    public GetStorageSpaceUseCase(StorageLocationRepository locations, ShelfSpaceService space,
                                  BoxFormatCatalog catalog) {
        this.locations = locations;
        this.space = space;
        this.catalog = catalog;
    }

    public record Fit(UUID formatId, String code, String label, int spineMm, int count) {}

    public record Space(UUID locationId, StorageLocationKind kind, int lengthMm, int usedMm, int placeableMm,
                        int boxes, int marginPercent, List<Fit> fits) {}

    @Transactional(readOnly = true)
    public Space execute(UUID locationId) {
        TenantId tenant = TenantContext.current();
        StorageLocation node = locations.findById(tenant, locationId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(locationId, tenant.value())));

        int length;
        int used;
        int placeable;
        int boxes;
        switch (node.kind()) {
            case POSITION -> {
                var t = space.tabletteOf(tenant, node, false, null);
                length = t.positionLengthMm(node);
                used = t.usedMm(node.id());
                placeable = Math.max(0, Math.min(length - used, t.fillableMm() - t.usedMm()));
                boxes = (int) t.boxes().stream().filter(b -> b.positionId().filter(node.id()::equals).isPresent()).count();
            }
            case RACK -> {
                var t = space.tablette(tenant, node);
                length = t.lengthMm();
                used = t.usedMm();
                placeable = Math.max(0, t.fillableMm() - used);
                boxes = t.boxes().size();
            }
            default -> throw new IllegalArgumentException(
                "La place se mesure sur une tablette ou un emplacement, pas sur un " + node.kind());
        }

        List<Fit> fits = catalog.spines(tenant).byId().values().stream()
            .filter(BoxFormat::active)
            .sorted(Comparator.comparingInt(BoxFormat::spineMm))
            .map(f -> new Fit(f.id(), f.code(), f.label(), f.spineMm(),
                LinearCapacity.boxesThatFit(placeable, f.spineMm())))
            .toList();
        return new Space(node.id(), node.kind(), length, used, placeable, boxes, space.marginPercent(), fits);
    }
}
