package com.jokkorm.adapter.out.master.maarchrm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jokkorm.adapter.out.master.MasterAdapter;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.AgentIdentity;
import com.jokkorm.domain.master.ArchiveSearchCriteria;
import com.jokkorm.domain.master.ArchiveSearchResult;
import com.jokkorm.domain.master.DestructionAttestation;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.MovementEvent;
import com.jokkorm.domain.master.PhysicalLocation;
import com.jokkorm.domain.record.Cote;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Maître = Maarch RM 2.x, par son API JSON (noyau « service » de laabs).
 *
 * <p>Ce que Maarch RM détient et que JokkoRM lit (patterns 01-03) : intitulé, service
 * producteur, profil d'archivage, durée de conservation, sort final, gel. La cote partagée
 * (ADR-0004) est l'identifiant attribué par le service d'archives : {@code archiverArchiveId}.
 *
 * <p>Pièges Maarch traités ici :
 * <ul>
 *   <li>sans le jeton « service client » dans le User-Agent, Maarch répond <b>200 en HTML</b>
 *       (son interface web) : traité comme une panne, jamais comme « cote inconnue » ;</li>
 *   <li>les résultats sont filtrés par le rattachement du compte : un compte sans service
 *       voit 0 archive. Le contrôle au démarrage le signale ;</li>
 *   <li>Maarch concatène {@code archiveName} dans sa requête : le texte cherché est épuré.</li>
 * </ul>
 *
 * <p>Tranche 2a : l'état physique (adresse de terrain + statut, sans code-barres) est écrit
 * dans un champ de description dédié de l'archive Maarch, par la file d'envoi
 * ({@link #publishPhysicalStatus}). Maarch remplace toute la description et remet à vide
 * {@code originatorArchiveId} / {@code originatingDate} s'ils ne sont pas renvoyés : on relit
 * donc l'archive juste avant d'écrire et on renvoie chaque champ tel quel. Une modification
 * faite dans Maarch entre cette lecture et l'écriture serait perdue (fenêtre de quelques ms).
 * Les {@code notify*} historiques restent journalisés seulement.
 *
 * <p>Activé dès que {@code jokkorm.master.maarchrm.base-url} est renseignée ; le routage
 * par tenant ({@code jokkorm.master.tenants.<tenant>=maarchrm}) décide qui l'utilise.
 */
@Component
@ConditionalOnExpression("'${jokkorm.master.maarchrm.base-url:}' != ''")
@EnableConfigurationProperties(MaarchRmProperties.class)
public class MaarchRmSaeMasterAdapter implements MasterAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(MaarchRmSaeMasterAdapter.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ARCHIVES = "/recordsManagement/archives";

    private final MaarchRmProperties config;
    private final Clock clock;
    private final HttpClient http;

    public MaarchRmSaeMasterAdapter(MaarchRmProperties config, Clock clock) {
        if (config.baseUrl().isBlank()) {
            throw new IllegalStateException("jokkorm.master.maarchrm.base-url est vide");
        }
        config.mode(); // valide auth-mode au démarrage
        this.config = config;
        this.clock = clock;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(config.timeoutMs()))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    @Override
    public String kind() { return "maarchrm"; }

    @Override
    public String displayName() { return "Maarch RM"; }

    // ─── Lectures ────────────────────────────────────────────────────────────────

    @Override
    public Optional<MasterArchiveView> fetchByCote(TenantId tenant, Cote cote) {
        JsonNode list = getArray(ARCHIVES + "?" + query(Map.of(
            "archiverArchiveId", cote.value(), "maxResults", "5")));
        List<JsonNode> hits = new ArrayList<>();
        list.forEach(a -> { if (cote.value().equals(a.path("archiverArchiveId").asText())) hits.add(a); });
        if (hits.isEmpty()) return Optional.empty();
        if (hits.size() > 1) {
            LOG.warn("[maarchrm] {} archives portent la cote {} (tenant {}) : la première est retenue",
                hits.size(), cote.value(), tenant.value());
        }
        JsonNode a = hits.get(0);
        return Optional.of(new MasterArchiveView(
            cote,
            text(a, "archiveName"),
            classificationPath(a),
            durationYears(text(a, "retentionDuration")),
            finalFate(text(a, "finalDisposition")),
            "frozen".equals(text(a, "status"))));
    }

    @Override
    public List<ArchiveSearchResult> search(TenantId tenant, ArchiveSearchCriteria criteria) {
        if (!criteria.hasAnyCriteria()) return List.of();
        Map<String, String> params = new LinkedHashMap<>();
        if (notBlank(criteria.cote())) params.put("archiverArchiveId", criteria.cote().trim());
        String name = sanitizeName(criteria.freeText());
        if (!name.isEmpty()) params.put("archiveName", name);
        if (notBlank(criteria.service())) params.put("originatorOrgRegNumber", criteria.service().trim());
        if (notBlank(criteria.typologie())) params.put("profileReference", criteria.typologie().trim());
        if (criteria.dateFrom() != null) params.put("originatingStartDate", criteria.dateFrom().toString());
        if (criteria.dateTo() != null) params.put("originatingEndDate", criteria.dateTo().toString());
        // classificationPath : pas de critère équivalent côté Maarch RM, ignoré.
        if (params.isEmpty()) return List.of();
        params.put("maxResults", String.valueOf(criteria.limit()));

        LocalDate today = LocalDate.now(clock);
        List<ArchiveSearchResult> results = new ArrayList<>();
        for (JsonNode a : getArray(ARCHIVES + "?" + query(params))) {
            String cote = text(a, "archiverArchiveId");
            if (cote == null || !isValidCote(cote)) continue; // sans cote partagée, pas de lien physique possible
            LocalDate disposal = date(text(a, "disposalDate"));
            LocalDate comDate = date(text(a, "accessRuleComDate"));
            results.add(new ArchiveSearchResult(
                cote,
                text(a, "archiveName"),
                text(a, "originatorOrgRegNumber"),
                text(a, "archivalProfileReference"),
                classificationPath(a),
                date(text(a, "originatingDate")),
                null,
                disposal == null ? null : (int) Math.max(0, ChronoUnit.YEARS.between(today, disposal)),
                comDate == null ? null : !comDate.isAfter(today)));
        }
        return results;
    }

    @Override
    public Optional<AgentIdentity> resolveAgent(TenantId tenant, String subject) {
        return Optional.empty();
    }

    // ─── Écritures : tranche suivante ────────────────────────────────────────────

    @Override
    public void notifyPhysicalLocation(TenantId tenant, Cote cote, PhysicalLocation location) {
        LOG.debug("[maarchrm] emplacement de {} : transmis par la file d'envoi", cote.value());
    }

    @Override
    public void notifyMovement(TenantId tenant, Cote cote, MovementEvent event) {
        LOG.debug("[maarchrm] mouvement {} de {} : état transmis par la file d'envoi", event.kind(), cote.value());
    }

    // ─── Écriture de l'état physique (tranche 2a) ────────────────────────────────

    @Override
    public boolean acceptsPhysicalStatus(TenantId tenant) {
        return true;
    }

    @Override
    public void publishPhysicalStatus(TenantId tenant, Cote cote, com.jokkorm.domain.master.PhysicalStatus status) {
        JsonNode hits = getArray(ARCHIVES + "?" + query(Map.of("archiverArchiveId", cote.value(), "maxResults", "2")));
        String archiveId = null;
        for (JsonNode a : hits) {
            if (cote.value().equals(a.path("archiverArchiveId").asText())) {
                archiveId = a.path("archiveId").asText();
                break;
            }
        }
        if (archiveId == null || archiveId.isBlank()) {
            throw new com.jokkorm.domain.master.MasterRejectedException(
                "Cote " + cote.value() + " inconnue de Maarch RM (ou non visible par le compte de connexion)");
        }
        // Relire juste avant d'écrire : Maarch remplace tout ce qu'on lui envoie.
        JsonNode meta = getJson("/recordsManagement/archive/Metadata/" + URLEncoder.encode(archiveId, StandardCharsets.UTF_8));
        com.fasterxml.jackson.databind.node.ObjectNode description = meta.path("description").isObject()
            ? ((com.fasterxml.jackson.databind.node.ObjectNode) meta.path("description")).deepCopy()
            : JSON.createObjectNode();
        description.put(config.locationField(), status.line());
        com.fasterxml.jackson.databind.node.ObjectNode body = JSON.createObjectNode();
        body.put("archiveId", archiveId);
        body.set("originatorArchiveId", meta.path("originatorArchiveId"));
        body.set("archiverArchiveId", meta.path("archiverArchiveId"));
        body.set("archiveName", meta.path("archiveName"));
        body.set("originatingDate", meta.path("originatingDate"));
        body.set("description", description);
        JsonNode result = send("PUT", "/recordsManagement/archives/Metadata", body.toString());
        if (result.isBoolean() && !result.asBoolean()) {
            throw new com.jokkorm.domain.master.MasterRejectedException("Maarch RM a refusé la modification de " + cote.value());
        }
    }

    @Override
    public void notifyDestructionExecuted(TenantId tenant, Cote cote, DestructionAttestation attestation) {
        LOG.info("[maarchrm] destruction de {} non transmise à Maarch RM (écriture prévue en tranche 2)", cote.value());
    }

    // ─── Contrôle au démarrage ───────────────────────────────────────────────────

    /** Dit tout de suite si la connexion est mauvaise, au lieu de laisser des panneaux vides. */
    @EventListener(ApplicationReadyEvent.class)
    public void probe() {
        try {
            int visible = getJson(ARCHIVES + "/Count").asInt(-1);
            if (visible == 0) {
                LOG.warn("[maarchrm] {} joignable mais AUCUNE archive visible pour {} : ce compte est-il "
                    + "rattaché au service d'archives (position dans une organisation) ?",
                    config.baseUrl(), config.principal());
            } else {
                LOG.info("[maarchrm] {} joignable : {} archive(s) visible(s) pour {}",
                    config.baseUrl(), visible, config.principal());
            }
        } catch (MasterUnreachableException e) {
            LOG.error("[maarchrm] {} injoignable au démarrage : {}", config.baseUrl(), e.getMessage());
        }
    }

    // ─── HTTP ────────────────────────────────────────────────────────────────────

    private JsonNode getJson(String pathAndQuery) {
        return send("GET", pathAndQuery, null);
    }

    private JsonNode send(String method, String pathAndQuery, String jsonBody) {
        HttpRequest.Builder req = HttpRequest.newBuilder()
            .uri(URI.create(config.baseUrl() + pathAndQuery))
            .timeout(Duration.ofMillis(config.timeoutMs()))
            .header("Accept", "application/json")
            .header("User-Agent", "JokkoRM/1.0 (" + config.serviceClientToken() + ")")
            .method(method, jsonBody == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (jsonBody != null) req.header("Content-Type", "application/json");
        if (config.mode() == MaarchRmProperties.AuthMode.BASIC) {
            String pair = (config.login() == null ? "" : config.login()) + ":" + (config.password() == null ? "" : config.password());
            req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8)));
        } else {
            req.header("Cookie", "LAABS-AUTH=" + URLEncoder.encode(
                config.serviceToken() == null ? "" : config.serviceToken(), StandardCharsets.UTF_8));
        }

        HttpResponse<String> response;
        try {
            response = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new MasterUnreachableException("Maarch RM injoignable (" + e.getClass().getSimpleName() + ")", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MasterUnreachableException("Appel à Maarch RM interrompu", e);
        }

        int status = response.statusCode();
        if (status == 401) {
            throw new MasterUnreachableException("Maarch RM refuse la connexion (HTTP 401) : vérifier le compte");
        }
        if (status == 403 && jsonBody == null) {
            throw new MasterUnreachableException("Maarch RM refuse la connexion (HTTP 403) : vérifier le compte et son rattachement au service d'archives");
        }
        if (jsonBody != null && status >= 400 && status < 500 && status != 408 && status != 429) {
            // Écriture refusée par Maarch (champ inconnu du profil, droits…) : refus durable.
            throw new com.jokkorm.domain.master.MasterRejectedException(
                "Maarch RM refuse l'écriture (HTTP " + status + ") : " + maarchMessage(response.body()));
        }
        if (status >= 300) {
            throw new MasterUnreachableException("Maarch RM a répondu HTTP " + status);
        }
        // Piège laabs : une exception sans code HTTP part en « 200 {} », le vrai message est dans cet en-tête.
        String laabsException = response.headers().firstValue("X-Laabs-Exception").orElse("");
        if (!laabsException.isBlank()) {
            String detail = laabsException.length() > 300 ? laabsException.substring(0, 300) + "…" : laabsException;
            if (jsonBody != null) {
                throw new com.jokkorm.domain.master.MasterRejectedException("Maarch RM signale une erreur : " + detail);
            }
            throw new MasterUnreachableException("Maarch RM signale une erreur : " + detail);
        }
        String type = response.headers().firstValue("Content-Type").orElse("");
        if (!type.contains("json") && !type.contains("javascript")) {
            throw new MasterUnreachableException("Maarch RM a répondu en " + (type.isEmpty() ? "format inconnu" : type)
                + " au lieu de JSON : le User-Agent doit contenir le SERVICE_CLIENT_TOKEN du vhost (« "
                + config.serviceClientToken() + " » configuré)");
        }
        try {
            return JSON.readTree(response.body());
        } catch (IOException e) {
            throw new MasterUnreachableException("Réponse JSON illisible de Maarch RM", e);
        }
    }

    /** Le message d'erreur Maarch ({"message": …}) ou le début de la réponse. */
    private static String maarchMessage(String body) {
        if (body == null || body.isBlank()) return "sans détail";
        try {
            JsonNode n = JSON.readTree(body);
            if (n.hasNonNull("message")) return n.get("message").asText();
        } catch (IOException ignored) {
            // réponse non JSON
        }
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }

    private JsonNode getArray(String pathAndQuery) {
        JsonNode node = getJson(pathAndQuery);
        if (!node.isArray()) {
            throw new MasterUnreachableException("Réponse inattendue de Maarch RM : une liste d'archives était attendue");
        }
        return node;
    }

    private static String query(Map<String, String> params) {
        return params.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    }

    // ─── Correspondances Maarch → JokkoRM ────────────────────────────────────────

    /** « DIP/DOSIP » : service producteur puis profil d'archivage. */
    private static String classificationPath(JsonNode a) {
        String path = java.util.stream.Stream.of(text(a, "originatorOrgRegNumber"), text(a, "archivalProfileReference"))
            .filter(MaarchRmSaeMasterAdapter::notBlank)
            .collect(Collectors.joining("/"));
        return path.isEmpty() ? null : path;
    }

    /** ISO 8601 ({@code P90Y}, {@code P6M}) → années entières ; null si absente ou illisible. */
    static Integer durationYears(String iso) {
        if (!notBlank(iso)) return null;
        try {
            Period p = Period.parse(iso);
            return (int) (p.toTotalMonths() / 12);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Sort final Maarch (preservation / destruction) ; pas d'équivalent du versement. */
    static String finalFate(String disposition) {
        if (disposition == null) return null;
        return switch (disposition) {
            case "destruction" -> "DESTRUCTION";
            case "preservation" -> "CONSERVATION";
            default -> null;
        };
    }

    /** Maarch insère archiveName tel quel entre jokers : on retire ce qui peut casser la requête. */
    static String sanitizeName(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("['\"*\\\\%;\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > 100 ? cleaned.substring(0, 100) : cleaned;
    }

    private static boolean isValidCote(String value) {
        try {
            Cote.of(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static LocalDate date(String value) {
        if (!notBlank(value) || value.length() < 10) return null;
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
