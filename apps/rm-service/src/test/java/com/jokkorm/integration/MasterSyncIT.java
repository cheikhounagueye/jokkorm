package com.jokkorm.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import com.jokkorm.application.master.MasterWatchService;
import com.jokkorm.application.physical.BoxFormatAdminUseCase;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.record.HandleCoteAssignedEventUseCase;
import com.jokkorm.application.record.RecordContainerConflictException;
import com.jokkorm.application.record.StoreRecordInContainerUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterAlert;
import com.jokkorm.domain.master.MasterSync;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.record.Cote;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
        registry.add("jokkorm.master.watch.enabled", () -> "false");
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
    @Autowired MasterWatchService watcher;

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
        MAARCH.stubFor(get(urlPathEqualTo("/lifeCycle/event/Search")).willReturn(json("[]")));

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
        MAARCH.stubFor(get(urlPathEqualTo("/lifeCycle/event/Search")).willReturn(json("[]")));
        as(() -> place.execute(box.id(), p1.id(), "it"));
        sync.drain(100);
        assertThat(row().status()).isEqualTo(MasterSync.Status.SENT);
    }

    // ─── Veilleur du journal (étape 1) ───────────────────────────────────────────

    private static final String MOD = "recordsManagement/metadataModification";
    private static final String FREEZE = "recordsManagement/freeze";
    private static final String UNFREEZE = "recordsManagement/unfreeze";

    private static String eventJson(String type, String id, Instant at, String account) {
        return "{\"eventId\":\"" + id + "\",\"eventType\":\"" + type + "\",\"timestamp\":\""
            + OffsetDateTime.ofInstant(at, ZoneOffset.UTC) + "\",\"objectClass\":\"recordsManagement/archive\","
            + "\"objectId\":\"arc-1\",\"accountName\":\"" + account + "\"}";
    }

    private void stubEvent(String type, String id, Instant at, String account) {
        MAARCH.stubFor(get(urlPathEqualTo("/lifeCycle/event/Search")).withQueryParam("eventType", equalTo(type))
            .willReturn(json("[" + eventJson(type, id, at, account) + "]")));
    }

    /** Ce que Maarch affiche dans le champ de localisation de l'archive. */
    private void stubDisplayed(String displayed) {
        String field;
        try {
            field = displayed == null ? "" : ",\"localisation_physique\":"
                + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(displayed);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        MAARCH.stubFor(get(urlPathMatching("/recordsManagement/archive/Metadata/.*")).willReturn(json(
            "{\"archiveId\":\"arc-1\",\"archiverArchiveId\":\"" + COTE + "\",\"originatorArchiveId\":\"123\","
                + "\"archiveName\":\"Dossier\",\"originatingDate\":\"2026-01-02\",\"description\":{\"service\":\"RH\"" + field + "}}")));
    }

    private java.util.List<MasterAlert> alerts(boolean openOnly) {
        return watcher.alerts(tenant, openOnly, 50);
    }

    private int audits(String action) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE tenant_id = ? AND action = ?",
            Integer.class, tenant.value(), action);
        return n == null ? 0 : n;
    }

    private String recordStatus() {
        return jdbc.queryForObject("SELECT status FROM physical_archive_record WHERE tenant_id = ? AND cote = ?",
            String.class, tenant.value(), COTE);
    }

    @Test
    @DisplayName("Veilleur : emplacement modifié à la main dans Maarch → rétabli, signalé une seule fois, curseur = heure Maarch")
    void manual_location_edit_is_restored_and_reported_once() {
        Instant at = Instant.now().minusSeconds(90).truncatedTo(ChronoUnit.SECONDS);
        stubDisplayed("Bureau du DRH, armoire du fond");
        stubEvent(MOD, "ev-mod-1", at, "ggrand");

        MasterWatchService.Run run = watcher.watch(tenant);

        assertThat(run.ok()).isTrue();
        assertThat(run.raised()).isEqualTo(1);
        assertThat(row().status()).as("réécriture demandée").isEqualTo(MasterSync.Status.PENDING);
        MasterAlert alert = alerts(true).get(0);
        assertThat(alert.kind()).isEqualTo(MasterAlert.Kind.LOCATION_RESTORED);
        assertThat(alert.actor()).isEqualTo("ggrand");
        assertThat(alert.masterEventId()).isEqualTo("ev-mod-1");
        assertThat(alert.detail()).contains("modifié à la main").doesNotContain("Bureau du DRH");
        assertThat(audits("MASTER_LOCATION_RESTORED")).isEqualTo(1);
        assertThat(watcher.status(tenant).cursorAt()).isEqualTo(at);

        // Relire le même événement ne rejoue rien.
        assertThat(watcher.watch(tenant).raised()).isZero();
        assertThat(alerts(false)).hasSize(1);
        assertThat(audits("MASTER_LOCATION_RESTORED")).isEqualTo(1);

        // Le travailleur réécrit l'emplacement de JokkoRM.
        sync.drain(100);
        MAARCH.verify(putRequestedFor(urlPathEqualTo("/recordsManagement/archives/Metadata"))
            .withRequestBody(matchingJsonPath("$.description.localisation_physique", containing("Tablette 1"))));
    }

    @Test
    @DisplayName("Veilleur : même emplacement aux espaces près (nom modifié, par ex.) → aucun signalement")
    void same_location_with_whitespace_is_not_a_discrepancy() {
        String sent = row().lastPayload();
        stubDisplayed("  " + sent.replace(" · ", "  ·   ") + "\n");
        stubEvent(MOD, "ev-mod-2", Instant.now().minusSeconds(30), "ggrand");

        MasterWatchService.Run run = watcher.watch(tenant);

        assertThat(run.raised()).isZero();
        assertThat(alerts(false)).isEmpty();
        assertThat(row().status()).isEqualTo(MasterSync.Status.SENT);
    }

    @Test
    @DisplayName("Veilleur : valeur différente juste après une écriture de JokkoRM → à vérifier, jamais de réécriture (pas de boucle)")
    void own_write_mismatch_is_reported_without_rewrite() {
        stubDisplayed("valeur retouchée par Maarch");
        stubEvent(MOD, "ev-mod-3", Instant.now().minusSeconds(30), "svc");  // svc = compte de JokkoRM dans ce test

        watcher.watch(tenant);

        assertThat(alerts(true)).extracting(MasterAlert::kind).containsExactly(MasterAlert.Kind.LOCATION_MISMATCH);
        assertThat(row().status()).isEqualTo(MasterSync.Status.SENT);
        assertThat(audits("MASTER_LOCATION_RESTORED")).isZero();
    }

    @Test
    @DisplayName("Veilleur : gel posé dans Maarch pendant un prêt → archive gelée, alerte « demander le retour » ; levée → dégelée")
    void freeze_while_on_loan_then_unfreeze() {
        as(() -> {
            var loan = requestLoan.execute(box.id(), "Awa Diop", "DRH", "Audit", Instant.now().plus(Duration.ofDays(10)), "it");
            return markOut.execute(loan.id(), "it");
        });
        stubEvent(FREEZE, "ev-freeze-1", Instant.now().minusSeconds(120), "jjonasz");

        watcher.watch(tenant);

        assertThat(recordStatus()).isEqualTo("FROZEN");
        MasterAlert alert = alerts(true).get(0);
        assertThat(alert.kind()).isEqualTo(MasterAlert.Kind.FROZEN_WHILE_OUT);
        assertThat(alert.detail()).contains("demander son retour");
        Integer traced = jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE tenant_id = ? AND action = 'LEGAL_HOLD_APPLIED' "
            + "AND actor = 'veilleur' AND metadata LIKE '%evenement-maitre=ev-freeze-1%'", Integer.class, tenant.value());
        assertThat(traced).isEqualTo(1);

        stubEvent(UNFREEZE, "ev-unfreeze-1", Instant.now().minusSeconds(60), "jjonasz");
        watcher.watch(tenant);

        assertThat(recordStatus()).isEqualTo("STORED");
        assertThat(alerts(false)).extracting(MasterAlert::kind).contains(MasterAlert.Kind.UNFROZEN);
        assertThat(alerts(true)).extracting(MasterAlert::kind).doesNotContain(MasterAlert.Kind.UNFROZEN);
        assertThat(watcher.acknowledge(tenant, alert.id(), "archiviste-it")).isTrue();
        assertThat(watcher.acknowledge(tenant, alert.id(), "archiviste-it")).isFalse();
        assertThat(audits("MASTER_ALERT_ACKNOWLEDGED")).isEqualTo(1);
    }

    @Test
    @DisplayName("Veilleur : écriture de JokkoRM puis modification à la main dans la même minute → un seul « rétabli », aucun faux « à vérifier »")
    void connector_write_then_manual_edit_in_same_pass() {
        Instant t = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.SECONDS);
        stubDisplayed("Bureau du DRH");
        MAARCH.stubFor(get(urlPathEqualTo("/lifeCycle/event/Search")).withQueryParam("eventType", equalTo(MOD))
            .willReturn(json("[" + eventJson(MOD, "ev-human", t.plusSeconds(10), "ggrand") + ","
                + eventJson(MOD, "ev-jokkorm", t, "svc") + "]")));

        watcher.watch(tenant);

        assertThat(alerts(false)).extracting(MasterAlert::kind).containsExactly(MasterAlert.Kind.LOCATION_RESTORED);
        assertThat(alerts(false).get(0).actor()).isEqualTo("ggrand");
        assertThat(audits("MASTER_LOCATION_RESTORED")).isEqualTo(1);
    }

    // ─── Un dossier, une seule boîte ──────────────────────────────────────────

    private PhysicalContainer secondBoxAtP2() {
        return as(() -> {
            UUID d10 = formats.list().stream().filter(f -> f.spineMm() == 100).map(BoxFormat::id).findFirst().orElseThrow();
            PhysicalContainer b2 = createBox.execute("Dossiers du personnel", 50, d10);
            return place.execute(b2.id(), p2.id(), "it");
        });
    }

    private String recordBox() {
        return jdbc.queryForObject(
            "SELECT c.barcode FROM physical_archive_record r JOIN physical_container c ON c.id = r.container_id "
                + "WHERE r.tenant_id = ? AND r.cote = ?", String.class, tenant.value(), COTE);
    }

    private java.util.List<String> movementLog() {
        return jdbc.queryForList(
            "SELECT m.kind || ' ' || m.motive FROM physical_movement m JOIN physical_archive_record r ON r.id = m.record_id "
                + "WHERE r.tenant_id = ? AND r.cote = ? ORDER BY m.occurred_at", String.class, tenant.value(), COTE);
    }

    private RecordContainerConflictException storeFails(String barcode, boolean transfer) {
        try {
            as(() -> store.execute(Cote.of(COTE), barcode, transfer));
        } catch (RecordContainerConflictException e) {
            return e;
        }
        throw new AssertionError("Le rangement aurait dû être refusé");
    }

    @Test
    @DisplayName("Dossier déjà dans une boîte, rangé dans une autre sans transfert → refusé, rien n'est écrit, la boîte actuelle est nommée")
    void storing_in_another_box_without_transfer_is_refused() {
        PhysicalContainer b2 = secondBoxAtP2();
        int movementsBefore = movementLog().size();
        sync.drain(100);
        MAARCH.resetRequests();

        RecordContainerConflictException e = storeFails(b2.barcode(), false);

        assertThat(e.reason()).isEqualTo(RecordContainerConflictException.Reason.IN_ANOTHER_BOX);
        assertThat(e.currentBarcode()).isEqualTo(box.barcode());
        assertThat(e.currentAddress()).contains("Travée T01 · Tablette 1 · Emplacement 01");
        assertThat(e.getMessage()).contains(box.barcode()).contains("une seule boîte");
        assertThat(recordBox()).isEqualTo(box.barcode());
        assertThat(movementLog()).hasSize(movementsBefore);
        sync.drain(100);
        MAARCH.verify(0, putRequestedFor(urlPathEqualTo("/recordsManagement/archives/Metadata")));
    }

    @Test
    @DisplayName("Transfert confirmé → le dossier change de boîte, déplacement tracé avec la boîte d'origine, Maarch reçoit la nouvelle adresse")
    void confirmed_transfer_moves_the_record() {
        PhysicalContainer b2 = secondBoxAtP2();
        sync.drain(100);

        as(() -> store.execute(Cote.of(COTE), b2.barcode(), true));

        assertThat(recordBox()).isEqualTo(b2.barcode());
        assertThat(movementLog()).last().asString()
            .startsWith("RELOCATE").contains("container=" + b2.barcode()).contains("from=" + box.barcode());
        sync.drain(100);
        assertThat(row().lastPayload()).contains("Tablette 2 · Emplacement 01");
        MAARCH.verify(putRequestedFor(urlPathEqualTo("/recordsManagement/archives/Metadata"))
            .withRequestBody(matchingJsonPath("$.description.localisation_physique", containing("Tablette 2"))));
    }

    @Test
    @DisplayName("Même boîte une 2ᵉ fois → « déjà dans cette boîte », aucun mouvement, aucun envoi")
    void storing_twice_in_the_same_box_writes_nothing() {
        int movementsBefore = movementLog().size();
        int storedBefore = audits("RECORD_STORED");

        RecordContainerConflictException e = storeFails(box.barcode(), false);

        assertThat(e.reason()).isEqualTo(RecordContainerConflictException.Reason.ALREADY_IN_THIS_BOX);
        assertThat(movementLog()).hasSize(movementsBefore);
        assertThat(audits("RECORD_STORED")).isEqualTo(storedBefore);
        assertThat(row().status()).isEqualTo(MasterSync.Status.SENT);
    }

    @Test
    @DisplayName("Transfert alors que la boîte d'origine est sortie en prêt → refusé, le dossier ne bouge pas")
    void transfer_is_blocked_while_the_source_box_is_on_loan() {
        PhysicalContainer b2 = secondBoxAtP2();
        as(() -> {
            var loan = requestLoan.execute(box.id(), "Awa Diop", "DRH", "Audit", Instant.now().plus(Duration.ofDays(10)), "it");
            return markOut.execute(loan.id(), "it");
        });

        RecordContainerConflictException e = storeFails(b2.barcode(), true);

        assertThat(e.reason()).isEqualTo(RecordContainerConflictException.Reason.TRANSFER_BLOCKED);
        assertThat(e.getMessage()).contains("sortie en prêt");
        assertThat(recordBox()).isEqualTo(box.barcode());
    }

    @Test
    @DisplayName("Dossier sous gel contentieux → aucun transfert proposé ni accepté, message clair, le dossier ne bouge pas")
    void frozen_record_never_changes_box() {
        PhysicalContainer b2 = secondBoxAtP2();
        jdbc.update("UPDATE physical_archive_record SET status = 'FROZEN' WHERE tenant_id = ? AND cote = ?",
            tenant.value(), COTE);

        for (boolean transfer : new boolean[] {false, true}) {
            try {
                as(() -> store.execute(Cote.of(COTE), b2.barcode(), transfer));
                throw new AssertionError("Le rangement d'un dossier gelé aurait dû être refusé");
            } catch (com.jokkorm.application.record.LegalHoldException e) {
                assertThat(e.getMessage()).contains("gel contentieux").contains(box.barcode());
            }
        }
        assertThat(recordBox()).isEqualTo(box.barcode());
        assertThat(recordStatus()).isEqualTo("FROZEN");
    }

    @Test
    @DisplayName("Veilleur : Maarch injoignable → passage en échec visible, curseur inchangé, rien de perdu")
    void outage_keeps_the_cursor() {
        Instant at = Instant.now().minusSeconds(200).truncatedTo(ChronoUnit.SECONDS);
        stubDisplayed(row().lastPayload());
        stubEvent(MOD, "ev-mod-4", at, "ggrand");
        watcher.watch(tenant);
        assertThat(watcher.status(tenant).cursorAt()).isEqualTo(at);

        MAARCH.stubFor(get(urlPathEqualTo("/lifeCycle/event/Search")).willReturn(aResponse().withStatus(503).withBody("down")));
        MasterWatchService.Run run = watcher.watch(tenant);

        assertThat(run.ok()).isFalse();
        MasterWatchService.Status status = watcher.status(tenant);
        assertThat(status.lastError()).contains("503");
        assertThat(status.cursorAt()).isEqualTo(at);
        assertThat(status.lastSuccessAt()).isNotNull();
    }
}
