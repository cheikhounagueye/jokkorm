package com.jokkorm.application.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditEvent;
import com.jokkorm.domain.audit.AuditEventRepository;
import com.jokkorm.domain.audit.AuditTargetType;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests unitaires du calcul de chaîne HMAC — repository en mémoire, Clock figé.
 *
 * <p>Ces tests ne touchent pas Spring. L'injection du bean se fait directement.
 */
class AuditChainServiceTest {

    private static final TenantId TENANT = TenantId.of("acme");
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-21T10:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("1er évent : prevEventHash=null, hmac et payloadHash cohérents")
    void first_event_has_null_prev_hash() {
        InMemoryAuditRepo repo = new InMemoryAuditRepo();
        AuditChainService service = new AuditChainService(repo, "test-secret", FIXED);
        UUID targetId = UUID.randomUUID();

        AuditEvent event = TenantContext.callWithUnchecked(TENANT, () ->
            service.record(AuditAction.COTE_ASSIGNED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "alice", "cote=X/1"));

        assertThat(event.prevEventHash()).isNull();
        assertThat(event.payloadHash()).hasSize(32);
        assertThat(event.hmac()).hasSize(32);
        assertThat(event.ts()).isEqualTo(Instant.parse("2026-11-21T10:00:00Z"));
    }

    @Test
    @DisplayName("Chaîne de 3 évents : chaque hmac devient le prevEventHash du suivant")
    void chain_links_prev_hash_to_hmac() {
        InMemoryAuditRepo repo = new InMemoryAuditRepo();
        AuditChainService service = new AuditChainService(repo, "test-secret", FIXED);
        UUID targetId = UUID.randomUUID();

        List<AuditEvent> events = new ArrayList<>();
        TenantContext.callWithUnchecked(TENANT, () -> {
            events.add(service.record(AuditAction.COTE_ASSIGNED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "alice", "a"));
            events.add(service.record(AuditAction.LEGAL_HOLD_APPLIED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "bob", "b"));
            events.add(service.record(AuditAction.LEGAL_HOLD_RELEASED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "alice", "c"));
            return null;
        });

        assertThat(events.get(0).prevEventHash()).isNull();
        assertThat(events.get(1).prevEventHash()).isEqualTo(events.get(0).hmac());
        assertThat(events.get(2).prevEventHash()).isEqualTo(events.get(1).hmac());
    }

    @Test
    @DisplayName("verifyChain() retourne -1 sur une chaîne intègre")
    void verify_chain_returns_minus_one_on_intact_chain() {
        InMemoryAuditRepo repo = new InMemoryAuditRepo();
        AuditChainService service = new AuditChainService(repo, "test-secret", FIXED);
        UUID targetId = UUID.randomUUID();

        TenantContext.callWithUnchecked(TENANT, () -> {
            service.record(AuditAction.COTE_ASSIGNED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "alice", "a");
            service.record(AuditAction.LEGAL_HOLD_APPLIED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "bob", "b");
            return null;
        });

        int firstBadIndex = TenantContext.callWithUnchecked(TENANT, () ->
            service.verifyChain(TENANT, AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId));
        assertThat(firstBadIndex).isEqualTo(-1);
    }

    @Test
    @DisplayName("verifyChain() détecte une altération de métadonnée (payload hash change)")
    void verify_chain_detects_tampered_metadata() throws Exception {
        InMemoryAuditRepo repo = new InMemoryAuditRepo();
        AuditChainService service = new AuditChainService(repo, "test-secret", FIXED);
        UUID targetId = UUID.randomUUID();

        TenantContext.callWithUnchecked(TENANT, () -> {
            service.record(AuditAction.COTE_ASSIGNED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "alice", "original");
            service.record(AuditAction.LEGAL_HOLD_APPLIED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "bob", "second");
            return null;
        });

        // Altération : on remplace la metadata du 1er évent par un autre texte, sans recalculer hash/hmac.
        AuditEvent original = repo.store.get(0);
        AuditEvent tampered = new AuditEvent(
            original.id(), original.tenantId(), original.ts(), original.actor(),
            original.action(), original.targetType(), original.targetId(),
            original.payloadHash(), original.prevEventHash(), original.hmac(),
            "TAMPERED");
        repo.store.set(0, tampered);

        int firstBadIndex = TenantContext.callWithUnchecked(TENANT, () ->
            service.verifyChain(TENANT, AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId));
        assertThat(firstBadIndex).isEqualTo(0);
    }

    @Test
    @DisplayName("Deux tenants, même targetId → chaînes indépendantes (clé HMAC distincte)")
    void chains_are_tenant_scoped() {
        InMemoryAuditRepo repo = new InMemoryAuditRepo();
        AuditChainService service = new AuditChainService(repo, "test-secret", FIXED);
        UUID targetId = UUID.randomUUID();
        TenantId acme = TenantId.of("acme");
        TenantId gamma = TenantId.of("gamma");

        AuditEvent fromAcme = TenantContext.callWithUnchecked(acme, () ->
            service.record(AuditAction.COTE_ASSIGNED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "alice", "same"));
        AuditEvent fromGamma = TenantContext.callWithUnchecked(gamma, () ->
            service.record(AuditAction.COTE_ASSIGNED,
                AuditTargetType.PHYSICAL_ARCHIVE_RECORD, targetId, "alice", "same"));

        // Même payload mais clé HMAC différente par tenant → hmacs différents
        assertThat(fromAcme.hmac()).isNotEqualTo(fromGamma.hmac());
    }

    // ───── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Wrapper statique pour appeler callWith sans propager la checked exception.
     * Utilisé par les tests pour rester lisibles.
     */
    private static class TenantContextCallers {}

    /**
     * Repository en mémoire : utilise l'ordre d'insertion comme tiebreaker du ts.
     * Avec un Clock fixé, plusieurs évents partagent le même ts — l'ordre d'insertion
     * est le seul moyen déterministe de retrouver « le dernier ». Approximation
     * acceptable de l'index composite (tenant_id, target_type, target_id, ts, id)
     * que Postgres utilise en prod.
     */
    private static class InMemoryAuditRepo implements AuditEventRepository {
        final List<AuditEvent> store = new ArrayList<>();

        @Override public AuditEvent save(AuditEvent event) {
            store.add(event);
            return event;
        }

        @Override public Optional<AuditEvent> findLast(
            TenantId tenant, AuditTargetType targetType, UUID targetId) {
            AuditEvent last = null;
            for (AuditEvent e : store) {
                if (e.tenantId().equals(tenant)
                    && e.targetType() == targetType
                    && e.targetId().equals(targetId)) {
                    last = e;
                }
            }
            return Optional.ofNullable(last);
        }

        @Override public List<AuditEvent> findChain(
            TenantId tenant, AuditTargetType targetType, UUID targetId) {
            return store.stream()
                .filter(e -> e.tenantId().equals(tenant)
                    && e.targetType() == targetType
                    && e.targetId().equals(targetId))
                .toList();
        }
    }
}
