package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.loan.PhysicalLoan;
import com.jokkorm.domain.loan.PhysicalLoanRepository;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.RoomEquipmentRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assemble la vue d'un magasin en un nombre de requêtes borné : la structure via les
 * enfants de chaque niveau, puis toutes les boîtes en une requête, puis les prêts actifs.
 */
@Service
public class GetRoomViewUseCase {

    /** Tri « naturel » des codes : 2 avant 10, T02 avant T10. */
    static final Comparator<StorageLocation> NATURAL_CODE_ORDER =
        Comparator.comparing(StorageLocation::code, GetRoomViewUseCase::compareNatural);

    private final StorageLocationRepository locations;
    private final PhysicalContainerRepository containers;
    private final PhysicalLoanRepository loans;
    private final RoomEquipmentRepository equipment;
    private final Clock clock;

    public GetRoomViewUseCase(
        StorageLocationRepository locations,
        PhysicalContainerRepository containers,
        PhysicalLoanRepository loans,
        RoomEquipmentRepository equipment,
        Clock clock) {
        this.locations = locations;
        this.containers = containers;
        this.loans = loans;
        this.equipment = equipment;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RoomView execute(UUID storehouseId) {
        TenantId tenant = TenantContext.current();
        StorageLocation room = locations.findById(tenant, storehouseId)
            .orElseThrow(() -> new StorageLocationNotFoundException(
                "Storage location %s not found (tenant=%s)".formatted(storehouseId, tenant.value())));
        if (room.kind() != StorageLocationKind.STOREHOUSE) {
            throw new IllegalArgumentException(
                "A room view needs a STOREHOUSE, got %s (path=%s)".formatted(room.kind(), room.path()));
        }

        // Structure : travées → tablettes → positions
        List<StorageLocation> shelves = sorted(locations.findChildren(tenant, room.id()));
        Map<UUID, List<StorageLocation>> racksByShelf = new HashMap<>();
        Map<UUID, List<StorageLocation>> positionsByRack = new HashMap<>();
        List<UUID> positionIds = new ArrayList<>();
        for (StorageLocation shelf : shelves) {
            List<StorageLocation> racks = sorted(locations.findChildren(tenant, shelf.id()));
            racksByShelf.put(shelf.id(), racks);
            for (StorageLocation rack : racks) {
                List<StorageLocation> positions = sorted(locations.findChildren(tenant, rack.id()));
                positionsByRack.put(rack.id(), positions);
                positions.forEach(p -> positionIds.add(p.id()));
            }
        }

        Map<UUID, List<PhysicalContainer>> boxesByPosition = containers.findByPositions(tenant, positionIds)
            .stream()
            .collect(Collectors.groupingBy(c -> c.positionId().orElseThrow()));

        Instant now = clock.instant();
        Map<UUID, PhysicalLoan> activeLoanByContainer = loans.findActive(tenant).stream()
            .collect(Collectors.toMap(PhysicalLoan::containerId, Function.identity(), (a, b) -> a));

        List<RoomView.Shelf> shelfViews = new ArrayList<>();
        RoomView.Occupancy roomOcc = RoomView.Occupancy.EMPTY;
        for (StorageLocation shelf : shelves) {
            List<RoomView.Rack> rackViews = new ArrayList<>();
            RoomView.Occupancy shelfOcc = RoomView.Occupancy.EMPTY;
            for (StorageLocation rack : racksByShelf.get(shelf.id())) {
                List<RoomView.Position> positionViews = new ArrayList<>();
                RoomView.Occupancy rackOcc = RoomView.Occupancy.EMPTY;
                for (StorageLocation position : positionsByRack.get(rack.id())) {
                    List<RoomView.Box> boxes = boxesByPosition.getOrDefault(position.id(), List.of()).stream()
                        .map(c -> {
                            PhysicalLoan loan = activeLoanByContainer.get(c.id());
                            return new RoomView.Box(c, loan != null, loan != null && loan.isOverdue(now),
                                loan == null ? null : loan.expectedReturnAt());
                        })
                        .toList();
                    RoomView.Occupancy posOcc = new RoomView.Occupancy(boxes.size(), position.effectiveCapacity());
                    positionViews.add(new RoomView.Position(position, boxes, posOcc));
                    rackOcc = rackOcc.plus(posOcc);
                }
                rackViews.add(new RoomView.Rack(rack, positionViews, rackOcc));
                shelfOcc = shelfOcc.plus(rackOcc);
            }
            shelfViews.add(new RoomView.Shelf(shelf, rackViews, shelfOcc));
            roomOcc = roomOcc.plus(shelfOcc);
        }

        return new RoomView(room, shelfViews, equipment.findByStorehouse(tenant, room.id()), roomOcc,
            LocalDate.ofInstant(now, clock.getZone()));
    }

    private static List<StorageLocation> sorted(List<StorageLocation> nodes) {
        return nodes.stream().sorted(NATURAL_CODE_ORDER).toList();
    }

    static int compareNatural(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                long na = Long.parseLong(a.substring(si, Math.min(i, si + 18)));
                long nb = Long.parseLong(b.substring(sj, Math.min(j, sj + 18)));
                if (na != nb) return Long.compare(na, nb);
            } else {
                if (ca != cb) return Character.compare(ca, cb);
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }
}
