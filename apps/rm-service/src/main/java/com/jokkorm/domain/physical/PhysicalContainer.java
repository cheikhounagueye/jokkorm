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
 *   <li>{@code receivedAt}/{@code receivedBy} — pointage d'arrivée en zone tampon (mode magasin,
 *       pattern 12). Null si la boîte n'a jamais été pointée.</li>
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
    private final UUID batchId;
    private final Integer sequenceInBatch;
    private String externalReference;
    /** Format de boîte (dos, hauteur…). Null = format par défaut du tenant. */
    private UUID boxFormatId;
    private Instant receivedAt;
    private String receivedBy;

    private PhysicalContainer(
        UUID id, TenantId tenantId, String barcode, String label, UUID positionId,
        Integer capacity, ContainerState state,
        Instant createdAt, Instant updatedAt, Instant lastMovedAt,
        UUID batchId, Integer sequenceInBatch, String externalReference, UUID boxFormatId) {
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
        this.batchId = batchId;
        this.sequenceInBatch = sequenceInBatch;
        this.externalReference = externalReference;
        this.boxFormatId = boxFormatId;
        if ((batchId == null) != (sequenceInBatch == null)) {
            throw new IllegalArgumentException(
                "batchId and sequenceInBatch must both be set or both be null");
        }
        if (sequenceInBatch != null && sequenceInBatch < 1) {
            throw new IllegalArgumentException("sequenceInBatch must be >= 1");
        }
    }

    /** Création d'une boîte vide, non positionnée, sans lot (phase 5B unitaire). */
    public static PhysicalContainer createEmpty(
        TenantId tenant, String barcode, String label, Integer capacity, UUID boxFormatId, Instant at) {
        return new PhysicalContainer(
            UUID.randomUUID(), tenant, normalize(barcode), label, null,
            capacity, ContainerState.EMPTY, at, at, null, null, null, null, boxFormatId);
    }

    /** Création d'une boîte dans un lot (phase 5M bulk). */
    public static PhysicalContainer createInBatch(
        TenantId tenant, String barcode, String label, Integer capacity,
        UUID batchId, int sequenceInBatch, UUID boxFormatId, Instant at) {
        return new PhysicalContainer(
            UUID.randomUUID(), tenant, normalize(barcode), label, null,
            capacity, ContainerState.EMPTY, at, at, null, batchId, sequenceInBatch, null, boxFormatId);
    }

    /** Rehydratation depuis la persistance. */
    public static PhysicalContainer rehydrate(
        UUID id, TenantId tenant, String barcode, String label, UUID positionId,
        Integer capacity, ContainerState state,
        Instant createdAt, Instant updatedAt, Instant lastMovedAt,
        UUID batchId, Integer sequenceInBatch, String externalReference, UUID boxFormatId,
        Instant receivedAt, String receivedBy) {
        PhysicalContainer c = new PhysicalContainer(
            id, tenant, barcode, label, positionId, capacity, state,
            createdAt, updatedAt, lastMovedAt, batchId, sequenceInBatch, externalReference, boxFormatId);
        c.receivedAt = receivedAt;
        c.receivedBy = receivedBy;
        return c;
    }

    /**
     * Pointage d'arrivée : la boîte est reçue dans la zone tampon (aucune position).
     * Pointer deux fois de suite ne change rien ; une boîte retirée de sa tablette puis
     * pointée de nouveau prend la nouvelle date. L'appelant écarte d'abord les cas métier
     * (boîte rangée, en prêt) pour donner un message clair.
     *
     * @return {@code false} si la boîte était déjà pointée et n'a pas bougé depuis
     */
    public boolean receive(String by, Instant at) {
        requireNotDestroyed();
        if (positionId != null) {
            throw new IllegalStateException("Container " + barcode + " is on a shelf : remove it first");
        }
        if (by == null || by.isBlank()) throw new IllegalArgumentException("receivedBy required");
        boolean movedSince = lastMovedAt != null && receivedAt != null && lastMovedAt.isAfter(receivedAt);
        if (receivedAt != null && !movedSince) return false;
        this.receivedAt = at;
        this.receivedBy = by.trim();
        this.updatedAt = at;
        return true;
    }

    /**
     * Retire la boîte de sa tablette : elle repasse en zone tampon et libère sa place.
     *
     * @return la position quittée
     */
    public UUID removeFromShelf(Instant at) {
        requireNotDestroyed();
        if (positionId == null) {
            throw new IllegalStateException("Container " + barcode + " is not on a shelf");
        }
        UUID from = positionId;
        this.positionId = null;
        this.lastMovedAt = at;
        this.updatedAt = at;
        return from;
    }

    /** Change le format (ex. correction après reprise). Le contrôle de place est fait par l'appelant. */
    public void changeFormat(UUID newFormatId, Instant at) {
        requireNotDestroyed();
        this.boxFormatId = Objects.requireNonNull(newFormatId, "newFormatId");
        this.updatedAt = at;
    }

    /**
     * Fixe une référence externe (ex. numéro PRO ARCHIVES SYSTEMES "19944") pour
     * tracer une boîte reçue d'un prestataire tiers. Nullable, modifiable.
     */
    public void setExternalReference(String ref, Instant at) {
        requireNotDestroyed();
        this.externalReference = (ref == null || ref.isBlank()) ? null : ref.trim();
        this.updatedAt = at;
    }

    private void requireNotDestroyed() {
        if (state == ContainerState.DESTROYED) {
            throw new IllegalStateException("Container " + barcode + " is destroyed");
        }
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

    /** Destruction physique — terminal. La boîte quitte la tablette et libère sa place. */
    public void destroy(Instant at) {
        requireNotDestroyed();
        this.state = ContainerState.DESTROYED;
        this.positionId = null;
        this.lastMovedAt = at;
        this.updatedAt = at;
    }

    public void rename(String newLabel, Instant at) {
        requireNotDestroyed();
        this.label = newLabel;
        this.updatedAt = at;
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
    public Optional<UUID> batchId() { return Optional.ofNullable(batchId); }
    public Optional<Integer> sequenceInBatch() { return Optional.ofNullable(sequenceInBatch); }
    public Optional<String> externalReference() { return Optional.ofNullable(externalReference); }
    public Optional<UUID> boxFormatId() { return Optional.ofNullable(boxFormatId); }
    public Optional<Instant> receivedAt() { return Optional.ofNullable(receivedAt); }
    public Optional<String> receivedBy() { return Optional.ofNullable(receivedBy); }
}
