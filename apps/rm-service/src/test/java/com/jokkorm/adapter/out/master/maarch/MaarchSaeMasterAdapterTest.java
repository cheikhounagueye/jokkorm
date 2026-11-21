package com.jokkorm.adapter.out.master.maarch;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.DestructionAttestation;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MovementEvent;
import com.jokkorm.domain.master.PhysicalLocation;
import com.jokkorm.domain.record.Cote;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests du MaarchSaeMasterAdapter contre un WireMock standalone — pas de Spring, pas de DB.
 * Prouve : auth JWT caché + retry 401, fetchByCote mappe les champs Maarch, les 3 notify
 * posent les bons payloads sur les bons endpoints.
 */
class MaarchSaeMasterAdapterTest {

    private static final TenantId TENANT = TenantId.of("acme");
    private static final Cote COTE = Cote.of("DMG/MP/2024/047");
    private static WireMockServer wireMock;
    private MaarchSaeMasterAdapter adapter;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @BeforeEach
    void setup() {
        wireMock.resetAll();
        // Authent OK par défaut — chaque test peut override.
        wireMock.stubFor(post(urlPathEqualTo("/rest/authenticate"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"token\":\"test-jwt\"}")));

        MaarchProperties props = new MaarchProperties(
            wireMock.baseUrl(), "svc-jokkorm", "secret", 5000);
        adapter = new MaarchSaeMasterAdapter(props);
    }

    @Test
    @DisplayName("fetchByCote mappe title / sort final / legal hold")
    void fetch_by_cote_maps_fields() {
        wireMock.stubFor(get(urlPathEqualTo("/rest/resources"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    { "resources": [ {
                        "resId": 42,
                        "subject": "Marché public n° 2024-047",
                        "destination": "finances/marches/2024",
                        "retentionFinalDisposition": "VERSEMENT_AN",
                        "retentionHold": true
                    } ] }
                    """)));

        Optional<MasterArchiveView> view = adapter.fetchByCote(TENANT, COTE);

        assertThat(view).isPresent();
        assertThat(view.get().title()).isEqualTo("Marché public n° 2024-047");
        assertThat(view.get().classificationPath()).isEqualTo("finances/marches/2024");
        assertThat(view.get().finalFate()).isEqualTo("VERSEMENT_AN");
        assertThat(view.get().legalHold()).isTrue();
    }

    @Test
    @DisplayName("fetchByCote retourne empty sur 404 ou body vide")
    void fetch_by_cote_returns_empty_on_not_found() {
        wireMock.stubFor(get(urlPathEqualTo("/rest/resources"))
            .willReturn(aResponse().withStatus(200)
                .withBody("{\"resources\": []}")));

        assertThat(adapter.fetchByCote(TENANT, COTE)).isEmpty();
    }

    @Test
    @DisplayName("401 force une ré-authentification puis retry")
    void refreshes_token_on_401() {
        // Premier GET : 401. Le retry doit passer après ré-auth.
        wireMock.stubFor(get(urlPathEqualTo("/rest/resources"))
            .inScenario("auth retry")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(401))
            .willSetStateTo("retry"));
        wireMock.stubFor(get(urlPathEqualTo("/rest/resources"))
            .inScenario("auth retry")
            .whenScenarioStateIs("retry")
            .willReturn(aResponse().withStatus(200)
                .withBody("{\"resources\":[{\"resId\":99,\"subject\":\"ok\"}]}")));

        Optional<MasterArchiveView> view = adapter.fetchByCote(TENANT, COTE);

        assertThat(view).isPresent();
        assertThat(view.get().title()).isEqualTo("ok");
        // L'adapter a appelé /authenticate au moins deux fois (init + retry).
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/rest/authenticate")));
    }

    @Test
    @DisplayName("notifyPhysicalLocation PUT /externalInfos avec la single-line location")
    void notify_physical_location_puts_external_infos() {
        stubResolveResId(42L);
        wireMock.stubFor(put(urlPathEqualTo("/rest/resources/42/externalInfos"))
            .willReturn(aResponse().withStatus(204)));

        PhysicalLocation loc = new PhysicalLocation("Diamniadio", "MAG-B", "14", "3", "07");
        adapter.notifyPhysicalLocation(TENANT, COTE, loc);

        wireMock.verify(WireMock.putRequestedFor(urlPathEqualTo("/rest/resources/42/externalInfos"))
            .withRequestBody(equalToJson("""
                { "externalInfos": {
                    "physical_location": "Diamniadio / MAG-B / 14 / 3 / 07",
                    "physical_location_updated_by": "jokkorm"
                } }
                """)));
    }

    @Test
    @DisplayName("notifyMovement POST /notes avec un libellé structuré")
    void notify_movement_posts_note() {
        stubResolveResId(42L);
        wireMock.stubFor(post(urlPathEqualTo("/rest/resources/42/notes"))
            .willReturn(aResponse().withStatus(200)));

        MovementEvent ev = new MovementEvent(
            MovementEvent.Kind.LOAN_OUT, "khady.diouf",
            Instant.parse("2026-11-21T10:00:00Z"), "consultation DAF");
        adapter.notifyMovement(TENANT, COTE, ev);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/rest/resources/42/notes")));
    }

    @Test
    @DisplayName("notifyDestructionExecuted POST /attachments avec PDF Base64")
    void notify_destruction_posts_attachment() {
        stubResolveResId(42L);
        wireMock.stubFor(post(urlPathEqualTo("/rest/resources/42/attachments"))
            .willReturn(aResponse().withStatus(201)));

        DestructionAttestation att = new DestructionAttestation(
            "att-001", Instant.parse("2026-11-21T10:00:00Z"), "gora.fall",
            "fake-pdf-bytes".getBytes());
        adapter.notifyDestructionExecuted(TENANT, COTE, att);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/rest/resources/42/attachments")));
    }

    @Test
    @DisplayName("notify* silencieux si la cote est introuvable (resId manquant)")
    void notify_silent_on_unknown_cote() {
        // Pas de stub /rest/resources → wiremock renvoie 404 par défaut
        wireMock.stubFor(get(urlPathMatching("/rest/resources.*"))
            .willReturn(aResponse().withStatus(200).withBody("{\"resources\":[]}")));

        // Ne doit pas lever d'exception, et ne pas poster sur externalInfos
        adapter.notifyPhysicalLocation(TENANT, COTE,
            new PhysicalLocation("S", "M", "T", "Tab", "P"));

        wireMock.verify(0, WireMock.putRequestedFor(urlPathMatching("/rest/resources/.*/externalInfos")));
    }

    private void stubResolveResId(long resId) {
        wireMock.stubFor(get(urlPathEqualTo("/rest/resources"))
            .willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"resources\":[{\"resId\":" + resId + ",\"subject\":\"x\"}]}")));
    }
}
