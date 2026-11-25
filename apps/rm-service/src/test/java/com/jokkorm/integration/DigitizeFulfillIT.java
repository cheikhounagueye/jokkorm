package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jokkorm.application.access.AccessRequestWorkflow;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.access.AccessRequest;
import com.jokkorm.domain.access.RequestType;
import com.jokkorm.domain.physical.PhysicalContainer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pattern 07 : JokkoRM ne garde jamais un fichier numérisé. Une demande de numérisation s'exécute
 * avec la référence du scan déposé dans Maarch RM ; un fichier envoyé est refusé et rien n'est écrit.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class DigitizeFulfillIT {

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
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired AccessRequestWorkflow workflow;

    private TenantId tenant;

    @BeforeEach
    void setUp() {
        tenant = TenantId.of("scan-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?)", tenant.value(), "IT " + tenant.value());
    }

    @AfterEach
    void tearDown() {
        RoleContext.clear();
    }

    private <T> T as(Set<UserRole> roles, Callable<T> body) {
        RoleContext.set(roles);
        try {
            return TenantContext.callWithUnchecked(tenant, body);
        } finally {
            RoleContext.clear();
        }
    }

    /** Demande approuvée en 4 yeux : qualifiée par l'archiviste, approuvée par la responsable. */
    private AccessRequest approved(RequestType type) {
        PhysicalContainer box = as(Set.of(UserRole.ARCHIVIST),
            () -> createBox.execute("Marchés publics · " + UUID.randomUUID().toString().substring(0, 6), null, null));
        Instant back = type == RequestType.BORROW_BOX ? Instant.now().plus(7, ChronoUnit.DAYS) : null;
        AccessRequest draft = as(Set.of(UserRole.REQUESTER), () -> workflow.createDraft(type, box.id(),
            "awa", "Awa Diop", "Contrôle de gestion", "Revue des marchés 2016",
            type == RequestType.BORROW_BOX ? null : "Cahier des charges et PV d'attribution", back));
        as(Set.of(UserRole.REQUESTER), () -> workflow.submit(draft.id(), "awa"));
        as(Set.of(UserRole.ARCHIVIST), () -> workflow.qualify(draft.id(), null, "archiviste"));
        return as(Set.of(UserRole.ARCHIVES_HEAD), () -> workflow.approve(draft.id(), null, "resp-archives"));
    }

    private ResultActions fulfill(UUID id, String json) throws Exception {
        return mvc.perform(post("/api/v1/access-requests/" + id + "/fulfill")
            .header("X-Tenant-Id", tenant.value())
            .header("X-Role", "ARCHIVIST")
            .header("X-Actor", "archiviste")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM access_request WHERE id = ?", String.class, id);
    }

    @Test
    @DisplayName("Numérisation exécutée avec la référence Maarch : JokkoRM garde la référence et le lien, rien d'autre")
    void fulfilledWithReference() throws Exception {
        AccessRequest r = approved(RequestType.DIGITIZE_EXTRACT);

        fulfill(r.id(), """
            {"notes":"4 pièces","masterReference":" FR094080_2026_AE0002_001 ",
             "masterUrl":"https://maarch.example.org/recordsManagement/archiveDescription/abc"}""")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.request.status").value("FULFILLED"))
            .andExpect(jsonPath("$.request.maarchResId").value("FR094080_2026_AE0002_001"))
            .andExpect(jsonPath("$.request.fulfillmentNotes").value("4 pièces"));

        // Les notes de l'archiviste restent les siennes (plus de mention technique ajoutée).
        assertThat(jdbc.queryForObject("SELECT fulfillment_notes FROM access_request WHERE id = ?",
            String.class, r.id())).isEqualTo("4 pièces");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE target_id = ? AND action = ?",
            Integer.class, r.id(), "ACCESS_REQUEST_FULFILLED")).isEqualTo(1);
    }

    @Test
    @DisplayName("Un PDF envoyé est refusé (400) : la demande reste approuvée, aucun fichier reçu")
    void pdfRefused() throws Exception {
        AccessRequest r = approved(RequestType.DIGITIZE_EXTRACT);

        fulfill(r.id(), """
            {"masterReference":"AE0002","digitizedPdfBase64":"JVBERi0xLjQK"}""")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Maarch RM")));
        assertThat(statusOf(r.id())).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("Sans référence, ou avec un lien qui n'est pas http(s) : refusé, la demande reste approuvée")
    void referenceRequiredAndLinkChecked() throws Exception {
        AccessRequest r = approved(RequestType.DIGITIZE_EXTRACT);

        fulfill(r.id(), "{\"notes\":\"oubli\"}").andExpect(status().isBadRequest());
        fulfill(r.id(), "{\"masterReference\":\"AE0002\",\"masterUrl\":\"javascript:alert(1)\"}")
            .andExpect(status().isBadRequest());
        assertThat(statusOf(r.id())).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT maarch_res_id FROM access_request WHERE id = ?",
            String.class, r.id())).isNull();
    }

    @Test
    @DisplayName("Une référence Maarch sur une extraction de pièces : refusée (elle ne concerne que la numérisation)")
    void referenceOnlyForDigitize() throws Exception {
        AccessRequest r = approved(RequestType.EXTRACT_PIECES);

        fulfill(r.id(), "{\"masterReference\":\"AE0002\"}").andExpect(status().isBadRequest());
        assertThat(statusOf(r.id())).isEqualTo("APPROVED");
        fulfill(r.id(), "{\"notes\":\"pièces remises\"}").andExpect(status().isOk());
        assertThat(statusOf(r.id())).isEqualTo("FULFILLED");
    }
}
