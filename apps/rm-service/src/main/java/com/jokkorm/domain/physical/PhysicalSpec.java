package com.jokkorm.domain.physical;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Mobilier et dimensions d'une travée ou d'une tablette (pattern 10 § 2.1 et 2.3).
 * Tout est facultatif : {@code null} veut dire « pas mesuré », et une dimension non mesurée
 * n'est jamais contrôlée (aucune valeur n'est inventée).
 *
 * <ul>
 *   <li><b>Travée (SHELF)</b> : type de mobilier, profondeur et hauteur libre par défaut de
 *       ses tablettes, hauteur hors tout (pour le plafond), simple ou double face, épi.</li>
 *   <li><b>Tablette (RACK)</b> : profondeur et hauteur libre propres, qui remplacent celles
 *       de la travée (une tablette basse plus haute…).</li>
 * </ul>
 *
 * <p>L'épi (rangée) n'est pas un niveau de l'arbre : c'est un attribut de la travée (code,
 * ordre dans l'épi, face A ou B). Les chemins et les codes-barres ne changent pas.
 *
 * @param furnitureType type de mobilier (travée)
 * @param depthMm       profondeur utile (mm)
 * @param clearanceMm   hauteur libre au-dessus d'une tablette (mm) : la boîte doit y tenir
 * @param heightMm      hauteur hors tout du meuble (mm, travée) : comparée au plafond de la salle
 * @param faces         1 = simple face, 2 = double face (travée)
 * @param aisleCode     code de l'épi (travée)
 * @param aisleOrder    rang de la travée dans son épi (travée)
 * @param aisleSide     face de l'épi, A ou B (travée)
 */
public record PhysicalSpec(
    FurnitureType furnitureType,
    Integer depthMm,
    Integer clearanceMm,
    Integer heightMm,
    Integer faces,
    String aisleCode,
    Integer aisleOrder,
    String aisleSide) {

    public static final PhysicalSpec EMPTY = new PhysicalSpec(null, null, null, null, null, null, null, null);

    private static final Pattern AISLE = Pattern.compile("^[A-Z0-9][A-Z0-9_\\-]{0,15}$");

    public PhysicalSpec {
        depthMm = range(depthMm, 100, 3_000, "profondeur");
        clearanceMm = range(clearanceMm, 50, 3_000, "hauteur libre");
        heightMm = range(heightMm, 300, 8_000, "hauteur du meuble");
        if (faces != null && faces != 1 && faces != 2) {
            throw new IllegalArgumentException("faces : 1 (simple face) ou 2 (double face), reçu " + faces);
        }
        aisleCode = blankToNull(aisleCode);
        if (aisleCode != null) {
            aisleCode = aisleCode.toUpperCase(Locale.ROOT);
            if (!AISLE.matcher(aisleCode).matches()) {
                throw new IllegalArgumentException("code d'épi invalide : " + aisleCode);
            }
        }
        if (aisleOrder != null && (aisleOrder < 1 || aisleOrder > 999)) {
            throw new IllegalArgumentException("rang dans l'épi : entre 1 et 999, reçu " + aisleOrder);
        }
        aisleSide = blankToNull(aisleSide);
        if (aisleSide != null) {
            aisleSide = aisleSide.toUpperCase(Locale.ROOT);
            if (!aisleSide.equals("A") && !aisleSide.equals("B")) {
                throw new IllegalArgumentException("face d'épi : A ou B, reçu " + aisleSide);
            }
        }
    }

    /** Vérifie que seuls les champs permis pour ce niveau sont renseignés. */
    public PhysicalSpec requireAllowedFor(StorageLocationKind kind) {
        boolean shelfOnly = furnitureType != null || heightMm != null || faces != null
            || aisleCode != null || aisleOrder != null || aisleSide != null;
        boolean tabletteFields = depthMm != null || clearanceMm != null;
        switch (kind) {
            case SHELF -> { }
            case RACK -> {
                if (shelfOnly) {
                    throw new IllegalArgumentException(
                        "Une tablette ne porte que sa profondeur et sa hauteur libre ; le mobilier et l'épi se règlent sur la travée");
                }
            }
            default -> {
                if (shelfOnly || tabletteFields) {
                    throw new IllegalArgumentException("Un " + kind + " n'a pas de mobilier ni de dimensions de rangement");
                }
            }
        }
        return this;
    }

    public boolean isEmpty() {
        return equals(EMPTY);
    }

    private static Integer range(Integer v, int min, int max, String name) {
        if (v == null) return null;
        if (v < min || v > max) {
            throw new IllegalArgumentException("%s : entre %d et %d mm, reçu %d".formatted(name, min, max, v));
        }
        return v;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
