package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Agrégat racine « emplacement physique ». Modélisé en table unique polymorphique :
 * un seul type Java pour les 5 niveaux (SITE → POSITION), différenciés par
 * {@link StorageLocationKind}. Simple à maîtriser, facile à requêter en arbre.
 *
 * <p><b>Invariants</b> :
 * <ul>
 *   <li>Un {@code SITE} n'a pas de parent, tous les autres niveaux en ont un.</li>
 *   <li>Le parent doit être du bon kind (ex. parent d'un {@code SHELF} = {@code STOREHOUSE}).</li>
 *   <li>Le {@code code} est un slug court lisible sur une étiquette (A-Z, 0-9, tiret, underscore).</li>
 *   <li>Le {@code path} est dénormalisé = concaténation des codes de la racine jusqu'à soi,
 *       séparés par {@code /}. Recalculé à chaque modification. Sert au code-barres et au listing.</li>
 * </ul>
 */
public final class StorageLocation {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_\\-]{0,31}$");

    /** Bornes d'une longueur utile (travée, tablette ou emplacement), en millimètres. */
    public static final int MIN_LENGTH_MM = 50;
    public static final int MAX_LENGTH_MM = 30_000;

    private final UUID id;
    private final TenantId tenantId;
    private final StorageLocationKind kind;
    private String code;
    private String label;
    private final UUID parentId;
    private String path;
    /** Longueur utile en mm (travée, tablette ou emplacement). Null = déduite (pattern 10). */
    private Integer lengthMm;
    /** Mobilier et dimensions (travée, tablette), jamais null : {@link PhysicalSpec#EMPTY}. */
    private PhysicalSpec spec = PhysicalSpec.EMPTY;
    private final Instant createdAt;
    private Instant updatedAt;

    private StorageLocation(
        UUID id, TenantId tenantId, StorageLocationKind kind, String code, String label,
        UUID parentId, String path, Integer lengthMm, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.code = requireValidCode(code);
        this.label = label;
        this.parentId = parentId;
        this.path = Objects.requireNonNull(path, "path");
        this.lengthMm = requireValidLength(kind, lengthMm);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        requireParentShape(kind, parentId);
    }

    /** Création d'un SITE (racine). */
    public static StorageLocation createSite(
        TenantId tenant, String code, String label, Instant at) {
        UUID id = UUID.randomUUID();
        String normalizedCode = normalize(code);
        return new StorageLocation(
            id, tenant, StorageLocationKind.SITE, normalizedCode, label,
            null, normalizedCode, null, at, at);
    }

    /** Création d'un enfant à partir du parent — valide le kind et calcule le path. */
    public static StorageLocation createChild(
        StorageLocation parent, StorageLocationKind childKind, String code, String label, Instant at) {
        Objects.requireNonNull(parent, "parent");
        if (childKind != parent.kind.childKind()) {
            throw new IllegalArgumentException(
                "Child of %s must be %s, got %s".formatted(parent.kind, parent.kind.childKind(), childKind));
        }
        String normalizedCode = normalize(code);
        String path = parent.path + "/" + normalizedCode;
        return new StorageLocation(
            UUID.randomUUID(), parent.tenantId, childKind, normalizedCode, label,
            parent.id, path, null, at, at);
    }

    /** Rehydratation depuis la persistance (le path est lu, pas recalculé). */
    public static StorageLocation rehydrate(
        UUID id, TenantId tenant, StorageLocationKind kind, String code, String label,
        UUID parentId, String path, Integer lengthMm, Instant createdAt, Instant updatedAt) {
        return new StorageLocation(id, tenant, kind, code, label, parentId, path, lengthMm, createdAt, updatedAt);
    }

    /** Rehydratation avec le mobilier et les dimensions (pattern 10, 5T.B). */
    public static StorageLocation rehydrate(
        UUID id, TenantId tenant, StorageLocationKind kind, String code, String label,
        UUID parentId, String path, Integer lengthMm, PhysicalSpec spec, Instant createdAt, Instant updatedAt) {
        StorageLocation l = new StorageLocation(id, tenant, kind, code, label, parentId, path, lengthMm, createdAt, updatedAt);
        l.spec = (spec == null ? PhysicalSpec.EMPTY : spec).requireAllowedFor(kind);
        return l;
    }

    /** Fixe le mobilier et les dimensions (travée) ou les dimensions propres (tablette). */
    public void changeSpec(PhysicalSpec newSpec, Instant at) {
        this.spec = (newSpec == null ? PhysicalSpec.EMPTY : newSpec).requireAllowedFor(kind);
        this.updatedAt = at;
    }

    /** Seules la travée et la tablette portent du mobilier ou des dimensions de rangement. */
    public static boolean hasSpec(StorageLocationKind kind) {
        return kind == StorageLocationKind.SHELF || kind == StorageLocationKind.RACK;
    }

    public void rename(String newLabel, Instant at) {
        this.label = newLabel;
        this.updatedAt = at;
    }

    /** Fixe la longueur utile (travée, tablette, emplacement) ; {@code null} = déduite. */
    public void changeLength(Integer newLengthMm, Instant at) {
        this.lengthMm = requireValidLength(kind, newLengthMm);
        this.updatedAt = at;
    }

    /** Seuls la travée, la tablette et l'emplacement ont une longueur de rangement. */
    public static boolean hasLength(StorageLocationKind kind) {
        return kind == StorageLocationKind.SHELF || kind == StorageLocationKind.RACK
            || kind == StorageLocationKind.POSITION;
    }

    private static Integer requireValidLength(StorageLocationKind kind, Integer mm) {
        if (mm == null) return null;
        if (!hasLength(kind)) {
            throw new IllegalArgumentException("A " + kind + " has no storage length");
        }
        if (mm < MIN_LENGTH_MM || mm > MAX_LENGTH_MM) {
            throw new IllegalArgumentException(
                "length must be in [%d, %d] mm, got %d".formatted(MIN_LENGTH_MM, MAX_LENGTH_MM, mm));
        }
        return mm;
    }

    /** Recalcule le path quand le parent a bougé — utilisé par le use case de recalcul récursif. */
    public void rewritePath(String newPath, Instant at) {
        this.path = Objects.requireNonNull(newPath, "newPath");
        this.updatedAt = at;
    }

    private static String normalize(String raw) {
        if (raw == null) throw new IllegalArgumentException("code must not be null");
        return raw.toUpperCase(java.util.Locale.ROOT).trim();
    }

    private static String requireValidCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException(
                "code '%s' invalid : expected [A-Z0-9][A-Z0-9_-]{0,31}".formatted(code));
        }
        return code;
    }

    private static void requireParentShape(StorageLocationKind kind, UUID parentId) {
        if (kind == StorageLocationKind.SITE && parentId != null) {
            throw new IllegalArgumentException("SITE must have no parent");
        }
        if (kind != StorageLocationKind.SITE && parentId == null) {
            throw new IllegalArgumentException(kind + " requires a parent");
        }
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public StorageLocationKind kind() { return kind; }
    public String code() { return code; }
    public String label() { return label; }
    public Optional<UUID> parentId() { return Optional.ofNullable(parentId); }
    public String path() { return path; }
    public Optional<Integer> lengthMm() { return Optional.ofNullable(lengthMm); }
    public PhysicalSpec spec() { return spec; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
