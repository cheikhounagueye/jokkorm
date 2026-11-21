package com.jokkorm.application.audit;

import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.audit.AuditAction;
import com.jokkorm.domain.audit.AuditEvent;
import com.jokkorm.domain.audit.AuditEventRepository;
import com.jokkorm.domain.audit.AuditTargetType;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service d'audit chaîné HMAC-SHA256 — version JokkoRM, inspirée d'eversuite-modern.
 *
 * <p>Pour chaque évent :
 * <ol>
 *   <li>{@code payloadHash} = SHA-256 du payload normalisé</li>
 *   <li>{@code prevEventHash} = {@code hmac} du précédent évent de la même cible</li>
 *   <li>{@code hmac} = HMAC-SHA-256 de {@code payloadHash || prevEventHash || ts} avec
 *       clé dérivée par tenant (HMAC du secret maître avec le slug tenant)</li>
 * </ol>
 *
 * <p>L'écriture se fait dans la même transaction que la mutation métier pour que la chaîne
 * reste cohérente — un rollback métier rollback aussi l'audit.
 *
 * <p>Simplifications par rapport à eversuite-modern :
 * <ul>
 *   <li>Pas de SimulationScope (fonctionnalité workflow absente ici)</li>
 *   <li>Un seul sink (repository). Les sinks additionnels (log CEF, Kafka) viendront
 *       quand on aura des clients demandeurs.</li>
 * </ul>
 */
@Service
public class AuditChainService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditChainService.class);
    private static final String HMAC_ALG = "HmacSHA256";
    private static final String SHA_ALG = "SHA-256";
    private static final String DEV_SECRET = "dev-master-secret-do-not-use-in-prod";

    private final AuditEventRepository repository;
    private final byte[] masterSecret;
    private final Clock clock;

    public AuditChainService(
        AuditEventRepository repository,
        @Value("${jokkorm.audit.master-secret:" + DEV_SECRET + "}") String masterSecret,
        Clock clock) {
        this.repository = repository;
        this.masterSecret = masterSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
        if (DEV_SECRET.equals(masterSecret)) {
            LOG.warn("Audit chain uses the DEV master secret — set JOKKORM_AUDIT_MASTER_SECRET "
                + "before any production deployment.");
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent record(
        AuditAction action, AuditTargetType targetType, UUID targetId,
        String actor, String metadata) {

        TenantId tenant = TenantContext.current();
        Instant ts = clock.instant();
        UUID id = UUID.randomUUID();

        byte[] payloadHash = sha256(
            canonicalPayload(action, targetType, targetId, actor, ts, metadata));

        Optional<AuditEvent> previous = repository.findLast(tenant, targetType, targetId);
        byte[] prevHash = previous.map(AuditEvent::hmac).orElse(null);

        byte[] hmac = hmac(tenant,
            concat(payloadHash, prevHash, ts.toString().getBytes(StandardCharsets.UTF_8)));

        AuditEvent event = new AuditEvent(
            id, tenant, ts, actor, action, targetType, targetId,
            payloadHash, prevHash, hmac, metadata);

        return repository.save(event);
    }

    /**
     * Vérifie que la chaîne d'une cible n'a pas été altérée. Recalcule chaque HMAC et le
     * compare à la valeur stockée.
     *
     * @return index du premier évent incohérent, ou {@code -1} si la chaîne est intègre.
     */
    @Transactional(readOnly = true)
    public int verifyChain(TenantId tenant, AuditTargetType targetType, UUID targetId) {
        List<AuditEvent> chain = repository.findChain(tenant, targetType, targetId);
        byte[] expectedPrev = null;
        for (int i = 0; i < chain.size(); i++) {
            AuditEvent e = chain.get(i);

            byte[] expectedPayloadHash = sha256(canonicalPayload(
                e.action(), e.targetType(), e.targetId(), e.actor(), e.ts(), e.metadata()));
            if (!constantTimeEquals(expectedPayloadHash, e.payloadHash())) {
                return i;
            }

            if (!constantTimeEquals(expectedPrev, e.prevEventHash())) {
                return i;
            }

            byte[] expectedHmac = hmac(tenant,
                concat(expectedPayloadHash, expectedPrev,
                    e.ts().toString().getBytes(StandardCharsets.UTF_8)));
            if (!constantTimeEquals(expectedHmac, e.hmac())) {
                return i;
            }

            expectedPrev = e.hmac();
        }
        return -1;
    }

    // ─── Crypto helpers ──────────────────────────────────────────────────────────────

    private static String canonicalPayload(
        AuditAction action, AuditTargetType targetType, UUID targetId,
        String actor, Instant ts, String metadata) {
        return new StringBuilder(256)
            .append(action.name()).append('|')
            .append(targetType.name()).append('|')
            .append(targetId).append('|')
            .append(actor).append('|')
            .append(ts.toString()).append('|')
            .append(metadata == null ? "" : metadata)
            .toString();
    }

    private byte[] hmac(TenantId tenant, byte[] payload) {
        return hmacRaw(tenantKey(tenant), payload);
    }

    private byte[] tenantKey(TenantId tenant) {
        return hmacRaw(masterSecret,
            ("audit:" + tenant.value()).getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmacRaw(byte[] key, byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(key, HMAC_ALG));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA-256 unavailable in JVM", e);
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance(SHA_ALG).digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in JVM", e);
        }
    }

    private static byte[] concat(byte[] a, byte[] b, byte[] c) {
        int len = a.length + (b == null ? 0 : b.length) + c.length;
        byte[] out = new byte[len];
        int pos = 0;
        System.arraycopy(a, 0, out, pos, a.length); pos += a.length;
        if (b != null) { System.arraycopy(b, 0, out, pos, b.length); pos += b.length; }
        System.arraycopy(c, 0, out, pos, c.length);
        return out;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.length != b.length) return false;
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }
}
