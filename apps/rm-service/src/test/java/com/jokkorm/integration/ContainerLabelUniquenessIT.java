package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.adapter.in.web.dev.DevRoomSeedingController;
import com.jokkorm.application.physical.BoxFormatAdminUseCase;
import com.jokkorm.application.physical.ContainerLabelTakenException;
import com.jokkorm.application.physical.CreateContainerBatchUseCase;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
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
 * Règle « un libellé, une seule boîte » contre un vrai Postgres : deux boîtes non détruites
 * d'un espace ne portent jamais le même libellé, deux lots jamais le même libellé commun.
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class ContainerLabelUniquenessIT {

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
        registry.add("jokkorm.dev.seeding-enabled", () -> "true");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CreateStorageLocationUseCase createLocation;
    @Autowired BoxFormatAdminUseCase formats;
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired CreateContainerBatchUseCase createBatch;
    @Autowired PlaceContainerUseCase place;
    @Autowired PhysicalContainerRepository containers;
    @Autowired DevRoomSeedingController devRooms;

    private TenantId tenant;
    private StorageLocation p1;
    private UUID d10;

    @BeforeEach
    void setUp() {
        tenant = newTenant("lbl");
        RoleContext.set(Set.of(UserRole.ARCHIVIST));
        as(() -> {
            StorageLocation site = createLocation.createSite("DKR", "Dakar");
            StorageLocation room = createLocation.createChild(site.id(), StorageLocationKind.STOREHOUSE, "SALLE", "Salle IT");
            StorageLocation shelf = createLocation.createChild(room.id(), StorageLocationKind.SHELF, "T01", null);
            StorageLocation rack = createLocation.createChild(shelf.id(), StorageLocationKind.RACK, "1", null);
            p1 = createLocation.createChild(rack.id(), StorageLocationKind.POSITION, "01", null);
            d10 = formats.list().stream().filter(f -> f.spineMm() == 100).map(BoxFormat::id).findFirst().orElseThrow();
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        RoleContext.clear();
    }

    private TenantId newTenant(String prefix) {
        TenantId t = TenantId.of(prefix + "-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?)", t.value(), "IT " + t.value());
        return t;
    }

    private <T> T as(Callable<T> body) {
        return TenantContext.callWithUnchecked(tenant, body);
    }

    private int boxCount(TenantId t) {
        return jdbc.queryForObject("SELECT count(*) FROM physical_container WHERE tenant_id = ?", Integer.class, t.value());
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @DisplayName("Une boîte seule : un libellé déjà porté est refusé (casse et espaces ignorés), rien n'est écrit")
    void single_box_label_is_unique_in_the_space() {
        PhysicalContainer first = as(() -> createBox.execute("Test gel · 001/001", null, d10));
        as(() -> place.execute(first.id(), p1.id(), "it"));
        int before = boxCount(tenant);

        assertThatThrownBy(() -> as(() -> createBox.execute("  test   GEL · 001/001 ", null, d10)))
            .isInstanceOf(ContainerLabelTakenException.class)
            .hasMessageContaining("Une boîte porte déjà le libellé « Test gel · 001/001 »")
            .hasMessageContaining(first.barcode())
            .hasMessageContaining("rangée à Salle IT (SALLE) · Travée T01 · Tablette 1 · Emplacement 01")
            .satisfies(e -> {
                var taken = (ContainerLabelTakenException) e;
                assertThat(taken.currentBarcode()).isEqualTo(first.barcode());
                assertThat(taken.count()).isEqualTo(1);
            });
        assertThat(boxCount(tenant)).isEqualTo(before);

        // Un autre espace n'est pas concerné.
        TenantId other = newTenant("lbl2");
        PhysicalContainer elsewhere = TenantContext.callWithUnchecked(other,
            () -> createBox.execute("Test gel · 001/001", null, null));
        assertThat(elsewhere.label()).isEqualTo("Test gel · 001/001");
    }

    @Test
    @DisplayName("Le libellé est enregistré nettoyé ; une boîte sans libellé n'est pas concernée")
    void label_is_stored_clean_and_blank_labels_are_free() {
        PhysicalContainer box = as(() -> createBox.execute("   Dossiers   du personnel 2026 ", null, d10));
        assertThat(box.label()).isEqualTo("Dossiers du personnel 2026");

        PhysicalContainer a = as(() -> createBox.execute(null, null, d10));
        PhysicalContainer b = as(() -> createBox.execute("   ", null, d10));
        assertThat(a.label()).isNull();
        assertThat(b.label()).isNull();
    }

    @Test
    @DisplayName("Un lot : un libellé commun déjà utilisé par une autre série est refusé avant toute écriture")
    void lot_label_is_unique_across_series() {
        var first = as(() -> createBatch.execute("Commande 1", 2, "Test gel", null, d10, "it"));
        assertThat(first.containers()).extracting(PhysicalContainer::label)
            .containsExactly("Test gel · 001/002", "Test gel · 002/002");
        int boxes = boxCount(tenant);
        int lots = count("SELECT count(*) FROM container_batch WHERE tenant_id = ?", tenant.value());
        int audits = count("SELECT count(*) FROM audit_event WHERE tenant_id = ? AND action = 'CONTAINER_BATCH_CREATED'",
            tenant.value());

        // Même libellé commun, autre taille : les numéros diffèrent mais la série serait ambiguë.
        assertThatThrownBy(() -> as(() -> createBatch.execute("Commande 2", 3, " test  gel ", null, d10, "it")))
            .isInstanceOf(ContainerLabelTakenException.class)
            .hasMessageContaining("Le libellé commun « test gel » est déjà celui d'une autre série : 2 boîtes, dont « Test gel · 001/002 »")
            .hasMessageContaining("en zone tampon, pas encore rangée");
        assertThat(boxCount(tenant)).isEqualTo(boxes);
        assertThat(count("SELECT count(*) FROM container_batch WHERE tenant_id = ?", tenant.value())).isEqualTo(lots);
        assertThat(count("SELECT count(*) FROM audit_event WHERE tenant_id = ? AND action = 'CONTAINER_BATCH_CREATED'",
            tenant.value())).isEqualTo(audits);

        // Une boîte seule ne peut pas reprendre un libellé de la série.
        assertThatThrownBy(() -> as(() -> createBox.execute("Test gel · 002/002", null, d10)))
            .isInstanceOf(ContainerLabelTakenException.class);

        // Un autre libellé commun passe, même s'il commence pareil.
        var other = as(() -> createBatch.execute("Commande 3", 1, "Test gel 2026", null, d10, "it"));
        assertThat(other.containers().get(0).label()).isEqualTo("Test gel 2026 · 001/001");
    }

    @Test
    @DisplayName("Deux créations simultanées du même libellé : une seule passe")
    void concurrent_creations_cannot_both_pass() throws Exception {
        int threads = 4;
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        as(() -> createBox.execute("Arrivée simultanée", null, d10));
                        return true;
                    } catch (ContainerLabelTakenException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int created = 0;
            for (var f : futures) if (f.get(30, java.util.concurrent.TimeUnit.SECONDS)) created++;
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("SELECT count(*) FROM physical_container WHERE tenant_id = ? AND label = ?",
            tenant.value(), "Arrivée simultanée")).isEqualTo(1);
    }

    @Test
    @DisplayName("Une boîte détruite libère son libellé")
    void destroyed_box_frees_its_label() {
        PhysicalContainer old = as(() -> createBox.execute("Courrier 2010", null, d10));
        as(() -> {
            PhysicalContainer box = containers.findById(tenant, old.id()).orElseThrow();
            box.destroy(Instant.now());
            return containers.save(box);
        });
        PhysicalContainer again = as(() -> createBox.execute("Courrier 2010", null, d10));
        assertThat(again.id()).isNotEqualTo(old.id());
    }

    @Test
    @DisplayName("Les salles de démonstration se créent toujours, sans aucun libellé en double")
    void demo_rooms_seed_without_duplicate_labels() {
        TenantId fresh = newTenant("seed");
        var rooms = TenantContext.callWithUnchecked(fresh, () -> devRooms.seedDemoRoom());
        assertThat(rooms).allSatisfy(r -> assertThat(r.boxes()).isPositive());
        int duplicates = count("""
            SELECT count(*) FROM (
              SELECT lower(label) FROM physical_container
              WHERE tenant_id = ? AND label IS NOT NULL
              GROUP BY lower(label) HAVING count(*) > 1) d
            """, fresh.value());
        assertThat(duplicates).isZero();
    }
}
