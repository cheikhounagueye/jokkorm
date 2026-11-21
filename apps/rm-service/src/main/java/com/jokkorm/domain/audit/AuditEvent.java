package com.jokkorm.domain.audit;

import com.jokkorm.commons.tenancy.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Évent d'audit immuable, chaînage HMAC-SHA256 par {@code (tenant, targetType, targetId)}.
 *
 * <p>Chaque évent porte :
 * <ul>
 *   <li>{@code payloadHash} — SHA-256 du payload normalisé {@code action|targetType|targetId|actor|ts|metadata}</li>
 *   <li>{@code prevEventHash} — {@code hmac} du précédent évent de la même cible ({@code null} si premier)</li>
 *   <li>{@code hmac} — HMAC-SHA-256 de {@code payloadHash || prevEventHash || ts} avec clé dérivée par tenant</li>
 * </ul>
 *
 * <p>Vérification ex-post = rejeu de la chaîne. Insertion / suppression / modification
 * au milieu casse le chaînage en aval = détection non-répudiable.
 *
 * <p>La table SQL doit porter un trigger qui refuse DELETE et UPDATE — l'application elle-même
 * n'expose que {@code save}.
 */
public record AuditEvent(
    UUID id,
    TenantId tenantId,
    Instant ts,
    String actor,
    AuditAction action,
    AuditTargetType targetType,
    UUID targetId,
    byte[] payloadHash,
    byte[] prevEventHash,
    byte[] hmac,
    String metadata) {

    public AuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(ts, "ts");
        if (actor == null || actor.isBlank()) throw new IllegalArgumentException("actor required");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        if (payloadHash == null || payloadHash.length != 32) {
            throw new IllegalArgumentException("payloadHash must be 32 bytes (SHA-256)");
        }
        if (hmac == null || hmac.length != 32) {
            throw new IllegalArgumentException("hmac must be 32 bytes (HMAC-SHA-256)");
        }
        // prevEventHash peut être null (premier évent de la chaîne)
    }

    // On base equals/hashCode sur `id` seul : les byte[] auraient une identité référentielle,
    // ce qui casse la comparaison dans les tests.
    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof AuditEvent that && Objects.equals(id, that.id));
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "AuditEvent[id=%s action=%s target=%s:%s]"
            .formatted(id, action, targetType, targetId);
    }
}
