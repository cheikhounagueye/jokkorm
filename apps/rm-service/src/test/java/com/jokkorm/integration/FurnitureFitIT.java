package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.application.physical.BoxFormatAdminUseCase;
import com.jokkorm.application.physical.ChangeContainerFormatUseCase;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.PhysicalContainerConflictException;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.physical.RoomSpecUseCase;
import com.jokkorm.application.physical.SetStorageLengthUseCase;
import com.jokkorm.application.physical.SetStorageSpecUseCase;
import com.jokkorm.application.physical.StorageLocationConflictException;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
import com.jokkorm.domain.physical.FurnitureType;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalSpec;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
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
 * Mobilier et dimensions (pattern 10, 5T.B) contre un vrai Postgres : la boîte doit tenir en
 * hauteur et en profondeur, la tablette hérite de sa travée, et rien n'est contrôlé tant que
 * rien n'est mesuré. Les formats standard font 33 cm de haut et 25 cm de profondeur.
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class FurnitureFitIT {

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
    @Autowired SetStorageLengthUseCase setLength;
    @Autowired SetStorageSpecUseCase setSpec;
    @Autowired RoomSpecUseCase roomSpec;
    @Autowired BoxFormatAdminUseCase formats;
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired PlaceContainerUseCase place;
    @Autowired ChangeContainerFormatUseCase changeFormat;

    private TenantId tenant;
    private StorageLocation room;

    @BeforeEach
    void setUp() {
        tenant = TenantId.of("fit-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?)", tenant.value(), "IT " + tenant.value());
        RoleContext.set(Set.of(UserRole.ARCHIVIST));
        room = as(() -> {
            StorageLocation site = createLocation.createSite("DKR", "Dakar");
            return createLocation.createChild(site.id(), StorageLocationKind.STOREHOUSE, "SALLE", "Salle IT");
        });
    }

    @AfterEach
    void tearDown() {
        RoleContext.clear();
    }

    private <T> T as(Callable<T> body) {
        return TenantContext.callWithUnchecked(tenant, body);
    }

    private record Travee(StorageLocation shelf, StorageLocation rack, StorageLocation position) {}

    private Travee travee(String code) {
        return as(() -> {
            StorageLocation shelf = createLocation.createChild(room.id(), StorageLocationKind.SHELF, code, null);
            setLength.execute(shelf.id(), 1000, "it");
            StorageLocation rack = createLocation.createChild(shelf.id(), StorageLocationKind.RACK, "1", null);
            StorageLocation pos = createLocation.createChild(rack.id(), StorageLocationKind.POSITION, "01", null);
            return new Travee(shelf, rack, pos);
        });
    }

    private static PhysicalSpec shelf(Integer depthMm, Integer clearanceMm) {
        return new PhysicalSpec(FurnitureType.FIXED_SHELVING, depthMm, clearanceMm, null, null, null, null, null);
    }

    private static PhysicalSpec tablette(Integer depthMm, Integer clearanceMm) {
        return new PhysicalSpec(null, depthMm, clearanceMm, null, null, null, null, null);
    }

    private void spec(StorageLocation node, PhysicalSpec spec) {
        as(() -> setSpec.execute(node.id(), spec, "it"));
    }

    private int boxes;

    private PhysicalContainer box() {
        UUID d10 = as(() -> formats.list().stream().filter(f -> f.spineMm() == 100).map(BoxFormat::id).findFirst().orElseThrow());
        String label = "Boîte dos 10 n° " + (++boxes);   // un libellé, une seule boîte
        return as(() -> createBox.execute(label, null, d10));
    }

    private PhysicalContainer put(PhysicalContainer box, StorageLocation position) {
        return as(() -> place.execute(box.id(), position.id(), "it"));
    }

    @Test
    @DisplayName("Rien de mesuré : aucun contrôle de hauteur ni de profondeur (données existantes intactes)")
    void unmeasured_furniture_is_not_checked() {
        Travee t = travee("T01");
        assertThat(put(box(), t.position()).positionId()).contains(t.position().id());
    }

    @Test
    @DisplayName("Travée à 30 cm de hauteur libre : une boîte de 33 cm est refusée, message chiffré")
    void too_tall_box_is_refused() {
        Travee t = travee("T01");
        spec(t.shelf(), shelf(null, 300));

        PhysicalContainer b = box();
        assertThatThrownBy(() -> put(b, t.position()))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("33 cm de haut")
            .hasMessageContaining("30 cm de hauteur libre");
    }

    @Test
    @DisplayName("Une tablette plus haute que sa travée accepte la boîte : la tablette prime sur la travée")
    void tablette_override_wins_over_travee() {
        Travee t = travee("T01");
        spec(t.shelf(), shelf(null, 300));
        spec(t.rack(), tablette(null, 350));

        assertThat(put(box(), t.position()).positionId()).contains(t.position().id());
    }

    @Test
    @DisplayName("Travée de 20 cm de profondeur : une boîte de 25 cm de profondeur est refusée")
    void too_deep_box_is_refused() {
        Travee t = travee("T01");
        spec(t.shelf(), shelf(200, null));

        assertThatThrownBy(() -> put(box(), t.position()))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("25 cm de profondeur");
    }

    @Test
    @DisplayName("Baisser la hauteur libre sous une boîte déjà rangée est refusé, sur la travée comme sur la tablette")
    void cannot_lower_clearance_below_stored_box() {
        Travee t = travee("T01");
        put(box(), t.position());

        assertThatThrownBy(() -> spec(t.shelf(), shelf(null, 300)))
            .isInstanceOf(StorageLocationConflictException.class)
            .hasMessageContaining("déjà rangée");
        assertThatThrownBy(() -> spec(t.rack(), tablette(null, 300)))
            .isInstanceOf(StorageLocationConflictException.class);

        spec(t.shelf(), shelf(300, 350));                     // assez haut et profond : accepté
        assertThat(as(() -> jdbc.queryForObject(
            "SELECT clearance_mm FROM storage_location WHERE id = ?", Integer.class, t.shelf().id()))).isEqualTo(350);
    }

    @Test
    @DisplayName("Changer pour un format plus haut que la hauteur libre est refusé")
    void taller_format_change_is_refused() {
        Travee t = travee("T01");
        spec(t.shelf(), shelf(null, 350));
        PhysicalContainer b = put(box(), t.position());
        BoxFormat tall = as(() -> formats.create(new BoxFormatAdminUseCase.NewFormat(
            "HAUTE-40", "Boîte haute 40 cm", 100, 250, 400, null, null), "it"));

        assertThatThrownBy(() -> as(() -> changeFormat.execute(b.id(), tall.id(), "it")))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("40 cm de haut");
    }

    @Test
    @DisplayName("Une tablette ne porte ni mobilier ni épi : ils se règlent sur la travée")
    void rack_refuses_shelf_only_fields() {
        Travee t = travee("T01");
        assertThatThrownBy(() -> spec(t.rack(), shelf(null, 350)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("travée");
    }

    @Test
    @DisplayName("Salle : mobilier plus haut que le plafond, épi trop long, doublon d'épi → avertissements, jamais de blocage")
    void room_limits_warn() {
        Travee a = travee("T01");
        Travee b = travee("T02");
        spec(a.shelf(), new PhysicalSpec(FurnitureType.FIXED_SHELVING, 400, 350, 2500, 2, "E1", 1, "A"));
        spec(b.shelf(), new PhysicalSpec(FurnitureType.FIXED_SHELVING, 400, 350, 2000, 2, "E1", 1, "A"));

        var view = as(() -> roomSpec.set(room.id(), new RoomSpecUseCase.Input(
            1500, 1200, 2200, null, null, 18.0, 22.0, 45, 55, null), "it"));

        assertThat(view.spec().ceilingHeightMm()).isEqualTo(2200);
        assertThat(view.limits().footprintM2()).isEqualTo(0.8);        // 2 × 1 m × 0,4 m
        assertThat(view.limits().areaM2()).isEqualTo(1.8);             // 1,5 × 1,2 m
        assertThat(view.limits().warnings()).anyMatch(w -> w.contains("T01") && w.contains("sous plafond"));
        assertThat(view.limits().warnings()).anyMatch(w -> w.startsWith("Épi E1 :") && w.contains("2,00 m de long"));
        assertThat(view.limits().warnings()).anyMatch(w -> w.contains("plusieurs travées au même endroit"));
        assertThat(put(box(), a.position()).positionId()).isPresent(); // un avertissement ne bloque pas
    }

    @Test
    @DisplayName("Consignes climatiques incohérentes (min > max) refusées")
    void inconsistent_climate_is_refused() {
        assertThatThrownBy(() -> as(() -> roomSpec.set(room.id(), new RoomSpecUseCase.Input(
            null, null, null, null, null, 25.0, 18.0, null, null, null), "it")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
