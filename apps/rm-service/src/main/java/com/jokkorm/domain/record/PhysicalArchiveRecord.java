package com.jokkorm.domain.record;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Agrégat racine « enveloppe physique d'une archive ». Modèle slave : la cote et les
 * métadonnées autoritaires (DUA, sort final) appartiennent au maître ; cet objet suit le
 * destin physique (statut, emplacement, gel). Voir ADR-0004 et docs/pattern/.
 *
 * <p>Invariants :
 * <ul>
 *   <li>La cote est fixée à la création et ne change jamais.</li>
 *   <li>Le tenant est fixé à la création et ne change jamais.</li>
 *   <li>Un record {@link PhysicalArchiveStatus#DESTROYED} ne peut plus être modifié.</li>
 *   <li>Un record {@link PhysicalArchiveStatus#FROZEN} ne peut ni être détruit ni
 *       transféré tant que le gel n'est pas levé par le maître.</li>
 * </ul>
 */
public final class PhysicalArchiveRecord {

    private final UUID id;
    private final TenantId tenantId;
    private final Cote cote;
    private final Instant registeredAt;
    private PhysicalArchiveStatus status;
    private Instant lastTransitionAt;

    private PhysicalArchiveRecord(
        UUID id, TenantId tenantId, Cote cote, PhysicalArchiveStatus status,
        Instant registeredAt, Instant lastTransitionAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.cote = Objects.requireNonNull(cote, "cote");
        this.status = Objects.requireNonNull(status, "status");
        this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt");
        this.lastTransitionAt = Objects.requireNonNull(lastTransitionAt, "lastTransitionAt");
    }

    /** Factory — appelée par le use case qui reçoit l'événement {@code cote-assigned} du maître. */
    public static PhysicalArchiveRecord register(TenantId tenant, Cote cote, Instant at) {
        UUID id = UUID.randomUUID();
        return new PhysicalArchiveRecord(id, tenant, cote, PhysicalArchiveStatus.REGISTERED, at, at);
    }

    /** Factory pour rehydratation depuis la persistance. */
    public static PhysicalArchiveRecord rehydrate(
        UUID id, TenantId tenant, Cote cote, PhysicalArchiveStatus status,
        Instant registeredAt, Instant lastTransitionAt) {
        return new PhysicalArchiveRecord(id, tenant, cote, status, registeredAt, lastTransitionAt);
    }

    /** Transition {@code REGISTERED → STORED} après versement physique + attribution emplacement. */
    public void markStored(Instant at) {
        requireNotDestroyed();
        if (status == PhysicalArchiveStatus.FROZEN) {
            throw new IllegalStateException("Cannot store a frozen archive: " + cote.value());
        }
        this.status = PhysicalArchiveStatus.STORED;
        this.lastTransitionAt = at;
    }

    /** Gel contentieux prononcé par le maître. Bloque toute sortie ultérieure. */
    public void markFrozen(Instant at) {
        requireNotDestroyed();
        if (status == PhysicalArchiveStatus.TRANSFERRED) {
            throw new IllegalStateException("Cannot freeze a transferred archive: " + cote.value());
        }
        this.status = PhysicalArchiveStatus.FROZEN;
        this.lastTransitionAt = at;
    }

    /** Levée du gel : retour à {@link PhysicalArchiveStatus#STORED}. */
    public void releaseFreeze(Instant at) {
        if (status != PhysicalArchiveStatus.FROZEN) {
            throw new IllegalStateException("Cannot release freeze on non-frozen archive: " + cote.value());
        }
        this.status = PhysicalArchiveStatus.STORED;
        this.lastTransitionAt = at;
    }

    /** Destruction exécutée. Transition terminale. */
    public void markDestroyed(Instant at) {
        requireNotDestroyed();
        if (status == PhysicalArchiveStatus.FROZEN) {
            throw new IllegalStateException("Cannot destroy a frozen archive: " + cote.value());
        }
        this.status = PhysicalArchiveStatus.DESTROYED;
        this.lastTransitionAt = at;
    }

    /** Transfert aux Archives nationales. Transition terminale. */
    public void markTransferred(Instant at) {
        requireNotDestroyed();
        if (status == PhysicalArchiveStatus.FROZEN) {
            throw new IllegalStateException("Cannot transfer a frozen archive: " + cote.value());
        }
        this.status = PhysicalArchiveStatus.TRANSFERRED;
        this.lastTransitionAt = at;
    }

    private void requireNotDestroyed() {
        if (status == PhysicalArchiveStatus.DESTROYED) {
            throw new IllegalStateException("Archive already destroyed: " + cote.value());
        }
    }

    public UUID id() { return id; }
    public TenantId tenantId() { return tenantId; }
    public Cote cote() { return cote; }
    public PhysicalArchiveStatus status() { return status; }
    public Instant registeredAt() { return registeredAt; }
    public Instant lastTransitionAt() { return lastTransitionAt; }
}
