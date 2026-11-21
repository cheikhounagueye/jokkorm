package com.jokkorm.domain.master;

/**
 * Adresse physique sur cinq niveaux : site → magasin → travée → tablette → position.
 * Renvoyée au maître pour affichage, jamais modifiée côté maître.
 *
 * <p>Tous les champs sont non-null — on ne range pas une boîte « quelque part ».
 */
public record PhysicalLocation(
    String site, String magasin, String travee, String tablette, String position) {

    public PhysicalLocation {
        requireNonBlank(site, "site");
        requireNonBlank(magasin, "magasin");
        requireNonBlank(travee, "travee");
        requireNonBlank(tablette, "tablette");
        requireNonBlank(position, "position");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    public String toSingleLine() {
        return "%s / %s / %s / %s / %s".formatted(site, magasin, travee, tablette, position);
    }
}
