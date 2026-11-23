package com.jokkorm.adapter.out.master.maarchrm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.ArchiveSearchCriteria;
import com.jokkorm.domain.master.MasterRejectedException;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.PhysicalStatus;
import com.jokkorm.domain.record.Cote;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Connecteur Maarch RM contre WireMock, sur une réponse réelle enregistrée depuis une
 * instance Maarch RM 2.7 (archive « CONNU », cote FR094080_2026_AE0002).
 */
class MaarchRmSaeMasterAdapterTest {

    private static final TenantId TENANT = TenantId.of("acme");
    private static final Cote COTE = Cote.of("FR094080_2026_AE0002");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);
    private static WireMockServer wireMock;
    private static String realResponse;

    private MaarchRmSaeMasterAdapter adapter;

    @BeforeAll
    static void start() throws IOException {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
        try (InputStream in = MaarchRmSaeMasterAdapterTest.class.getResourceAsStream("/maarchrm/archives-by-cote.json")) {
            realResponse = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @AfterAll
    static void stop() {
        if (wireMock != null) wireMock.stop();
    }

    @BeforeEach
    void setup() {
        wireMock.resetAll();
        adapter = new MaarchRmSaeMasterAdapter(new MaarchRmProperties(
            wireMock.baseUrl(), "service", "basic", "ccharles", "maarch", null, 2000), CLOCK);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    @Test
    @DisplayName("fetchByCote : la réponse réelle de Maarch RM devient la vue maître")
    void maps_real_maarch_archive() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives"))
            .withQueryParam("archiverArchiveId", equalTo(COTE.value()))
            .willReturn(json(realResponse)));

        var view = adapter.fetchByCote(TENANT, COTE).orElseThrow();

        assertThat(view.title()).isEqualTo("CONNU");
        assertThat(view.classificationPath()).isEqualTo("DIP/DOSIP");
        assertThat(view.duaYears()).isEqualTo(90);
        assertThat(view.finalFate()).isEqualTo("DESTRUCTION");
        assertThat(view.legalHold()).isFalse();
    }

    @Test
    @DisplayName("appel API : User-Agent porteur du jeton service et authentification Basic")
    void sends_service_user_agent_and_basic_auth() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives")).willReturn(json("[]")));

        adapter.fetchByCote(TENANT, COTE);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/recordsManagement/archives"))
            .withHeader("User-Agent", containing("service"))
            .withHeader("Accept", equalTo("application/json"))
            .withHeader("Authorization", equalTo("Basic Y2NoYXJsZXM6bWFhcmNo")));
    }

    @Test
    @DisplayName("mode compte de service : jeton en cookie LAABS-AUTH, pas de Basic")
    void service_token_mode_uses_cookie() {
        adapter = new MaarchRmSaeMasterAdapter(new MaarchRmProperties(
            wireMock.baseUrl(), "service", "service-token", null, null, "abc+/=", 2000), CLOCK);
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives")).willReturn(json("[]")));

        adapter.fetchByCote(TENANT, COTE);

        wireMock.verify(getRequestedFor(urlPathEqualTo("/recordsManagement/archives"))
            .withHeader("Cookie", equalTo("LAABS-AUTH=abc%2B%2F%3D"))
            .withoutHeader("Authorization"));
    }

    @Test
    @DisplayName("liste vide : cote inconnue du maître (réponse valide)")
    void empty_list_is_unknown_cote() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives")).willReturn(json("[]")));

        assertThat(adapter.fetchByCote(TENANT, COTE)).isEmpty();
    }

    @Test
    @DisplayName("statut frozen : gel actif côté maître")
    void frozen_status_is_legal_hold() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives"))
            .willReturn(json(realResponse.replace("\"preserved\"", "\"frozen\""))));

        assertThat(adapter.fetchByCote(TENANT, COTE).orElseThrow().legalHold()).isTrue();
    }

    @Test
    @DisplayName("HTML en 200 (jeton absent du User-Agent) : panne, jamais « inconnue »")
    void html_page_is_unreachable_not_unknown() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives")).willReturn(aResponse()
            .withStatus(200).withHeader("Content-Type", "text/html; charset=utf-8")
            .withBody("<!DOCTYPE html><html></html>")));

        assertThatThrownBy(() -> adapter.fetchByCote(TENANT, COTE))
            .isInstanceOf(MasterUnreachableException.class)
            .hasMessageContaining("SERVICE_CLIENT_TOKEN");
    }

    @Test
    @DisplayName("401 : panne d'authentification remontée")
    void unauthorized_is_unreachable() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives")).willReturn(aResponse()
            .withStatus(401).withHeader("Content-Type", "application/json")
            .withBody("{\"message\":\"Missing authentication credential\",\"code\":401}")));

        assertThatThrownBy(() -> adapter.fetchByCote(TENANT, COTE))
            .isInstanceOf(MasterUnreachableException.class)
            .hasMessageContaining("401");
    }

    @Test
    @DisplayName("délai dépassé : panne remontée")
    void timeout_is_unreachable() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives"))
            .willReturn(json("[]").withFixedDelay(3000)));

        assertThatThrownBy(() -> adapter.fetchByCote(TENANT, COTE))
            .isInstanceOf(MasterUnreachableException.class);
    }

    @Test
    @DisplayName("deux archives pour la même cote : la première est retenue")
    void duplicate_cote_takes_first() {
        String two = "[" + realResponse.substring(1, realResponse.lastIndexOf(']')) + ","
            + realResponse.substring(1, realResponse.lastIndexOf(']')).replace("\"CONNU\"", "\"DOUBLON\"") + "]";
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives")).willReturn(json(two)));

        assertThat(adapter.fetchByCote(TENANT, COTE).orElseThrow().title()).isEqualTo("CONNU");
    }

    @Test
    @DisplayName("search : critères traduits, texte épuré, cote et reste à conserver mappés")
    void search_maps_criteria_and_results() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives")).willReturn(json(realResponse)));

        var results = adapter.search(TENANT, new ArchiveSearchCriteria(
            "CON'NU*", "DIP", null, null, null, null, null, 10));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/recordsManagement/archives"))
            .withQueryParam("archiveName", equalTo("CON NU"))
            .withQueryParam("originatorOrgRegNumber", equalTo("DIP"))
            .withQueryParam("maxResults", equalTo("10")));
        assertThat(results).hasSize(1);
        var r = results.get(0);
        assertThat(r.cote()).isEqualTo("FR094080_2026_AE0002");
        assertThat(r.service()).isEqualTo("DIP");
        assertThat(r.typologie()).isEqualTo("DOSIP");
        assertThat(r.duaRemainingYears()).isEqualTo(89);
    }

    @Test
    @DisplayName("search sans critère : aucun appel au maître")
    void search_without_criteria_does_not_call() {
        assertThat(adapter.search(TENANT, new ArchiveSearchCriteria(
            null, null, null, null, null, null, null, 10))).isEmpty();
        wireMock.verify(0, getRequestedFor(urlPathEqualTo("/recordsManagement/archives")));
    }

    @Test
    @DisplayName("durées ISO et sorts finaux")
    void durations_and_fates() {
        assertThat(MaarchRmSaeMasterAdapter.durationYears("P90Y")).isEqualTo(90);
        assertThat(MaarchRmSaeMasterAdapter.durationYears("P18M")).isEqualTo(1);
        assertThat(MaarchRmSaeMasterAdapter.durationYears("n'importe quoi")).isNull();
        assertThat(MaarchRmSaeMasterAdapter.finalFate("preservation")).isEqualTo("CONSERVATION");
        assertThat(MaarchRmSaeMasterAdapter.finalFate("autre")).isNull();
    }

    // ─── Écriture de l'état physique (tranche 2a) ─────────────────────────────

    /** Métadonnées synthétiques (aucune donnée réelle) d'une archive Maarch. */
    private static final String METADATA = """
        {"archiveId":"arc-1","archiverArchiveId":"FR094080_2026_AE0002","originatorArchiveId":"123",
         "archiveName":"Dossier test","originatingDate":"2026-11-04","status":"preserved",
         "description":{"empid":"000","service":"RH","adresses":[{"ville":"Dakar"}]}}""";

    private void stubArchiveAndMetadata() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives"))
            .withQueryParam("archiverArchiveId", equalTo(COTE.value()))
            .willReturn(json("[{\"archiveId\":\"arc-1\",\"archiverArchiveId\":\"FR094080_2026_AE0002\"}]")));
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archive/Metadata/arc-1")).willReturn(json(METADATA)));
    }

    private static final PhysicalStatus STATUS = new PhysicalStatus(
        "SALLE-A · Épi E1 · rang 3 · face A · Travée T03 · Tablette 2 · Emplacement 01", "en magasin", "23/11/2026 14:02");

    @Test
    @DisplayName("Écriture : relit l'archive et renvoie cote, date, titre et description intacts, plus le champ de localisation")
    void publish_rewrites_every_field_plus_location() {
        stubArchiveAndMetadata();
        wireMock.stubFor(put(urlPathEqualTo("/recordsManagement/archives/Metadata")).willReturn(json("true")));

        adapter.publishPhysicalStatus(TENANT, COTE, STATUS);

        wireMock.verify(putRequestedFor(urlPathEqualTo("/recordsManagement/archives/Metadata"))
            .withHeader("User-Agent", containing("service"))
            .withRequestBody(equalToJson("""
                {"archiveId":"arc-1","originatorArchiveId":"123","archiverArchiveId":"FR094080_2026_AE0002",
                 "archiveName":"Dossier test","originatingDate":"2026-11-04",
                 "description":{"empid":"000","service":"RH","adresses":[{"ville":"Dakar"}],
                   "localisation_physique":"SALLE-A · Épi E1 · rang 3 · face A · Travée T03 · Tablette 2 · Emplacement 01 — en magasin (JokkoRM, 23/11/2026 14:02)"}}""")));
    }

    @Test
    @DisplayName("Écriture refusée par Maarch (champ absent du profil…) : refus durable, avec le message de Maarch")
    void publish_rejected_by_maarch() {
        stubArchiveAndMetadata();
        wireMock.stubFor(put(urlPathEqualTo("/recordsManagement/archives/Metadata")).willReturn(aResponse()
            .withStatus(400).withHeader("Content-Type", "application/json")
            .withBody("{\"message\":\"Invalid description field localisation_physique\",\"code\":400}")));

        assertThatThrownBy(() -> adapter.publishPhysicalStatus(TENANT, COTE, STATUS))
            .isInstanceOf(MasterRejectedException.class)
            .hasMessageContaining("localisation_physique");
    }

    @Test
    @DisplayName("Écriture : cote inconnue de Maarch → refus durable ; Maarch en panne → panne passagère")
    void publish_unknown_cote_or_outage() {
        wireMock.stubFor(get(urlPathEqualTo("/recordsManagement/archives")).willReturn(json("[]")));
        assertThatThrownBy(() -> adapter.publishPhysicalStatus(TENANT, COTE, STATUS))
            .isInstanceOf(MasterRejectedException.class).hasMessageContaining("inconnue");

        stubArchiveAndMetadata();
        wireMock.stubFor(put(urlPathEqualTo("/recordsManagement/archives/Metadata"))
            .willReturn(aResponse().withStatus(503).withBody("down")));
        assertThatThrownBy(() -> adapter.publishPhysicalStatus(TENANT, COTE, STATUS))
            .isInstanceOf(MasterUnreachableException.class);
    }
}
