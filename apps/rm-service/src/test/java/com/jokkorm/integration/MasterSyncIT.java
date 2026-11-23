package com.jokkorm.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.jokkorm.application.loan.MarkLoanOutUseCase;
import com.jokkorm.application.loan.RequestLoanUseCase;
import com.jokkorm.application.master.MasterSyncService;
import com.jokkorm.application.physical.BoxFormatAdminUseCase;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.record.HandleCoteAssignedEventUseCase;
import com.jokkorm.application.record.StoreRecordInContainerUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterSync;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.record.Cote;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Tranche 2a de bout en bout : un geste physique met la cote en file, le travailleur écrit
 * l'état dans un Maarch RM simulé (WireMock). Une panne ou un refus du maître ne bloque
 * jamais le geste.
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class MasterSyncIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jokkorm_it")
            .withUsername("jokkorm")
            .withPassword("jokkorm")
            .withReuse(true);

    static final WireMockServer MAARCH = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        MAARCH.start();
    }

    @AfterAll
    static void stopMaarch() {
        MAARCH.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration");
        registry.add("jokkorm.security.enabled", () -> "false");
        registry.add("jokkorm.master.kind", () -> "maarchrm");
        registry.add("jokkorm.master.maarchrm.base-url", MAARCH::baseUrl);
        registry.add("jokkorm.master.maarchrm.login", () -> "svc");
        registry.add("jokkorm.master.maarchrm.password", () -> "x");
        registry.add("jokkorm.master.sync.worker-enabled", () -> "false");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CreateStorageLocationUseCase createLocation;
    @Autowired BoxFormatAdminUseCase formats;
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired PlaceContainerUseCase place;
    @Autowired HandleCoteAssignedEventUseCase register;
    @Autowired StoreRecordInContainerUseCase store;
    @Autowired RequestLoanUseCase requestLoan;
    @Autowired MarkLoanOutUseCase markOut;
    @Autowired MasterSyncService sync;

    private static final String COTE = "FR-IT-0001";
    private TenantId tenant;
    private StorageLocation p1;
    private StorageLocation p2;
    private PhysicalContainer box;

    @BeforeEach
    void setUp() {
        MAARCH.resetAll();
        MAARCH.stubFor(get(urlPathEqualTo("/recordsManagement/archives")).willReturn(json(
            "[{\"archiveId\":\"arc-1\",\"archiverArchiveId\":\"" + COTE + "\"}]")));
        MAARCH.stubFor(get(urlPathEqualTo("/recordsManagement/archives/Count")).willReturn(json("1")));
        MAARCH.stubFor(get(urlPathMatching("/recordsManagement/archive/Metadata/.*")).willReturn(json(
            "{\"archiveId\":\"arc-1\",\"archiverArchiveId\":\"" + COTE + "\",\"originatorArchiveId\":\"123\","
                + "\"archiveName\":\"Dossier\",\"originatingDate\":\"2026-01-02\",\"description\":{\"service\":\"RH\"}}")));
        MAARCH.stubFor(put(urlPathEqualTo("/recordsManagement/archives/Metadata")).willReturn(json("true")));

        tenant = TenantId.of("sync-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?)", tenant.value(), "IT " + tenant.value());
        RoleContext.set(Set.of(UserRole.ARCHIVIST));
        as(() -> {
            StorageLocation site = createLocation.createSite("DKR", "Dakar");
            StorageLocation room = createLocation.createChild(site.id(), StorageLocationKind.STOREHOUSE, "SALLE", "Salle IT");
            StorageLocation shelf = createLocation.createChild(room.id(), StorageLocationKind.SHELF, "T01", null);
            StorageLocation rack1 = createLocation.createChild(shelf.id(), StorageLocationKind.RACK, "1", null);
            StorageLocation rack2 = createLocation.createChild(shelf.id(), StorageLocationKind.RACK, "2", null);
            p1 = createLocation.createChild(rack1.id(), StorageLocationKind.POSITION, "01", null);
            p2 = createLocation.createChild(rack2.id(), StorageLocationKind.POSITION, "01", null);
            UUID d10 = formats.list().stream().filter(f -> f.spineMm() == 100).map(BoxFormat::id).findFirst().orElseThrow();
            box = createBox.execute("Boîte", null, d10);
            box = place.execute(box.id(), p1.id(), "it");
            register.execute(tenant, Cote.of(COTE));
            store.execute(Cote.of(COTE), box.barcode());
            return null;
        });
        sync.drain(100);  // vide la file des gestes de mise en place
        MAARCH.resetRequests();
    }

    @AfterEach
    void tearDown() {
        RoleContext.clear();
    }

    private <T> T as(Callable<T> body) {
        return TenantContext.callWithUnchecked(tenant, body);
    }

    private static com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder json(String body) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body);
    }

    private MasterSync row() {
        return as(() -> sync.status(Cote.of(COTE))).orElseThrow();
    }

    @Test
    @DisplayName("Déplacer la boîte : la cote passe « à envoyer », le travailleur écrit la nouvelle adresse dans Maarch")
    void moving_the_box_updates_maarch() {
        as(() -> place.execute(box.id(), p2.id(), "it"));
        assertThat(row().status()).isEqualTo(MasterSync.Status.PENDING);

        sync.drain(100);

        assertThat(row().status()).isEqualTo(MasterSync.Status.SENT);
        assertThat(row().lastPayload()).contains("Travée T01 · Tablette 2 · Emplacement 01").contains("en magasin");
        MAARCH.verify(putRequestedFor(urlPathEqualTo("/recordsManagement/archives/Metadata"))
            .withRequestBody(matchingJsonPath("$.originatorArchiveId", containing("123")))
            .withRequestBody(matchingJsonPath("$.description.service", containing("RH")))
            .withRequestBody(matchingJsonPath("$.description.localisation_physique", containing("Tablette 2")))
            .withRequestBody(matchingJsonPath("$.description.localisation_physique", containing("en magasin"))));
    }

    @Test
    @DisplayName("Prêt : l'état affiché dans Maarch devient « en prêt, retour prévu le … », sans code-barres")
    void loan_shows_in_maarch() {
        as(() -> {
            var loan = requestLoan.execute(box.id(), "Awa Diop", "DRH", "Audit", Instant.now().plus(Duration.ofDays(10)), "it");
            return markOut.execute(loan.id(), "it");
        });

        sync.drain(100);

        assertThat(row().lastPayload()).contains("en prêt, retour prévu le").doesNotContain(box.barcode());
    }

    @Test
    @DisplayName("Demande de prêt pas encore sortie : Maarch affiche toujours « en magasin »")
    void requested_loan_is_not_shown_as_out() {
        as(() -> requestLoan.execute(box.id(), "Awa Diop", "DRH", "Audit", Instant.now().plus(Duration.ofDays(10)), "it"));
        as(() -> {
            sync.touchContainer(tenant, box.id());  // un envoi est demandé pendant que la demande attend
            return null;
        });

        sync.drain(100);

        assertThat(row().lastPayload()).contains("en magasin").doesNotContain("en prêt");
    }

    @Test
    @DisplayName("Maarch en panne : le geste réussit quand même, l'envoi est reprogrammé")
    void outage_never_blocks_the_gesture() {
        MAARCH.stubFor(put(urlPathEqualTo("/recordsManagement/archives/Metadata"))
            .willReturn(aResponse().withStatus(503).withBody("down")));

        PhysicalContainer moved = as(() -> place.execute(box.id(), p2.id(), "it"));
        assertThat(moved.positionId()).contains(p2.id());
        sync.drain(100);

        MasterSync r = row();
        assertThat(r.status()).isEqualTo(MasterSync.Status.PENDING);
        assertThat(r.attempts()).isEqualTo(1);
        assertThat(r.nextAttemptAt()).isAfter(Instant.now());
        assertThat(r.lastError()).contains("503");
    }

    @Test
    @DisplayName("Maarch refuse (champ absent du profil) : envoi « refusé » avec la raison, jusqu'au prochain geste")
    void rejection_is_visible() {
        MAARCH.stubFor(put(urlPathEqualTo("/recordsManagement/archives/Metadata")).willReturn(aResponse()
            .withStatus(400).withHeader("Content-Type", "application/json")
            .withBody("{\"message\":\"Invalid description field\"}")));
        as(() -> place.execute(box.id(), p2.id(), "it"));

        sync.drain(100);

        assertThat(row().status()).isEqualTo(MasterSync.Status.REJECTED);
        assertThat(row().lastError()).contains("Invalid description field");

        // Le geste suivant redemande un envoi.
        MAARCH.stubFor(put(urlPathEqualTo("/recordsManagement/archives/Metadata")).willReturn(json("true")));
        as(() -> place.execute(box.id(), p1.id(), "it"));
        sync.drain(100);
        assertThat(row().status()).isEqualTo(MasterSync.Status.SENT);
    }
}
