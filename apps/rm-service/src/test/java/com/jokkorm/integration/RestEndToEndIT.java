package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jokkorm.boot.Application;
import com.jokkorm.commons.web.HttpTenantResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * IT bout-en-bout REST → use case → JPA → Postgres (Testcontainers).
 *
 * <p>Chaîne complète : {@code TenantContextFilter} résout le header {@code X-Tenant-Id},
 * {@code MasterEventController} crée la cote, {@code RecordController} la relit, et le
 * check cross-tenant vérifie qu'un tenant ne voit pas le fonds d'un autre via REST.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class RestEndToEndIT {

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
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("POST cote-assigned → GET records le voit (tenant acme)")
    void cote_assigned_then_list() throws Exception {
        seedTenant("acme");

        mvc.perform(post("/api/v1/events/master/cote-assigned")
                .header(HttpTenantResolver.HEADER, "acme")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cote\":\"MARCHE/2024/001\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.cote").value("MARCHE/2024/001"));

        mvc.perform(get("/api/v1/records").header(HttpTenantResolver.HEADER, "acme"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].cote").value("MARCHE/2024/001"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("Un record acme n'est jamais visible par gamma via l'API REST")
    void cross_tenant_rest_isolation() throws Exception {
        seedTenant("acme");
        seedTenant("gamma");

        // acme crée MARCHE/2024/777
        mvc.perform(post("/api/v1/events/master/cote-assigned")
                .header(HttpTenantResolver.HEADER, "acme")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cote\":\"MARCHE/2024/777\"}"))
            .andExpect(status().isAccepted());

        // gamma essaie de la lire → 404
        mvc.perform(get("/api/v1/records/by-cote")
                .param("cote", "MARCHE/2024/777")
                .header(HttpTenantResolver.HEADER, "gamma"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.type").value("https://jokkorm.io/errors/record-not-found"));

        // gamma liste → vide
        mvc.perform(get("/api/v1/records").header(HttpTenantResolver.HEADER, "gamma"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("POST legal-hold FREEZE → statut passe à FROZEN")
    void legal_hold_freeze_persists() throws Exception {
        seedTenant("acme");

        mvc.perform(post("/api/v1/events/master/cote-assigned")
                .header(HttpTenantResolver.HEADER, "acme")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cote\":\"CONT/2024/042\"}"))
            .andExpect(status().isAccepted());

        mvc.perform(post("/api/v1/events/master/legal-hold")
                .header(HttpTenantResolver.HEADER, "acme")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cote\":\"CONT/2024/042\",\"action\":\"FREEZE\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("FROZEN"));

        mvc.perform(get("/api/v1/records/by-cote")
                .param("cote", "CONT/2024/042")
                .header(HttpTenantResolver.HEADER, "acme"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FROZEN"));
    }

    @Test
    @DisplayName("Requête sans X-Tenant-Id (+ security désactivée) → tenant unresolved 401")
    void missing_tenant_returns_401() throws Exception {
        mvc.perform(get("/api/v1/records"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Rejouer cote-assigned deux fois → idempotent (même record retourné)")
    void cote_assigned_is_idempotent() throws Exception {
        seedTenant("acme");

        mvc.perform(post("/api/v1/events/master/cote-assigned")
                .header(HttpTenantResolver.HEADER, "acme")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cote\":\"REPLAY/001\"}"))
            .andExpect(status().isAccepted());

        mvc.perform(post("/api/v1/events/master/cote-assigned")
                .header(HttpTenantResolver.HEADER, "acme")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cote\":\"REPLAY/001\"}"))
            .andExpect(status().isAccepted());

        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM physical_archive_record WHERE tenant_id = 'acme' AND cote = 'REPLAY/001'",
            Long.class);
        assertThat(count).isEqualTo(1L);
    }

    private void seedTenant(String id) {
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
            id, "Test tenant " + id);
    }
}
