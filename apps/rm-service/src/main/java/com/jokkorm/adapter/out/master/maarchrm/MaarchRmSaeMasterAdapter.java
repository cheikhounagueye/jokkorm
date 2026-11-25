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
import com.jokkorm.domain.master.MasterArchiveRef;
import com.jokkorm.domain.master.MasterEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
            "frozen".equals(text(a, "status")),
            pageUrl(text(a, "archiveId")),
            text(a, "originatorOrgRegNumber"),
            date(text(a, "disposalDate")),
            text(a, "classificationLevel"),
            text(a, "finalDisposition")));
    }

    /**
     * Page de l'archive dans l'interface Maarch (description, métadonnées de gestion, cycle de
     * vie). Maarch y contrôle lui-même qui peut voir l'archive.
     */
    String pageUrl(String archiveId) {
        if (archiveId == null || archiveId.isBlank()) return null;
        String segment = URLEncoder.encode(archiveId, StandardCharsets.UTF_8).replace("+", "%20");
        return config.publicUrl() + "/recordsManagement/archiveDescription/" + segment;
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

    // ─── Journal de Maarch (veilleur, étape 1) ───────────────────────────────────
    //
    // Pièges Maarch 2.7 (vérifiés sur instance réelle le 2026-11-23) :
    //  - tous les paramètres doivent être envoyés, même vides, sinon 400 ;
    //  - dates comparées en UTC sans fuseau (un « +01:00 » est ignoré) ;
    //  - résultats du plus récent au plus ancien, la limite garde les plus récents ;
    //  - maxDate reçoit 23 h 59 min 59 s de plus (filtre pensé « jusqu'à tel jour ») ;
    //  - compte sans le droit lifeCycle/event/readSearch : 403.

    private static final String JOURNAL = "/lifeCycle/event/Search";
    static final int JOURNAL_PAGE = 500;
    /** Taille de page du journal ; réduite par les tests pour éprouver la pagination. */
    int journalPage = JOURNAL_PAGE;
    static final int JOURNAL_MAX_PAGES = 40;
    private static final DateTimeFormatter MAARCH_UTC =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);
    private static final Duration MAARCH_MAXDATE_SHIFT = Duration.ofHours(23).plusMinutes(59).plusSeconds(59);
    private static final Map<MasterEvent.Kind, String> EVENT_TYPES = Map.of(
        MasterEvent.Kind.METADATA_MODIFIED, "recordsManagement/metadataModification",
        MasterEvent.Kind.FROZEN, "recordsManagement/freeze",
        MasterEvent.Kind.UNFROZEN, "recordsManagement/unfreeze",
        MasterEvent.Kind.DEPOSITED, "recordsManagement/deposit");
    private static final String JOURNAL_FORBIDDEN = "Maarch RM refuse la lecture du journal (HTTP 403) : il manque "
        + "au compte de JokkoRM le droit lifeCycle/event/readSearch (script jokkorm-setup-veilleur.sh)";

    @Override
    public boolean publishesEvents(TenantId tenant) {
        return true;
    }

    @Override
    public List<MasterEvent> readEvents(TenantId tenant, Instant from, Set<MasterEvent.Kind> kinds) {
        Map<String, MasterEvent> all = new LinkedHashMap<>();
        for (MasterEvent.Kind kind : kinds) {
            String type = EVENT_TYPES.get(kind);
            if (type == null) continue;
            for (MasterEvent e : readEventType(kind, type, from)) all.putIfAbsent(e.id(), e);
        }
        List<MasterEvent> sorted = new ArrayList<>(all.values());
        sorted.sort(Comparator.comparing(MasterEvent::occurredAt).thenComparing(MasterEvent::id));
        return sorted;
    }

    /**
     * Une requête par type (sinon les contrôles d'intégrité périodiques, un par archive, rempliraient
     * les pages). Page pleine : on redemande les plus anciens en bornant maxDate, et on vérifie que
     * le plus ancien déjà lu revient bien. Sinon Maarch a changé de comportement : on s'arrête sans
     * rien perdre (le curseur ne bouge pas).
     */
    private List<MasterEvent> readEventType(MasterEvent.Kind kind, String type, Instant from) {
        List<MasterEvent> events = new ArrayList<>();
        Instant upper = null;
        String mustReappear = null;
        for (int page = 0; page < JOURNAL_MAX_PAGES; page++) {
            Map<String, String> p = new LinkedHashMap<>();
            p.put("eventType", type);
            p.put("objectClass", "recordsManagement/archive");
            p.put("objectId", "");
            p.put("minDate", MAARCH_UTC.format(from));
            p.put("maxDate", upper == null ? "" : MAARCH_UTC.format(upper.minus(MAARCH_MAXDATE_SHIFT)));
            p.put("org", "");
            p.put("maxResults", String.valueOf(journalPage));
            JsonNode arr = send("GET", JOURNAL + "?" + query(p), null, false, JOURNAL_FORBIDDEN);
            if (arr == null || !arr.isArray()) {
                throw new MasterUnreachableException("Réponse inattendue du journal Maarch RM : une liste était attendue");
            }
            boolean reappeared = mustReappear == null;
            MasterEvent previous = null;
            for (JsonNode n : arr) {
                MasterEvent e = event(kind, n);
                if (e == null) continue;
                if (previous != null && e.occurredAt().isAfter(previous.occurredAt())) {
                    throw new MasterUnreachableException("Journal Maarch RM : ordre des événements inattendu, "
                        + "rattrapage arrêté sans rien perdre (voir docs/adapters/maarchrm.md, montées de version)");
                }
                if (e.id().equals(mustReappear)) reappeared = true;
                events.add(e);
                previous = e;
            }
            if (!reappeared) {
                throw new MasterUnreachableException("Journal Maarch RM : la borne maxDate ne se comporte plus comme "
                    + "prévu, rattrapage arrêté sans rien perdre (voir docs/adapters/maarchrm.md, montées de version)");
            }
            if (arr.size() < journalPage) return events;
            if (previous == null) {
                throw new MasterUnreachableException("Journal Maarch RM : page pleine mais illisible");
            }
            Instant next = previous.occurredAt().truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
            if (upper != null && !next.isBefore(upper)) {
                throw new MasterUnreachableException("Journal Maarch RM : plus de " + journalPage + " événements « "
                    + type + " » dans la même seconde, rattrapage impossible");
            }
            upper = next;
            mustReappear = previous.id();
        }
        throw new MasterUnreachableException("Journal Maarch RM : plus de " + (journalPage * JOURNAL_MAX_PAGES)
            + " événements « " + type + " » à rattraper d'un coup (voir docs/adapters/maarchrm.md)");
    }

    /** Type, archive, qui, quand. Jamais eventInfo ni description : ils peuvent porter des données personnelles. */
    private MasterEvent event(MasterEvent.Kind kind, JsonNode n) {
        String id = text(n, "eventId");
        Instant at = instant(text(n, "timestamp"));
        String objectId = text(n, "objectId");
        if (!notBlank(id) || at == null || !notBlank(objectId)) {
            LOG.warn("[maarchrm] événement de journal incomplet ignoré ({})", id);
            return null;
        }
        String actor = text(n, "accountName");
        boolean byConnector = actor != null && actor.equalsIgnoreCase(config.connectorAccountName());
        return new MasterEvent(id, kind, at, objectId, actor, byConnector);
    }

    static Instant instant(String value) {
        if (!notBlank(value)) return null;
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    @Override
    public Optional<String> contentLinkOrigin(TenantId tenant) {
        return config.publicUrl().isEmpty() ? Optional.empty() : Optional.of(config.publicUrl());
    }

    @Override
    public Optional<MasterArchiveRef> describe(TenantId tenant, String masterArchiveId) {
        // La réponse contient toute la description (données personnelles comprises) : jamais journalisée.
        JsonNode meta = send("GET", "/recordsManagement/archive/Metadata/"
            + URLEncoder.encode(masterArchiveId, StandardCharsets.UTF_8), null, true, null);
        if (meta == null || !meta.isObject()) return Optional.empty();
        String cote = text(meta, "archiverArchiveId");
        if (cote == null || !isValidCote(cote)) return Optional.empty();
        JsonNode field = meta.path("description").path(config.locationField());
        return Optional.of(new MasterArchiveRef(cote, field.isMissingNode() || field.isNull() ? null : field.asText(),
            text(meta, "archivalProfileReference"), notBlank(text(meta, "parentArchiveId"))));
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
        return send(method, pathAndQuery, jsonBody, false, null);
    }

    /**
     * @param absentIsNull     vrai : une archive inconnue (404) ou invisible pour ce compte
     *                         (« accessDenied » laabs) donne {@code null} au lieu d'une panne
     * @param forbiddenMessage message en cas de 403 en lecture (droit manquant), sinon message général
     */
    private JsonNode send(String method, String pathAndQuery, String jsonBody, boolean absentIsNull,
                          String forbiddenMessage) {
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
        String laabsException = response.headers().firstValue("X-Laabs-Exception").orElse("");
        if (absentIsNull && (status == 404 || laabsException.contains("accessDenied"))) {
            return null;
        }
        if (status == 401) {
            throw new MasterUnreachableException("Maarch RM refuse la connexion (HTTP 401) : vérifier le compte");
        }
        if (status == 403 && jsonBody == null) {
            throw new MasterUnreachableException(forbiddenMessage != null ? forbiddenMessage
                : "Maarch RM refuse la connexion (HTTP 403) : vérifier le compte et son rattachement au service d'archives");
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
