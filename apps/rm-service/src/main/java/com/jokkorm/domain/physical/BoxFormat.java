package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Format de boîte du catalogue d'un tenant (pattern 10). Le dos est la longueur que la
 * boîte occupe sur une tablette ; la hauteur doit tenir dans la hauteur libre.
 *
 * <p>v1 : boîtes rangées debout uniquement. Le rangement à plat (registres, cartons de
 * transfert, liasses, plans) demande un modèle d'empilement, prévu en v2.
 */
public final class BoxFormat {

    public enum Orientation { UPRIGHT, FLAT }

    public enum Quality { CONSERVATION, STANDARD, POLYPROPYLENE }

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_\\-]{0,31}$");

    private final UUID id;
    private final TenantId tenantId;
    private final String code;
    private String label;
    private final int spineMm;
    private final int depthMm;
    private final int heightMm;
    private final Orientation orientation;
    private Quality quality;
    private Integer maxWeightKg;
    private boolean active;
    private boolean defaultFormat;
    private final Instant createdAt;
    private Instant updatedAt;

    private BoxFormat(UUID id, TenantId tenantId, String code, String label, int spineMm, int depthMm,
                      int heightMm, Orientation orientation, Quality quality, Integer maxWeightKg,
                      boolean active, boolean defaultFormat, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("code '%s' invalid : expected [A-Z0-9][A-Z0-9_-]{0,31}".formatted(code));
        }
        this.code = code;
        this.label = requireLabel(label);
        this.spineMm = requireRange(spineMm, 10, 1_000, "spineMm");
        this.depthMm = requireRange(depthMm, 50, 1_500, "depthMm");
        this.heightMm = requireRange(heightMm, 50, 1_500, "heightMm");
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.quality = Objects.requireNonNull(quality, "quality");
        if (maxWeightKg != null) requireRange(maxWeightKg, 1, 1_000, "maxWeightKg");
        this.maxWeightKg = maxWeightKg;
        this.active = active;
        this.defaultFormat = defaultFormat;
        if (defaultFormat && !active) throw new IllegalArgumentException("A default format must be active");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** Nouveau format, rangé debout (le rangement à plat arrive en v2). */
    public static BoxFormat create(TenantId tenant, String code, String label, int spineMm, int depthMm,
                                   int heightMm, Quality quality, Integer maxWeightKg, Instant at) {
        return new BoxFormat(UUID.randomUUID(), tenant, normalize(code), label, spineMm, depthMm, heightMm,
            Orientation.UPRIGHT, quality, maxWeightKg, true, false, at, at);
    }

    public static BoxFormat rehydrate(UUID id, TenantId tenant, String code, String label, int spineMm,
                                      int depthMm, int heightMm, Orientation orientation, Quality quality,
                                      Integer maxWeightKg, boolean active, boolean defaultFormat,
                                      Instant createdAt, Instant updatedAt) {
        return new BoxFormat(id, tenant, code, label, spineMm, depthMm, heightMm, orientation, quality,
            maxWeightKg, active, defaultFormat, createdAt, updatedAt);
    }

    public void describe(String newLabel, Quality newQuality, Integer newMaxWeightKg, Instant at) {
        this.label = requireLabel(newLabel);
        this.quality = Objects.requireNonNull(newQuality, "quality");
        if (newMaxWeightKg != null) requireRange(newMaxWeightKg, 1, 1_000, "maxWeightKg");
        this.maxWeightKg = newMaxWeightKg;
        this.updatedAt = at;
    }

    /** Un format retiré reste lisible sur les boîtes existantes mais n'est plus proposé. */
    public void setActive(boolean value, Instant at) {
        if (!value && defaultFormat) {
            throw new IllegalStateException("Le format par défaut ne peut pas être retiré : choisis d'abord un autre défaut");
        }
        this.active = value;
        this.updatedAt = at;
    }

    public void setDefault(boolean value, Instant at) {
        if (value && !active) throw new IllegalStateException("Un format retiré ne peut pas devenir le défaut");
        this.defaultFormat = value;
        this.updatedAt = at;
    }

    private static String normalize(String raw) {
        if (raw == null) throw new IllegalArgumentException("code must not be null");
        return raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireLabel(String label) {
        if (label == null || label.isBlank()) throw new IllegalArgumentException("label required");
        return label.trim();
    }

    private static int requireRange(int v, int min, int max, String name) {
        if (v < min || v > max) {
            throw new IllegalArgumentException("%s must be in [%d, %d], got %d".formatted(name, min, max, v));
        }
        return v;
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String code() { return code; }
    public String label() { return label; }
    public int spineMm() { return spineMm; }
    public int depthMm() { return depthMm; }
    public int heightMm() { return heightMm; }
    public Orientation orientation() { return orientation; }
    public Quality quality() { return quality; }
    public Optional<Integer> maxWeightKg() { return Optional.ofNullable(maxWeightKg); }
    public boolean active() { return active; }
    public boolean isDefault() { return defaultFormat; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
