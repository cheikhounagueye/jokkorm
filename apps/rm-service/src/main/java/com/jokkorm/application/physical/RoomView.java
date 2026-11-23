package com.jokkorm.application.physical;

import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.RoomEquipment;
import com.jokkorm.domain.physical.StorageLocation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Lecture complète d'un magasin pour la visite 3D : structure travée → tablette →
 * position, boîtes rangées, prêts en cours, équipements et occupation à chaque niveau,
 * mesurée en longueur (pattern 10).
 */
public record RoomView(
    StorageLocation storehouse,
    List<Shelf> shelves,
    List<RoomEquipment> equipment,
    Occupancy occupancy,
    LocalDate today,
    int defaultSpineMm,
    int marginPercent) {

    public record Shelf(StorageLocation shelf, List<Rack> racks, Occupancy occupancy) {}

    public record Rack(StorageLocation rack, List<Position> positions, Occupancy occupancy) {}

    public record Position(StorageLocation position, List<Box> boxes, Occupancy occupancy) {}

    public record Box(PhysicalContainer container, int spineMm, String formatCode,
                      boolean onLoan, boolean overdue, Instant expectedReturnAt) {}

    /**
     * Occupation en longueur.
     *
     * @param boxes        boîtes présentes (une boîte en prêt garde sa place : fiche fantôme)
     * @param usedMm       somme des dos
     * @param lengthMm     longueur utile
     * @param placeableMm  longueur où l'on peut encore ranger, marge de la tablette déduite
     * @param places       équivalent en boîtes du format par défaut (longueur ÷ dos par défaut)
     */
    public record Occupancy(int boxes, int usedMm, int lengthMm, int placeableMm, int places) {
        public static final Occupancy EMPTY = new Occupancy(0, 0, 0, 0, 0);

        public Occupancy plus(Occupancy other) {
            return new Occupancy(boxes + other.boxes, usedMm + other.usedMm, lengthMm + other.lengthMm,
                placeableMm + other.placeableMm, places + other.places);
        }

        public double rate() {
            return lengthMm == 0 ? 0.0 : Math.min(1.0, (double) usedMm / lengthMm);
        }
    }
}
