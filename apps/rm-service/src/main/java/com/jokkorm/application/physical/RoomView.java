package com.jokkorm.application.physical;

import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.RoomEquipment;
import com.jokkorm.domain.physical.StorageLocation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lecture complète d'un magasin pour la visite 3D : structure travée → tablette →
 * position, boîtes rangées, prêts en cours, équipements et taux d'occupation à chaque niveau.
 */
public record RoomView(
    StorageLocation storehouse,
    List<Shelf> shelves,
    List<RoomEquipment> equipment,
    Occupancy occupancy,
    LocalDate today) {

    public record Shelf(StorageLocation shelf, List<Rack> racks, Occupancy occupancy) {}

    public record Rack(StorageLocation rack, List<Position> positions, Occupancy occupancy) {}

    public record Position(StorageLocation position, List<Box> boxes, Occupancy occupancy) {}

    public record Box(PhysicalContainer container, boolean onLoan, boolean overdue, Instant expectedReturnAt) {}

    /** Boîtes rangées / capacité totale. */
    public record Occupancy(int boxes, int capacity) {
        public static final Occupancy EMPTY = new Occupancy(0, 0);

        public Occupancy plus(Occupancy other) {
            return new Occupancy(boxes + other.boxes, capacity + other.capacity);
        }

        public double rate() {
            return capacity == 0 ? 0.0 : Math.min(1.0, (double) boxes / capacity);
        }
    }
}
