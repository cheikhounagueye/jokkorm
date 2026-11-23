package com.jokkorm.domain.physical;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Le mobilier tient-il dans la salle ? (pattern 10 § 2.4). Ne bloque jamais : produit des
 * avertissements, et une emprise <b>estimée</b> (longueur × profondeur des travées mesurées,
 * sans les allées).
 */
public final class RoomLimits {

    /**
     * @param areaM2           surface utile (saisie ou longueur × largeur), null si inconnue
     * @param footprintM2      emprise estimée des travées dont la profondeur est connue
     * @param measuredShelves  travées avec profondeur
     * @param shelves          toutes les travées de la salle
     * @param warnings         avertissements lisibles, du plus grave au moins grave
     */
    public record Report(Double areaM2, double footprintM2, int measuredShelves, int shelves, List<String> warnings) {}

    private RoomLimits() {}

    /**
     * @param spec     caractéristiques de la salle (peut être null : rien n'est mesuré)
     * @param shelves  travées (SHELF) de la salle
     * @param lengthOf longueur effective d'une travée en mm
     */
    public static Report check(RoomSpec spec, List<StorageLocation> shelves, ToIntFunction<StorageLocation> lengthOf) {
        List<String> warnings = new ArrayList<>();
        double footprint = 0;
        int measured = 0;
        for (StorageLocation s : shelves) {
            Integer depth = s.spec().depthMm();
            if (depth != null) {
                footprint += lengthOf.applyAsInt(s) / 1000.0 * depth / 1000.0;
                measured++;
            }
        }
        Double area = spec == null ? null : spec.effectiveAreaM2();

        if (area != null && footprint > area) {
            warnings.add("L'emprise estimée du mobilier (%s m²) dépasse la surface utile de la salle (%s m²)"
                .formatted(fmt(footprint), fmt(area)));
        }

        if (spec != null && spec.ceilingHeightMm() != null) {
            for (StorageLocation s : shelves) {
                Integer h = s.spec().heightMm();
                if (h != null && h > spec.ceilingHeightMm()) {
                    warnings.add("Travée %s : %s m de haut pour %s m sous plafond"
                        .formatted(s.code(), fmt(h / 1000.0), fmt(spec.ceilingHeightMm() / 1000.0)));
                }
            }
        }

        // Longueur de chaque épi : la plus longue de ses faces.
        Map<String, Map<String, Integer>> aisles = new LinkedHashMap<>();
        Map<String, List<String>> slots = new HashMap<>();
        for (StorageLocation s : shelves) {
            PhysicalSpec p = s.spec();
            if (p.aisleCode() == null) continue;
            aisles.computeIfAbsent(p.aisleCode(), k -> new HashMap<>())
                .merge(Objects.toString(p.aisleSide(), "-"), lengthOf.applyAsInt(s), Integer::sum);
            if (p.aisleOrder() != null) {
                String slot = p.aisleCode() + "|" + p.aisleOrder() + "|" + Objects.toString(p.aisleSide(), "-");
                slots.computeIfAbsent(slot, k -> new ArrayList<>()).add(s.code());
            }
        }
        if (spec != null && spec.lengthMm() != null && spec.widthMm() != null) {
            int longest = Math.max(spec.lengthMm(), spec.widthMm());
            aisles.forEach((code, sides) -> {
                int len = sides.values().stream().max(Comparator.naturalOrder()).orElse(0);
                if (len > longest) {
                    warnings.add("Épi %s : %s m de long pour une salle de %s × %s m"
                        .formatted(code, fmt(len / 1000.0), fmt(spec.lengthMm() / 1000.0), fmt(spec.widthMm() / 1000.0)));
                }
            });
        }
        slots.forEach((slot, codes) -> {
            if (codes.size() > 1) {
                String[] parts = slot.split("\\|");
                warnings.add("Épi %s, rang %s%s : plusieurs travées au même endroit (%s)".formatted(
                    parts[0], parts[1], parts[2].equals("-") ? "" : ", face " + parts[2],
                    String.join(", ", codes.stream().sorted().toList())));
            }
        });

        return new Report(area, footprint, measured, shelves.size(), List.copyOf(warnings));
    }

    private static String fmt(double v) {
        return String.format(Locale.FRANCE, v >= 10 ? "%.1f" : "%.2f", v);
    }
}
