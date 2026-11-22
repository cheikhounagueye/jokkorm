package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lot de provisionnement — regroupement logique de N boîtes créées en une opération
 * (ex. commande 500 boîtes neuves). Voir docs/pattern/06-provisioning-masse-boites.md.
 *
 * <p>Invariants :
 * <ul>
 *   <li>{@code size} est fixé à la création et ne change jamais (append-only).</li>
 *   <li>Ne peut passer en {@link BatchStatus#ARCHIVED} que si toutes les boîtes sont
 *       soit {@code DESTROYED} soit encore {@code EMPTY} en transit.</li>
 *   <li>{@code firstPrintedAt} / {@code lastPrintedAt} mis à jour à chaque génération PDF.</li>
 * </ul>
 */
public final class ContainerBatch {

    private final UUID id;
    private final TenantId tenantId;
    private final String reference;
    private final int size;
    private final String labelPattern;
    private final Integer defaultCapacity;
    private BatchStatus status;
    private final Instant createdAt;
    private final String createdBy;
    private Instant firstPrintedAt;
    private Instant lastPrintedAt;
    private Instant updatedAt;

    private ContainerBatch(
        UUID id, TenantId tenantId, String reference, int size,
        String labelPattern, Integer defaultCapacity, BatchStatus status,
        Instant createdAt, String createdBy,
        Instant firstPrintedAt, Instant lastPrintedAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("reference required (ex. 'Commande DMG 2026-11')");
        }
        if (reference.length() > 255) {
            throw new IllegalArgumentException("reference max 255 chars");
        }
        this.reference = reference.trim();
        if (size < 1 || size > 1000) {
            throw new IllegalArgumentException("size must be in [1, 1000]");
        }
        this.size = size;
        this.labelPattern = labelPattern == null ? null : labelPattern.trim();
        if (defaultCapacity != null && defaultCapacity < 1) {
            throw new IllegalArgumentException("defaultCapacity must be > 0 or null");
        }
        this.defaultCapacity = defaultCapacity;
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("createdBy required");
        }
        this.createdBy = createdBy;
        this.firstPrintedAt = firstPrintedAt;
        this.lastPrintedAt = lastPrintedAt;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ContainerBatch create(
        TenantId tenant, String reference, int size,
        String labelPattern, Integer defaultCapacity,
        String createdBy, Instant at) {
        return new ContainerBatch(
            UUID.randomUUID(), tenant, reference, size,
            labelPattern, defaultCapacity, BatchStatus.CREATED,
            at, createdBy, null, null, at);
    }

    public static ContainerBatch rehydrate(
        UUID id, TenantId tenantId, String reference, int size,
        String labelPattern, Integer defaultCapacity, BatchStatus status,
        Instant createdAt, String createdBy,
        Instant firstPrintedAt, Instant lastPrintedAt, Instant updatedAt) {
        return new ContainerBatch(id, tenantId, reference, size,
            labelPattern, defaultCapacity, status,
            createdAt, createdBy, firstPrintedAt, lastPrintedAt, updatedAt);
    }

    /** Marque le lot comme ayant été imprimé au moins une fois. Idempotent. */
    public void markPrinted(Instant at) {
        if (status == BatchStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot print an archived batch: " + reference);
        }
        if (firstPrintedAt == null) {
            this.firstPrintedAt = at;
            if (status == BatchStatus.CREATED) {
                this.status = BatchStatus.PRINTED;
            }
        }
        this.lastPrintedAt = at;
        this.updatedAt = at;
    }

    /** Marque le lot comme entièrement déployé — toutes boîtes rangées au moins une fois. */
    public void markFullyDeployed(Instant at) {
        if (status == BatchStatus.ARCHIVED) return;
        this.status = BatchStatus.FULLY_DEPLOYED;
        this.updatedAt = at;
    }

    /** Archive le lot — à appeler seulement quand le lot ne sert plus activement. */
    public void archive(Instant at) {
        this.status = BatchStatus.ARCHIVED;
        this.updatedAt = at;
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public String reference() { return reference; }
    public int size() { return size; }
    public Optional<String> labelPattern() { return Optional.ofNullable(labelPattern); }
    public Optional<Integer> defaultCapacity() { return Optional.ofNullable(defaultCapacity); }
    public BatchStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Optional<Instant> firstPrintedAt() { return Optional.ofNullable(firstPrintedAt); }
    public Optional<Instant> lastPrintedAt() { return Optional.ofNullable(lastPrintedAt); }
    public Instant updatedAt() { return updatedAt; }
}
