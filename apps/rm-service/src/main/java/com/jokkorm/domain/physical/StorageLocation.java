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

    /** Nombre de boîtes qu'une POSITION accueille quand aucune capacité n'est saisie. */
    public static final int DEFAULT_POSITION_CAPACITY = 6;
    public static final int MAX_POSITION_CAPACITY = 100;

    private final UUID id;
    private final TenantId tenantId;
    private final StorageLocationKind kind;
    private String code;
    private String label;
    private final UUID parentId;
    private String path;
    private Integer capacity;
    private final Instant createdAt;
    private Instant updatedAt;

    private StorageLocation(
        UUID id, TenantId tenantId, StorageLocationKind kind, String code, String label,
        UUID parentId, String path, Integer capacity, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.code = requireValidCode(code);
        this.label = label;
        this.parentId = parentId;
        this.path = Objects.requireNonNull(path, "path");
        this.capacity = requireValidCapacity(kind, capacity);
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
        UUID parentId, String path, Integer capacity, Instant createdAt, Instant updatedAt) {
        return new StorageLocation(id, tenant, kind, code, label, parentId, path, capacity, createdAt, updatedAt);
    }

    public void rename(String newLabel, Instant at) {
        this.label = newLabel;
        this.updatedAt = at;
    }

    /** Fixe le nombre de boîtes qu'accueille une POSITION ({@code null} = valeur par défaut). */
    public void changeCapacity(Integer newCapacity, Instant at) {
        this.capacity = requireValidCapacity(kind, newCapacity);
        this.updatedAt = at;
    }

    /** Capacité en boîtes d'une POSITION, valeur par défaut incluse. */
    public int effectiveCapacity() {
        if (kind != StorageLocationKind.POSITION) {
            throw new IllegalStateException("Only a POSITION has a box capacity, got " + kind);
        }
        return capacity != null ? capacity : DEFAULT_POSITION_CAPACITY;
    }

    private static Integer requireValidCapacity(StorageLocationKind kind, Integer capacity) {
        if (capacity == null) return null;
        if (kind != StorageLocationKind.POSITION) {
            throw new IllegalArgumentException("Only a POSITION can have a box capacity, got " + kind);
        }
        if (capacity < 1 || capacity > MAX_POSITION_CAPACITY) {
            throw new IllegalArgumentException(
                "capacity must be in [1, %d], got %d".formatted(MAX_POSITION_CAPACITY, capacity));
        }
        return capacity;
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
    public Optional<Integer> capacity() { return Optional.ofNullable(capacity); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
