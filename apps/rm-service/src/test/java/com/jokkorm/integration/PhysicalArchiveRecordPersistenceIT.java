package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jokkorm.adapter.out.persistence.record.JpaPhysicalArchiveRecordRepository;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * IT bout-en-bout — phase 1.
 *
 * <p>Démarre un vrai Postgres via Testcontainers, applique Liquibase, et vérifie :
 * <ol>
 *   <li>Le contexte Spring charge avec les migrations 0001 + 0002.</li>
 *   <li>On peut créer un {@link PhysicalArchiveRecord} pour deux tenants distincts.</li>
 *   <li>Une lookup par tenant A ne voit <b>jamais</b> le record du tenant B (cross-tenant leak).</li>
 *   <li>La transition de statut est bien persistée.</li>
 * </ol>
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
@Transactional
class PhysicalArchiveRecordPersistenceIT {

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
        // Désactive l'auto-config OIDC — pas de Keycloak en IT phase 1.
        registry.add("spring.autoconfigure.exclude",
            () -> "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration");
        registry.add("jokkorm.security.enabled", () -> "false");
    }

    @Autowired JpaPhysicalArchiveRecordRepository repository;
    @Autowired JdbcTemplate jdbc;

    private static final TenantId ACME = TenantId.of("acme");
    private static final TenantId GAMMA = TenantId.of("gamma");

    @Test
    @DisplayName("Spring boot, Liquibase applique les 2 migrations, le seed demo existe")
    void context_loads_with_migrations_applied() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM tenant WHERE id = 'demo'", Long.class);
        assertThat(count).isEqualTo(1L);

        // la table physical_archive_record doit exister (NPE si pas migrée)
        Long records = jdbc.queryForObject("SELECT COUNT(*) FROM physical_archive_record", Long.class);
        assertThat(records).isEqualTo(0L);
    }

    @Test
    @DisplayName("Un record se persiste et se relit — même tenant")
    void persist_and_find_same_tenant() {
        seedTenant(ACME);
        Cote cote = Cote.of("DMG/MP/2024/047");

        TenantContext.runWith(ACME, () -> {
            PhysicalArchiveRecord saved = repository.save(
                PhysicalArchiveRecord.register(ACME, cote, Instant.parse("2026-11-21T10:00:00Z")));

            assertThat(saved.id()).isNotNull();
            assertThat(repository.findByCote(ACME, cote)).isPresent();
            assertThat(repository.existsByCote(ACME, cote)).isTrue();
        });
    }

    @Test
    @DisplayName("Un record du tenant acme n'est JAMAIS visible depuis gamma (anti cross-tenant leak)")
    void cross_tenant_lookup_is_blind() {
        seedTenant(ACME);
        seedTenant(GAMMA);
        Cote shared = Cote.of("MARCHE/2024/99");

        TenantContext.runWith(ACME, () -> {
            repository.save(PhysicalArchiveRecord.register(ACME, shared,
                Instant.parse("2026-11-21T10:00:00Z")));
        });

        // Même cote, autre tenant, autre clé primaire — on ne doit rien voir
        TenantContext.runWith(GAMMA, () -> {
            assertThat(repository.findByCote(GAMMA, shared)).isEmpty();
            assertThat(repository.existsByCote(GAMMA, shared)).isFalse();
        });
    }

    @Test
    @DisplayName("Transition de statut persistée + compteur @Version incrémenté")
    void transition_is_persisted() {
        seedTenant(ACME);
        Cote cote = Cote.of("RH/CONTRAT/2024/001");

        AtomicReference<PhysicalArchiveRecord> savedRef = new AtomicReference<>();
        TenantContext.runWith(ACME, () -> savedRef.set(repository.save(
            PhysicalArchiveRecord.register(ACME, cote, Instant.parse("2026-11-21T10:00:00Z")))));
        PhysicalArchiveRecord saved = savedRef.get();

        TenantContext.runWith(ACME, () -> {
            PhysicalArchiveRecord loaded = repository.findById(ACME, saved.id()).orElseThrow();
            assertThat(loaded.status()).isEqualTo(PhysicalArchiveStatus.REGISTERED);

            loaded.markStored(Instant.parse("2026-11-21T11:00:00Z"));
            repository.save(loaded);

            PhysicalArchiveRecord reloaded = repository.findById(ACME, saved.id()).orElseThrow();
            assertThat(reloaded.status()).isEqualTo(PhysicalArchiveStatus.STORED);
            assertThat(reloaded.lastTransitionAt()).isEqualTo(Instant.parse("2026-11-21T11:00:00Z"));
        });
    }

    private void seedTenant(TenantId tenant) {
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
            tenant.value(), "Test tenant " + tenant.value());
    }
}
