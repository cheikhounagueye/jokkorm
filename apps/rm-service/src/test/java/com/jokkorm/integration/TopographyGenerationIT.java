package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.application.physical.BoxFormatAdminUseCase;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.GenerateShelvingUseCase;
import com.jokkorm.application.physical.ImportTopographyFromExcelUseCase;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.physical.SetStorageSpecUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.ForbiddenRoleException;
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
import com.jokkorm.domain.physical.StorageLocationRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

/** Générateur à gabarit et import Excel étendu (pattern 10 § 4.4, 5T.C) contre un vrai Postgres. */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class TopographyGenerationIT {

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
    @Autowired ImportTopographyFromExcelUseCase importer;
    @Autowired SetStorageSpecUseCase setSpec;
    @Autowired StorageLocationRepository locations;
    @Autowired BoxFormatAdminUseCase formats;
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired PlaceContainerUseCase place;

    private TenantId tenant;
    private StorageLocation room;

    @BeforeEach
    void setUp() {
        tenant = TenantId.of("gen-" + UUID.randomUUID().toString().substring(0, 8));
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

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class, tenant.value());
    }

    private List<StorageLocation> shelves() {
        return as(() -> locations.findChildren(tenant, room.id()).stream()
            .filter(l -> l.kind() == StorageLocationKind.SHELF)
            .sorted(java.util.Comparator.comparing(StorageLocation::code)).toList());
    }

    private StorageLocation byPath(String path) {
        return as(() -> {
            StorageLocation node = locations.findRoots(tenant).stream().filter(r -> r.code().equals(path.split("/")[0])).findFirst().orElseThrow();
            for (String code : java.util.Arrays.copyOfRange(path.split("/"), 1, path.split("/").length)) {
                StorageLocation parent = node;
                node = locations.findChildren(tenant, parent.id()).stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
            }
            return node;
        });
    }

    private static GenerateShelvingUseCase.Template template(int aisles, int ranks, int faces) {
        return new GenerateShelvingUseCase.Template(aisles, ranks, faces, 6, 1, 1000,
            FurnitureType.FIXED_SHELVING, 350, 350, 2200);
    }

    private ImportTopographyFromExcelUseCase.ImportReport importRows(String[]... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Topographie");
            sheet.createRow(0).createCell(0).setCellValue("kind");
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < rows[r].length; c++) row.createCell(c).setCellValue(rows[r][c]);
            }
            wb.write(out);
            return as(() -> importer.execute(new ByteArrayInputStream(out.toByteArray()), "it"));
        }
    }

    // ─── Générateur ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("À blanc : 2 épis × 5 rangs × 2 faces = 20 travées, 120 tablettes, 120 ml — et rien n'est écrit, pas même l'audit")
    void dry_run_writes_nothing() {
        long nodes = count("SELECT COUNT(*) FROM storage_location WHERE tenant_id = ?");
        long audits = count("SELECT COUNT(*) FROM audit_event WHERE tenant_id = ?");

        var plan = as(() -> generator.execute(room.id(), template(2, 5, 2), true, "it"));

        assertThat(plan.travees()).isEqualTo(20);
        assertThat(plan.tablettes()).isEqualTo(120);
        assertThat(plan.positions()).isEqualTo(120);
        assertThat(plan.installedMm()).isEqualTo(120_000);
        assertThat(plan.aisleCodes()).containsExactly("E1", "E2");
        assertThat(plan.firstTravee()).isEqualTo("T01");
        assertThat(plan.lastTravee()).isEqualTo("T20");
        assertThat(plan.applied()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM storage_location WHERE tenant_id = ?")).isEqualTo(nodes);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE tenant_id = ?")).isEqualTo(audits);
    }

    @Test
    @DisplayName("Génération réelle : face A puis face B par épi, mesures et épi sur chaque travée, une position par tablette")
    void generation_builds_double_face_aisles() {
        as(() -> generator.execute(room.id(), template(2, 5, 2), false, "it"));

        List<StorageLocation> s = shelves();
        assertThat(s).hasSize(20);
        PhysicalSpec t01 = s.get(0).spec();
        PhysicalSpec t06 = s.get(5).spec();
        assertThat(t01).isEqualTo(new PhysicalSpec(FurnitureType.FIXED_SHELVING, 350, 350, 2200, 2, "E1", 1, "A"));
        assertThat(t06.aisleCode()).isEqualTo("E1");
        assertThat(t06.aisleOrder()).isEqualTo(1);
        assertThat(t06.aisleSide()).isEqualTo("B");
        assertThat(s.get(10).spec().aisleCode()).isEqualTo("E2");
        assertThat(s.get(0).lengthMm()).contains(1000);
        assertThat(count("SELECT COUNT(*) FROM storage_location WHERE tenant_id = ? AND kind = 'RACK'")).isEqualTo(120);
        assertThat(count("SELECT COUNT(*) FROM storage_location WHERE tenant_id = ? AND kind = 'POSITION'")).isEqualTo(120);
        assertThat(count("SELECT COUNT(*) FROM audit_event WHERE tenant_id = ? AND action = 'STORAGE_LOCATIONS_GENERATED'")).isEqualTo(1);
    }

    @Test
    @DisplayName("Numérotation : reprend après les travées et épis existants de la salle, sans collision")
    void numbering_continues_after_existing() {
        as(() -> {
            StorageLocation t03 = createLocation.createChild(room.id(), StorageLocationKind.SHELF, "T03", null);
            return setSpec.execute(t03.id(), new PhysicalSpec(null, null, null, null, null, "E1", 1, null), "it");
        });

        var plan = as(() -> generator.execute(room.id(), template(1, 2, 1), false, "it"));

        assertThat(plan.aisleCodes()).containsExactly("E2");
        assertThat(shelves()).extracting(StorageLocation::code).containsExactly("T03", "T04", "T05");
        assertThat(shelves().get(1).spec().aisleSide()).isNull();
    }

    @Test
    @DisplayName("Garde-fous : taille maximale, salle uniquement, demandeur refusé")
    void guards() {
        assertThatThrownBy(() -> as(() -> generator.execute(room.id(), template(50, 100, 2), true, "it")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("maximum");
        StorageLocation shelf = as(() -> createLocation.createChild(room.id(), StorageLocationKind.SHELF, "T01", null));
        assertThatThrownBy(() -> as(() -> generator.execute(shelf.id(), template(1, 1, 1), true, "it")))
            .isInstanceOf(IllegalArgumentException.class);
        RoleContext.set(Set.of(UserRole.REQUESTER));
        assertThatThrownBy(() -> as(() -> generator.execute(room.id(), template(1, 1, 1), true, "it")))
            .isInstanceOf(ForbiddenRoleException.class);
    }

    // ─── Import Excel ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Ancien fichier à 4 colonnes : importé exactement comme avant")
    void legacy_four_column_file() throws Exception {
        var report = importRows(
            new String[] {"SITE", "ABJ", "Abidjan", ""},
            new String[] {"STOREHOUSE", "MAG", "Magasin", "ABJ"},
            new String[] {"SHELF", "A", "Travée A", "ABJ/MAG"},
            new String[] {"RACK", "1", "", "ABJ/MAG/A"},
            new String[] {"POSITION", "001", "", "ABJ/MAG/A/1"});

        assertThat(report.created()).isEqualTo(5);
        assertThat(report.updated()).isZero();
        assertThat(report.errors()).isEmpty();
        assertThat(byPath("ABJ/MAG/A").spec().isEmpty()).isTrue();

        var again = importRows(new String[] {"SHELF", "A", "Travée A", "ABJ/MAG"});
        assertThat(again.skippedExisting()).isEqualTo(1);
        assertThat(again.created()).isZero();
    }

    @Test
    @DisplayName("Mesures en cm (virgule acceptée), libellé français du mobilier, nb_tablettes crée les tablettes")
    void measures_and_tablettes() throws Exception {
        var report = importRows(new String[] {"SHELF", "T01", "", "DKR/SALLE",
            "rayonnage fixe", "100", "35,5", "35", "220", "double", "e1", "1", "a", "3"});

        assertThat(report.errors()).isEmpty();
        StorageLocation t01 = byPath("DKR/SALLE/T01");
        assertThat(t01.lengthMm()).contains(1000);
        assertThat(t01.spec()).isEqualTo(new PhysicalSpec(FurnitureType.FIXED_SHELVING, 355, 350, 2200, 2, "E1", 1, "A"));
        assertThat(byPath("DKR/SALLE/T01/3/01").kind()).isEqualTo(StorageLocationKind.POSITION);
        assertThat(report.created()).isEqualTo(1 + 3 * 2);
    }

    @Test
    @DisplayName("Ligne sur une travée existante : seules les cases remplies changent, les autres sont gardées")
    void existing_node_is_merged() throws Exception {
        importRows(new String[] {"SHELF", "T01", "", "DKR/SALLE", "armoire", "", "40", "35"});

        var report = importRows(new String[] {"SHELF", "T01", "", "DKR/SALLE", "", "", "", "", "200"});

        assertThat(report.updated()).isEqualTo(1);
        assertThat(byPath("DKR/SALLE/T01").spec())
            .isEqualTo(new PhysicalSpec(FurnitureType.CABINET, 400, 350, 2000, null, null, null, null));
    }

    @Test
    @DisplayName("Une ligne qui rendrait une boîte rangée trop haute est refusée sans trace ; les bonnes lignes passent")
    void bad_line_leaves_no_trace() throws Exception {
        importRows(new String[] {"SHELF", "T01", "", "DKR/SALLE", "", "", "", "", "", "", "", "", "", "1"});
        StorageLocation pos = byPath("DKR/SALLE/T01/1/01");
        UUID d10 = as(() -> formats.list().stream().filter(f -> f.spineMm() == 100).map(BoxFormat::id).findFirst().orElseThrow());
        PhysicalContainer box = as(() -> createBox.execute("Boîte", null, d10));
        as(() -> place.execute(box.id(), pos.id(), "it"));

        var report = importRows(
            new String[] {"SHELF", "T01", "", "DKR/SALLE", "", "", "", "30"},          // 30 cm < boîte de 33 cm
            new String[] {"SHELF", "T02", "", "DKR/SALLE", "rayonnage fixe", "120"});   // bonne ligne

        assertThat(report.errors()).singleElement().asString()
            .contains("Ligne 2").contains("33 cm de haut").contains("ligne ignorée");
        assertThat(report.created()).isEqualTo(1);
        assertThat(byPath("DKR/SALLE/T01").spec().clearanceMm()).isNull();
        assertThat(byPath("DKR/SALLE/T02").lengthMm()).contains(1200);
    }

    @Test
    @DisplayName("Import réservé à l'archiviste (il peut changer des dimensions)")
    void requester_cannot_import() {
        RoleContext.set(Set.of(UserRole.REQUESTER));
        assertThatThrownBy(() -> importRows(new String[] {"SITE", "X", "", ""}))
            .isInstanceOf(ForbiddenRoleException.class);
        assertThat(Map.of()).isEmpty();
    }
}
