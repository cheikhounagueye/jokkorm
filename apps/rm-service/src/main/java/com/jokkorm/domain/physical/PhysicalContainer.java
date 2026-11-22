package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Agrégat « conteneur physique » — une boîte d'archives.
 *
 * <ul>
 *   <li>{@code barcode} — identifiant physique imprimé sur l'étiquette. Unique par tenant.
 *       Généré par JokkoRM OU fourni par l'opérateur (ex. code existant des fournitures).</li>
 *   <li>{@code positionId} — emplacement de rangement (POSITION dans la topographie). Null tant
 *       que la boîte n'a pas été rangée.</li>
 *   <li>{@code capacity} — nombre max de records physiques acceptés. Null = pas de limite.</li>
 *   <li>{@code state} — EMPTY → OPEN → SEALED → DESTROYED (voir {@link ContainerState}).</li>
 * </ul>
 *
 * <p>Invariants :
 * <ul>
 *   <li>Une boîte DESTROYED ne peut plus changer d'état ni de position.</li>
 *   <li>Une boîte SEALED ne peut pas repasser à EMPTY/OPEN (sens unique).</li>
 *   <li>Changer la position incrémente {@code lastMovedAt}.</li>
 * </ul>
 */
public final class PhysicalContainer {

    private static final Pattern BARCODE = Pattern.compile("^[A-Z0-9][A-Z0-9_\\-]{0,63}$");

    private final UUID id;
    private final TenantId tenantId;
    private final String barcode;
    private String label;
    private UUID positionId;
    private Integer capacity;
    private ContainerState state;
    private final Instant createdAt;
    private Instant updatedAt;
    private Instant lastMovedAt;

    private PhysicalContainer(
        UUID id, TenantId tenantId, String barcode, String label, UUID positionId,
        Integer capacity, ContainerState state,
        Instant createdAt, Instant updatedAt, Instant lastMovedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.barcode = requireValidBarcode(barcode);
        this.label = label;
        this.positionId = positionId;
        this.capacity = requireValidCapacity(capacity);
        this.state = Objects.requireNonNull(state, "state");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.lastMovedAt = lastMovedAt;
    }

    /** Création d'une boîte vide, non positionnée. */
    public static PhysicalContainer createEmpty(
        TenantId tenant, String barcode, String label, Integer capacity, Instant at) {
        return new PhysicalContainer(
            UUID.randomUUID(), tenant, normalize(barcode), label, null,
            capacity, ContainerState.EMPTY, at, at, null);
    }

    /** Rehydratation depuis la persistance. */
    public static PhysicalContainer rehydrate(
        UUID id, TenantId tenant, String barcode, String label, UUID positionId,
        Integer capacity, ContainerState state,
        Instant createdAt, Instant updatedAt, Instant lastMovedAt) {
        return new PhysicalContainer(
            id, tenant, barcode, label, positionId, capacity, state,
            createdAt, updatedAt, lastMovedAt);
    }

    /** Range (ou déplace) la boîte sur une position donnée. */
    public void placeAt(UUID newPositionId, Instant at) {
        requireNotDestroyed();
        Objects.requireNonNull(newPositionId, "newPositionId");
        this.positionId = newPositionId;
        this.lastMovedAt = at;
        this.updatedAt = at;
    }

    /**
     * Signale qu'un dossier a été rangé dedans. EMPTY → OPEN automatiquement.
     * N'incrémente pas de compteur en 5B (phase 5C fera le lien avec PhysicalArchiveRecord).
     */
    public void markHasContent(Instant at) {
        requireNotDestroyed();
        if (state == ContainerState.SEALED) {
            throw new IllegalStateException("Sealed container " + barcode + " cannot accept new records");
        }
        if (state == ContainerState.EMPTY) {
            this.state = ContainerState.OPEN;
            this.updatedAt = at;
        }
    }

    /** Scellement — la boîte ne reçoit plus de nouveaux dossiers, sortie tracée. */
    public void seal(Instant at) {
        requireNotDestroyed();
        if (state == ContainerState.SEALED) return;
        this.state = ContainerState.SEALED;
        this.updatedAt = at;
    }

    /** Destruction physique — terminal. */
    public void destroy(Instant at) {
        requireNotDestroyed();
        this.state = ContainerState.DESTROYED;
        this.updatedAt = at;
    }

    public void rename(String newLabel, Instant at) {
        requireNotDestroyed();
        this.label = newLabel;
        this.updatedAt = at;
    }

    private void requireNotDestroyed() {
        if (state == ContainerState.DESTROYED) {
            throw new IllegalStateException("Container " + barcode + " is destroyed");
        }
    }

    private static String requireValidBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        if (!BARCODE.matcher(barcode).matches()) {
            throw new IllegalArgumentException(
                "barcode '%s' invalid : expected [A-Z0-9][A-Z0-9_-]{0,63}".formatted(barcode));
        }
        return barcode;
    }

    private static Integer requireValidCapacity(Integer capacity) {
        if (capacity != null && capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0 or null (unlimited)");
        }
        return capacity;
    }

    private static String normalize(String raw) {
        if (raw == null) throw new IllegalArgumentException("barcode must not be null");
        return raw.toUpperCase(java.util.Locale.ROOT).trim();
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String barcode() { return barcode; }
    public String label() { return label; }
    public Optional<UUID> positionId() { return Optional.ofNullable(positionId); }
    public Optional<Integer> capacity() { return Optional.ofNullable(capacity); }
    public ContainerState state() { return state; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public Optional<Instant> lastMovedAt() { return Optional.ofNullable(lastMovedAt); }
}
