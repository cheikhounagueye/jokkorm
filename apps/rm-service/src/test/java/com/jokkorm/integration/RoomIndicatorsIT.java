package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.jokkorm.application.physical.BoxFormatAdminUseCase;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.GenerateShelvingUseCase;
import com.jokkorm.application.physical.GetRoomIndicatorsUseCase;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.physical.RoomSpecUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.FurnitureType;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.util.List;
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

/** Indicateurs en mètres linéaires d'une salle (pattern 10 § 3, 5T.D) contre un vrai Postgres. */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class RoomIndicatorsIT {

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

    @Autowired JdbcTemplate jdbc;
    @Autowired CreateStorageLocationUseCase createLocation;
    @Autowired GenerateShelvingUseCase generator;
    @Autowired RoomSpecUseCase roomSpec;
    @Autowired GetRoomIndicatorsUseCase indicators;
    @Autowired StorageLocationRepository locations;
    @Autowired BoxFormatAdminUseCase formats;
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired PlaceContainerUseCase place;

    private TenantId tenant;
    private StorageLocation room;
    private List<StorageLocation> positions;

    @BeforeEach
    void setUp() {
        tenant = TenantId.of("ind-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?)", tenant.value(), "IT " + tenant.value());
        RoleContext.set(Set.of(UserRole.ARCHIVIST));
        room = as(() -> {
            StorageLocation site = createLocation.createSite("DKR", "Dakar");
            return createLocation.createChild(site.id(), StorageLocationKind.STOREHOUSE, "SALLE", "Salle IT");
        });
        // 1 épi double face × 1 rang, 2 tablettes de 1 m, 40 cm de profondeur par face : 4 ml installés.
        as(() -> generator.execute(room.id(), new GenerateShelvingUseCase.Template(1, 1, 2, 2, 1, 1000,
            FurnitureType.FIXED_SHELVING, 400, 350, 2000), false, "it"));
        positions = as(() -> {
            List<StorageLocation> out = new java.util.ArrayList<>();
            for (StorageLocation shelf : locations.findChildren(tenant, room.id())) {
                for (StorageLocation rack : locations.findChildren(tenant, shelf.id())) {
                    out.addAll(locations.findChildren(tenant, rack.id()));
                }
            }
            return out;
        });
        UUID d10 = as(() -> formats.list().stream().filter(f -> f.spineMm() == 100).map(BoxFormat::id).findFirst().orElseThrow());
        for (int i = 1; i <= 3; i++) {
            String label = "Boîte " + i;   // un libellé, une seule boîte
            PhysicalContainer box = as(() -> createBox.execute(label, null, d10));
            as(() -> place.execute(box.id(), positions.get(0).id(), "it"));
        }
    }

    @AfterEach
    void tearDown() {
        RoleContext.clear();
    }

    private <T> T as(Callable<T> body) {
        return TenantContext.callWithUnchecked(tenant, body);
    }

    private GetRoomIndicatorsUseCase.Indicators ind() {
        return as(() -> indicators.execute(room.id()));
    }

    @Test
    @DisplayName("Mètres linéaires : 4 ml installés, 30 cm occupés, ventilés par épi ; boîtes qui tiennent encore par format")
    void linear_metres_and_capacity() {
        var i = ind();

        assertThat(i.installedMm()).isEqualTo(4000);
        assertThat(i.usedMm()).isEqualTo(300);
        assertThat(i.placeableMm()).isEqualTo(4 * 950 - 300);              // marge de 5 % par tablette
        assertThat(i.byAisle()).singleElement().satisfies(l -> {
            assertThat(l.label()).isEqualTo("Épi E1");
            assertThat(l.travees()).isEqualTo(2);
            assertThat(l.usedMm()).isEqualTo(300);
        });
        // Tablette avec 3 boîtes : 650 mm → 6 dos 10 ; 3 tablettes vides : 950 mm → 9 chacune.
        assertThat(i.capacityByFormat()).filteredOn(f -> f.spineMm() == 100)
            .singleElement().extracting(GetRoomIndicatorsUseCase.FitCount::boxes).isEqualTo(6L + 3 * 9L);
    }

    @Test
    @DisplayName("Sans poids moyen au ml : charge non calculée ; avec 500 kg/ml saisis : 150 kg sur 0,8 m², au-dessus de la limite")
    void load_only_when_weight_is_given() {
        var before = ind();
        assertThat(before.load()).isNull();
        assertThat(before.loadNote()).contains("non renseigné");

        as(() -> roomSpec.set(room.id(), new RoomSpecUseCase.Input(5000, 4000, 2800, null, 100, null, null, null, null, 500.0), "it"));
        var after = ind();

        assertThat(after.load().estimatedKg()).isEqualTo(150.0);
        assertThat(after.load().footprintM2()).isEqualTo(0.8);
        assertThat(after.load().kgPerM2()).isEqualTo(187.5);
        assertThat(after.load().overLimit()).isTrue();                        // limite saisie : 100 kg/m²
        assertThat(after.densityMlPerM2()).isEqualTo(0.2);                     // 4 ml / 20 m²
    }

    @Test
    @DisplayName("Date de saturation : refusée sous 90 jours d'historique, calculée sur le rythme des 12 derniers mois")
    void saturation_needs_history() {
        assertThat(ind().saturation().date()).isNull();
        assertThat(ind().saturation().note()).contains("Historique insuffisant");

        // La première boîte est arrivée il y a 200 jours : 300 mm en 200 jours ≈ 45,7 mm/mois.
        jdbc.update("UPDATE physical_container SET last_moved_at = now() - interval '200 days' WHERE id = ("
            + "SELECT id FROM physical_container WHERE tenant_id = ? ORDER BY created_at LIMIT 1)", tenant.value());
        var s = ind().saturation();

        assertThat(s.historyDays()).isBetween(199L, 201L);
        assertThat(s.intakeMm()).isEqualTo(300);
        assertThat(s.mmPerMonth()).isBetween(45.0, 46.5);
        assertThat(s.monthsLeft()).isBetween(75.0, 77.5);                      // 3 500 mm rangeables ÷ 45,7
        assertThat(s.date()).isNotNull();
    }
}
