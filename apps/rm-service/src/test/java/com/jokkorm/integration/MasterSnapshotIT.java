package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jokkorm.application.master.MasterSnapshotService;
import com.jokkorm.application.record.ImportFromMasterUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterRecordSnapshotRepository;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * D3 (étude du 24 novembre 2026) : copie locale en lecture seule des données de rangement du
 * maître — prise à l'import, lue au besoin, relue quand elle vieillit, montrée dans « À conditionner ».
 * Espace {@code snapdemo} servi par le maître de démonstration ; espace de test aléatoire = sans maître.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class MasterSnapshotIT {

    private static final TenantId DEMO = TenantId.of("snapdemo");

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> PG =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("jokkorm_it")
            .withUsername("jokkorm")
            .withPassword("jokkorm")
            .withReuse(true);

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PG::getJdbcUrl);
        registry.add("spring.datasource.username", PG::getUsername);
        registry.add("spring.datasource.password", PG::getPassword);
        registry.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration");
        registry.add("jokkorm.security.enabled", () -> "false");
        registry.add("jokkorm.master.tenants." + DEMO.value(), () -> "demo");
        registry.add("jokkorm.master.snapshot.worker-enabled", () -> "false");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ImportFromMasterUseCase importFromMaster;
    @Autowired MasterSnapshotService snapshots;
    @Autowired MasterRecordSnapshotRepository repo;

    @BeforeEach
    void setUp() {
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
            DEMO.value(), "IT démo");
        RoleContext.set(Set.of(UserRole.ARCHIVIST));
    }

    @AfterEach
    void tearDown() {
        RoleContext.clear();
    }

    private <T> T in(TenantId tenant, Callable<T> body) {
        return TenantContext.callWithUnchecked(tenant, body);
    }

    private static String unique(String prefix, int year) {
        return "%s/%d/%s".formatted(prefix, year, UUID.randomUUID().toString().substring(0, 6).toUpperCase());
    }

    @Test
    @DisplayName("L'import prend la copie dans la réponse du maître : service, sort final, échéance")
    void importTakesSnapshot() {
        String cote = unique("DMG", 2016);
        PhysicalArchiveRecord r = in(DEMO, () -> importFromMaster.execute(Cote.of(cote), "it").record());

        var s = repo.find(DEMO, r.id()).orElseThrow();
        assertThat(s.masterKind()).isEqualTo("demo");
        assertThat(s.serviceCode()).isEqualTo("DMG");
        assertThat(s.finalFate()).isEqualTo("VERSEMENT_AN");
        assertThat(s.disposalDate()).isEqualTo(LocalDate.of(2026, 12, 31));   // 2016 + DUA 10 ans
        assertThat(s.classificationLevel()).isNull();
        assertThat(s.legalHold()).isFalse();
    }

    @Test
    @DisplayName("La relecture périodique comble les copies manquantes ; un espace sans maître n'est pas relu")
    void refreshStaleFillsGaps() {
        String cote = unique("RH", 2023);
        PhysicalArchiveRecord r = in(DEMO, () -> importFromMaster.execute(Cote.of(cote), "it").record());
        jdbc.update("DELETE FROM master_record_snapshot WHERE record_id = ?", r.id());

        assertThat(snapshots.refreshStale(DEMO, 500)).isPositive();
        var s = repo.find(DEMO, r.id()).orElseThrow();
        assertThat(s.serviceCode()).isEqualTo("RH");
        assertThat(s.finalFate()).isEqualTo("DESTRUCTION");

        TenantId alone = TenantId.of("snap-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?)", alone.value(), "IT seul");
        assertThat(snapshots.standalone(alone)).isTrue();
        assertThat(snapshots.refreshStale(alone, 500)).isZero();
    }

    @Test
    @DisplayName("Une copie ne se lit que dans l'espace de son dossier")
    void tenantBound() {
        String cote = unique("FIN", 2021);
        PhysicalArchiveRecord r = in(DEMO, () -> importFromMaster.execute(Cote.of(cote), "it").record());
        assertThat(repo.findAll(DEMO, List.of(r.id()))).containsKey(r.id());
        assertThat(repo.findAll(TenantId.of("autre"), List.of(r.id()))).isEmpty();
    }

    @Test
    @DisplayName("« À conditionner » montre la copie de chaque dossier, et dit si l'espace n'a pas de maître")
    void pendingListShowsFacts() throws Exception {
        String cote = unique("FACT", 2020);
        PhysicalArchiveRecord r = in(DEMO, () -> importFromMaster.execute(Cote.of(cote), "it").record());

        mvc.perform(get("/api/v1/physical/conditioning/pending").param("size", "200")
                .header("X-Tenant-Id", DEMO.value()).header("X-Role", "ARCHIVIST"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.standalone").value(false))
            .andExpect(jsonPath("$.master['" + r.id() + "'].serviceCode").value("FACT"))
            .andExpect(jsonPath("$.master['" + r.id() + "'].finalFate").value("DESTRUCTION"))
            .andExpect(jsonPath("$.master['" + r.id() + "'].disposalDate").value("2030-12-31"));
    }
}
