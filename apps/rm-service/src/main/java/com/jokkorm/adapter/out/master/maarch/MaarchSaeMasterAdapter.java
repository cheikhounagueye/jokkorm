package com.jokkorm.adapter.out.master.maarch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jokkorm.adapter.out.master.MasterAdapter;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.AgentIdentity;
import com.jokkorm.domain.master.DestructionAttestation;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MovementEvent;
import com.jokkorm.domain.master.PhysicalLocation;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * Adapter REST qui parle à Maarch RM. Phase 3B — couverture minimale :
 *
 * <ul>
 *   <li><b>fetchByCote</b> : GET {@code /rest/resources?search={cote}} puis extrait
 *       title, classificationPath, DUA, sort final, legal hold.</li>
 *   <li><b>notifyPhysicalLocation</b> : PUT {@code /rest/resources/{resId}/externalInfos}
 *       avec {@code externalId.physical_location} — champ libre que Maarch stocke sans
 *       le modifier.</li>
 *   <li><b>notifyMovement</b> : POST {@code /rest/resources/{resId}/notes} — Maarch a un
 *       canal Notes générique utilisable pour la traçabilité physique.</li>
 *   <li><b>notifyDestructionExecuted</b> : POST {@code /rest/resources/{resId}/attachments}
 *       — le PDF scellé devient une pièce jointe à la ressource.</li>
 *   <li><b>resolveAgent</b> : non implémenté en phase 3B (SSO Keycloak partagé rend ce
 *       lookup optionnel pour un adapter unique ; sera ajouté quand un tenant demandera).
 *       </li>
 * </ul>
 *
 * <p>Authentification : {@code POST /rest/authenticate} retourne un JWT ; cache en mémoire
 * TTL 50 min (Maarch émet 1h). Un 401 force une ré-authentification.
 *
 * <p>Phase 3B stricte : un seul Maarch adressé, pas de config par tenant. Phase 4 promouvra
 * en {@code tenant_master_config} DB-driven.
 *
 * <p>Activation : {@code jokkorm.master.kind=maarch}. Désactive automatiquement le NoopAdapter.
 */
@Component
@ConditionalOnProperty(name = "jokkorm.master.kind", havingValue = "maarch")
@EnableConfigurationProperties(MaarchProperties.class)
public class MaarchSaeMasterAdapter implements MasterAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MaarchSaeMasterAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MaarchProperties config;
    private final HttpClient http;
    private volatile CachedToken token;

    public MaarchSaeMasterAdapter(MaarchProperties config) {
        this.config = config;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.timeoutMs()))
            .build();
        if (config.baseUrl() == null || config.baseUrl().isBlank()) {
            throw new IllegalStateException(
                "jokkorm.master.kind=maarch but jokkorm.master.maarch.base-url is empty");
        }
    }

    @Override
    public String kind() { return "maarch"; }

    /** Maarch Courrier (API /rest/resources), pas Maarch RM : voir le kind « maarchrm ». */
    @Override
    public String displayName() { return "Maarch Courrier"; }

    @Override
    public Optional<MasterArchiveView> fetchByCote(TenantId tenant, Cote cote) {
        try {
            HttpResponse<String> response = authed(HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/rest/resources?search=" + urlEncode(cote.value())))
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .GET());
            if (response.statusCode() == 404 || response.body() == null || response.body().isBlank()) {
                return Optional.empty();
            }
            if (response.statusCode() >= 400) {
                LOG.warn("Maarch fetchByCote failed status={} tenant={} cote={}",
                    response.statusCode(), tenant.value(), cote.value());
                return Optional.empty();
            }
            JsonNode root = JSON.readTree(response.body());
            JsonNode resources = root.path("resources");
            if (!resources.isArray() || resources.isEmpty()) {
                return Optional.empty();
            }
            JsonNode first = resources.get(0);
            return Optional.of(new MasterArchiveView(
                cote,
                first.path("subject").asText(null),
                first.path("destination").asText(null),
                parseIntOrNull(first.path("retentionFrozen")),
                first.path("retentionFinalDisposition").asText(null),
                first.path("retentionHold").asBoolean(false)));
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.warn("Maarch fetchByCote error tenant={} cote={} : {}",
                tenant.value(), cote.value(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<AgentIdentity> resolveAgent(TenantId tenant, String subject) {
        // Phase 3B : SSO partagé via Keycloak rend ce lookup optionnel pour un adapter
        // unique. On retourne empty pour l'instant, à activer quand un tenant le demande.
        return Optional.empty();
    }

    @Override
    public void notifyPhysicalLocation(TenantId tenant, Cote cote, PhysicalLocation location) {
        Optional<Long> resId = resolveResId(cote);
        if (resId.isEmpty()) {
            LOG.warn("[maarch] notifyPhysicalLocation : cote inconnue tenant={} cote={}",
                tenant.value(), cote.value());
            return;
        }
        try {
            String payload = JSON.writeValueAsString(Map.of(
                "externalInfos", Map.of(
                    "physical_location", location.toSingleLine(),
                    "physical_location_updated_by", "jokkorm")));
            HttpResponse<String> response = authed(HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/rest/resources/" + resId.get() + "/externalInfos"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)));
            if (response.statusCode() >= 400) {
                LOG.warn("[maarch] notifyPhysicalLocation failed status={} tenant={} cote={}",
                    response.statusCode(), tenant.value(), cote.value());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.warn("[maarch] notifyPhysicalLocation error tenant={} cote={} : {}",
                tenant.value(), cote.value(), e.getMessage());
        }
    }

    @Override
    public void notifyMovement(TenantId tenant, Cote cote, MovementEvent event) {
        Optional<Long> resId = resolveResId(cote);
        if (resId.isEmpty()) {
            LOG.warn("[maarch] notifyMovement : cote inconnue tenant={} cote={}",
                tenant.value(), cote.value());
            return;
        }
        try {
            String payload = JSON.writeValueAsString(Map.of(
                "note", "[JokkoRM] %s · agent=%s · at=%s · %s".formatted(
                    event.kind(), event.agentSubject(), event.occurredAt(),
                    event.motive() == null ? "" : event.motive())));
            HttpResponse<String> response = authed(HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/rest/resources/" + resId.get() + "/notes"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)));
            if (response.statusCode() >= 400) {
                LOG.warn("[maarch] notifyMovement failed status={} tenant={} cote={}",
                    response.statusCode(), tenant.value(), cote.value());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.warn("[maarch] notifyMovement error tenant={} cote={} : {}",
                tenant.value(), cote.value(), e.getMessage());
        }
    }

    @Override
    public void notifyDestructionExecuted(TenantId tenant, Cote cote, DestructionAttestation attestation) {
        Optional<Long> resId = resolveResId(cote);
        if (resId.isEmpty()) {
            LOG.warn("[maarch] notifyDestructionExecuted : cote inconnue tenant={} cote={}",
                tenant.value(), cote.value());
            return;
        }
        try {
            String payload = JSON.writeValueAsString(Map.of(
                "attachment", Map.of(
                    "resIdMaster", resId.get(),
                    "title", "Attestation destruction " + attestation.attestationId(),
                    "type", "destruction_attestation",
                    "format", "pdf",
                    "encodedFile", Base64.getEncoder().encodeToString(attestation.signedPdf()))));
            HttpResponse<String> response = authed(HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/rest/resources/" + resId.get() + "/attachments"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8)));
            if (response.statusCode() >= 400) {
                LOG.warn("[maarch] notifyDestructionExecuted failed status={} tenant={} cote={} body={}",
                    response.statusCode(), tenant.value(), cote.value(), response.body());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.warn("[maarch] notifyDestructionExecuted error tenant={} cote={} : {}",
                tenant.value(), cote.value(), e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private Optional<Long> resolveResId(Cote cote) {
        try {
            HttpResponse<String> r = authed(HttpRequest.newBuilder()
                .uri(URI.create(config.baseUrl() + "/rest/resources?search=" + urlEncode(cote.value())))
                .GET());
            if (r.statusCode() >= 400 || r.body() == null) return Optional.empty();
            JsonNode first = JSON.readTree(r.body()).path("resources");
            if (!first.isArray() || first.isEmpty()) return Optional.empty();
            long id = first.get(0).path("resId").asLong(0L);
            return id == 0L ? Optional.empty() : Optional.of(id);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private HttpResponse<String> authed(HttpRequest.Builder builder)
        throws IOException, InterruptedException {
        HttpRequest req = builder.header("Authorization", "Bearer " + token()).build();
        HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            token = null;   // force refresh
            HttpRequest retry = builder.header("Authorization", "Bearer " + token()).build();
            response = http.send(retry, HttpResponse.BodyHandlers.ofString());
        }
        return response;
    }

    private String token() throws IOException, InterruptedException {
        CachedToken current = this.token;
        if (current != null && current.isStillFresh()) {
            return current.value;
        }
        String body = JSON.writeValueAsString(Map.of(
            "login", config.login(), "password", config.password()));
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(config.baseUrl() + "/rest/authenticate"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .timeout(Duration.ofMillis(config.timeoutMs()))
            .build();
        HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("Maarch authenticate failed status=" + response.statusCode());
        }
        // Maarch retourne soit un JWT nu dans le body, soit { "token": "..." }. On gère les 2.
        String raw = response.body().trim();
        String jwt;
        if (raw.startsWith("{")) {
            JsonNode tree = JSON.readTree(raw);
            jwt = tree.path("token").asText(null);
            if (jwt == null || jwt.isBlank()) jwt = raw;
        } else {
            jwt = raw.replace("\"", "");
        }
        CachedToken fresh = new CachedToken(jwt, System.currentTimeMillis() + 50 * 60 * 1000L);
        this.token = fresh;
        return jwt;
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static Integer parseIntOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        if (node.isInt()) return node.asInt();
        try { return Integer.parseInt(node.asText()); } catch (NumberFormatException e) { return null; }
    }

    /** Wrapper classes pour les tests qui veulent lire les propriétés. */
    public MaarchProperties config() { return config; }

    private record CachedToken(String value, long expiresAtEpochMs) {
        boolean isStillFresh() { return System.currentTimeMillis() < expiresAtEpochMs; }
    }
}
