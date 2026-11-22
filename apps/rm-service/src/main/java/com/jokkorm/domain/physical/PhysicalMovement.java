package com.jokkorm.domain.physical;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Journal des mouvements physiques — append-only (table protégée par trigger PG).
 *
 * <p>Chaque mouvement est un évent immuable qui raconte qu'un dossier (identifié par
 * son {@code recordId}) a subi un verbe physique ({@code kind}) à un instant précis.
 * La boîte éventuellement impliquée est tracée via {@code containerId}.
 *
 * <p>La timeline par cote = tous les mouvements de son record, triés par {@code occurredAt}.
 */
public record PhysicalMovement(
    UUID id,
    TenantId tenantId,
    UUID recordId,
    UUID containerId,
    MovementKind kind,
    String actor,
    Instant occurredAt,
    String motive) {

    public PhysicalMovement {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(kind, "kind");
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("actor required");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        // containerId peut être null (ex. DIGITIZE_REQUEST sans ouvrir la boîte ?)
        // motive peut être null
    }

    public static PhysicalMovement create(
        TenantId tenant, UUID recordId, UUID containerId,
        MovementKind kind, String actor, Instant at, String motive) {
        return new PhysicalMovement(
            UUID.randomUUID(), tenant, recordId, containerId, kind, actor, at, motive);
    }

    public Optional<UUID> containerIdOpt() { return Optional.ofNullable(containerId); }
    public Optional<String> motiveOpt() { return Optional.ofNullable(motive); }
}
