package com.jokkorm.domain.physical;

/**
 * Calcul de capacité en longueur (pattern 10), sans dépendance : la longueur utile est la
 * seule vérité, le nombre de boîtes s'en déduit selon le dos de chaque format.
 *
 * <p>La marge de manipulation s'applique une fois, à la tablette : un emplacement se
 * remplit jusqu'à sa longueur, mais la tablette garde toujours sa marge libre pour qu'on
 * puisse sortir une boîte. Un emplacement migré de la phase 5S (6 boîtes = 600 mm) garde
 * donc ses 6 places.
 */
public final class LinearCapacity {

    private LinearCapacity() {}

    /** Longueur d'une tablette : saisie sur la tablette → longueur de la travée → défaut. */
    public static int tabletteLengthMm(StorageLocation tablette, StorageLocation travee, int defaultMm) {
        if (tablette != null && tablette.lengthMm().isPresent()) return tablette.lengthMm().get();
        if (travee != null && travee.lengthMm().isPresent()) return travee.lengthMm().get();
        return defaultMm;
    }

    /** Longueur d'un emplacement : saisie → part égale de la tablette. */
    public static int positionLengthMm(StorageLocation position, int tabletteMm, int positionsInTablette) {
        if (position.lengthMm().isPresent()) return position.lengthMm().get();
        return tabletteMm / Math.max(1, positionsInTablette);
    }

    /** Longueur remplissable d'une tablette, marge de manipulation déduite. */
    public static int fillableMm(int tabletteMm, int marginPercent) {
        int margin = Math.max(0, Math.min(50, marginPercent));
        return tabletteMm * (100 - margin) / 100;
    }

    /** Nombre de boîtes d'un dos donné qui tiennent dans une longueur libre. */
    public static int boxesThatFit(int freeMm, int spineMm) {
        if (spineMm <= 0) return 0;
        return Math.max(0, freeMm / spineMm);
    }

    /** Affichage en centimètres : 180 → « 18 cm », 45 → « 4,5 cm ». */
    public static String cm(int mm) {
        return mm % 10 == 0 ? (mm / 10) + " cm" : "%d,%d cm".formatted(mm / 10, Math.abs(mm % 10));
    }
}
