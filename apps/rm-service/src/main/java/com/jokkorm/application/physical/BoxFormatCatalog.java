package com.jokkorm.application.physical;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.BoxFormatRepository;
import com.jokkorm.domain.physical.PhysicalContainer;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue de formats de boîtes d'un tenant (pattern 10).
 *
 * <p>Lecture : un tenant sans catalogue (créé après la migration 0013) retombe sur un dos
 * standard de 100 mm, jamais sur 0. Écriture : le catalogue standard est installé à la
 * première création de boîte, pour que chaque boîte référence un vrai format.
 */
@Service
public class BoxFormatCatalog {

    /** Dos utilisé quand une boîte n'a pas de format et que le tenant n'a pas de défaut. */
    public static final int FALLBACK_SPINE_MM = 100;

    /** Catalogue proposé à l'installation : boîtes 25 × 33 cm, dos 8, 10, 15 et 20 cm. */
    static final List<Object[]> STANDARD = List.of(
        new Object[] {"BOITE-25X33-D08", "Boîte 25 × 33 dos 8 cm", 80},
        new Object[] {"BOITE-25X33-D10", "Boîte 25 × 33 dos 10 cm", 100},
        new Object[] {"BOITE-25X33-D15", "Boîte 25 × 33 dos 15 cm", 150},
        new Object[] {"BOITE-25X33-D20", "Boîte 25 × 33 dos 20 cm", 200});

    private final BoxFormatRepository formats;
    private final Clock clock;

    public BoxFormatCatalog(BoxFormatRepository formats, Clock clock) {
        this.formats = formats;
        this.clock = clock;
    }

    /** Vue figée du catalogue, pour calculer beaucoup de dos sans requête par boîte. */
    public Spines spines(TenantId tenant) {
        List<BoxFormat> all = formats.findAll(tenant);
        int defaultSpine = all.stream().filter(BoxFormat::isDefault).findFirst()
            .map(BoxFormat::spineMm).orElse(FALLBACK_SPINE_MM);
        return new Spines(all.stream().collect(Collectors.toMap(BoxFormat::id, Function.identity())),
            defaultSpine);
    }

    /**
     * Format à attribuer à une nouvelle boîte : celui demandé (actif, du tenant) ou le défaut.
     * Installe le catalogue standard si le tenant n'en a pas encore.
     */
    @Transactional
    public BoxFormat resolveForNewBox(TenantId tenant, UUID requested) {
        if (requested != null) {
            BoxFormat f = formats.findById(tenant, requested)
                .orElseThrow(() -> new IllegalArgumentException("Format de boîte inconnu : " + requested));
            if (!f.active()) throw new IllegalArgumentException("Format retiré : " + f.code());
            return f;
        }
        return defaultFormat(tenant);
    }

    @Transactional
    public BoxFormat defaultFormat(TenantId tenant) {
        List<BoxFormat> all = formats.findAll(tenant);
        if (all.isEmpty()) all = installStandard(tenant);
        return all.stream().filter(BoxFormat::isDefault).findFirst()
            .orElseGet(() -> formats.findAll(tenant).stream().filter(BoxFormat::active).findFirst()
                .orElseThrow(() -> new IllegalStateException("Aucun format de boîte actif pour " + tenant.value())));
    }

    private List<BoxFormat> installStandard(TenantId tenant) {
        var now = clock.instant();
        for (Object[] s : STANDARD) {
            BoxFormat f = BoxFormat.create(tenant, (String) s[0], (String) s[1], (Integer) s[2], 250, 330,
                BoxFormat.Quality.STANDARD, null, now);
            if ((Integer) s[2] == FALLBACK_SPINE_MM) f.setDefault(true, now);
            formats.save(f);
        }
        return formats.findAll(tenant);
    }

    /** Dos des formats du tenant, avec le dos par défaut en repli. */
    public record Spines(Map<UUID, BoxFormat> byId, int defaultSpineMm) {

        public int of(PhysicalContainer c) {
            return c.boxFormatId().map(byId::get).map(BoxFormat::spineMm).orElse(defaultSpineMm);
        }

        public Optional<BoxFormat> formatOf(PhysicalContainer c) {
            return c.boxFormatId().map(byId::get);
        }
    }
}
