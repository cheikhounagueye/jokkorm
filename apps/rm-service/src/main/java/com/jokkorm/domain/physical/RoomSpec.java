package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.util.Objects;
import java.util.UUID;

/**
 * Dimensions réelles et limites d'une salle (magasin, STOREHOUSE) — pattern 10 § 2.4.
 * Tout est facultatif. Les consignes climatiques ne sont pas codées en dur : elles se
 * saisissent par salle, d'après l'ISO 11799 et les recommandations nationales.
 *
 * @param lengthMm        longueur de la salle (mm)
 * @param widthMm         largeur (mm)
 * @param ceilingHeightMm hauteur sous plafond (mm)
 * @param usableAreaM2    surface utile (m²) ; à défaut, longueur × largeur
 * @param floorLoadKgM2   charge au sol admissible (kg/m²)
 * @param tempMinC        consigne de température basse (°C)
 * @param tempMaxC        consigne de température haute (°C)
 * @param rhMinPct        consigne d'humidité relative basse (%)
 * @param rhMaxPct        consigne d'humidité relative haute (%)
 * @param avgWeightKgPerMl poids moyen au mètre linéaire (kg/ml), mesuré sur place ; sert à la
 *                         charge au sol estimée (indicative, à faire confirmer par un bureau d'études)
 */
public record RoomSpec(
    UUID storehouseId,
    TenantId tenant,
    Integer lengthMm,
    Integer widthMm,
    Integer ceilingHeightMm,
    Double usableAreaM2,
    Integer floorLoadKgM2,
    Double tempMinC,
    Double tempMaxC,
    Integer rhMinPct,
    Integer rhMaxPct,
    Double avgWeightKgPerMl) {

    public RoomSpec {
        Objects.requireNonNull(storehouseId, "storehouseId");
        Objects.requireNonNull(tenant, "tenant");
        range(lengthMm, 1_000, 500_000, "longueur de la salle (mm)");
        range(widthMm, 1_000, 500_000, "largeur de la salle (mm)");
        range(ceilingHeightMm, 1_500, 30_000, "hauteur sous plafond (mm)");
        if (usableAreaM2 != null && (usableAreaM2 <= 0 || usableAreaM2 > 250_000)) {
            throw new IllegalArgumentException("surface utile : entre 0 et 250 000 m², reçu " + usableAreaM2);
        }
        range(floorLoadKgM2, 50, 10_000, "charge au sol (kg/m²)");
        if (tempMinC != null && (tempMinC < -30 || tempMinC > 60)) throw new IllegalArgumentException("température basse hors bornes");
        if (tempMaxC != null && (tempMaxC < -30 || tempMaxC > 60)) throw new IllegalArgumentException("température haute hors bornes");
        if (tempMinC != null && tempMaxC != null && tempMinC > tempMaxC) {
            throw new IllegalArgumentException("La température basse dépasse la température haute");
        }
        range(rhMinPct, 0, 100, "humidité relative basse (%)");
        range(rhMaxPct, 0, 100, "humidité relative haute (%)");
        if (avgWeightKgPerMl != null && (avgWeightKgPerMl <= 0 || avgWeightKgPerMl > 1000)) {
            throw new IllegalArgumentException("poids moyen au ml : entre 0 et 1000 kg, reçu " + avgWeightKgPerMl);
        }
        if (rhMinPct != null && rhMaxPct != null && rhMinPct > rhMaxPct) {
            throw new IllegalArgumentException("L'humidité basse dépasse l'humidité haute");
        }
    }

    /** Surface utile saisie, sinon longueur × largeur, sinon inconnue. */
    public Double effectiveAreaM2() {
        if (usableAreaM2 != null) return usableAreaM2;
        if (lengthMm != null && widthMm != null) return lengthMm / 1000.0 * widthMm / 1000.0;
        return null;
    }

    private static void range(Integer v, int min, int max, String name) {
        if (v != null && (v < min || v > max)) {
            throw new IllegalArgumentException("%s : entre %d et %d, reçu %d".formatted(name, min, max, v));
        }
    }
}
