package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.application.loan.MarkLoanOutUseCase;
import com.jokkorm.application.loan.RequestLoanUseCase;
import com.jokkorm.application.physical.ContainerNotAtPositionException;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.GenerateShelvingUseCase;
import com.jokkorm.application.physical.MagasinScanService;
import com.jokkorm.application.physical.PhysicalContainerConflictException;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.physical.PositionLabelsPdfGenerator;
import com.jokkorm.application.physical.ReceiveContainerUseCase;
import com.jokkorm.application.physical.RemoveContainerFromShelfUseCase;
import com.jokkorm.application.physical.ScanCodeUnknownException;
import com.jokkorm.application.physical.SetStorageLengthUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.FurnitureType;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mode magasin (pattern 12) contre un vrai Postgres : étiquettes d'emplacement, et les gestes
 * arrivée (1 scan), placer (2 scans), retirer (3 scans) tels que l'iPhone les enchaîne.
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class MagasinGesturesIT {

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
    @Autowired GenerateShelvingUseCase generator;
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired PlaceContainerUseCase place;
    @Autowired MagasinScanService scan;
    @Autowired ReceiveContainerUseCase receive;
    @Autowired RemoveContainerFromShelfUseCase remove;
    @Autowired PositionLabelsPdfGenerator labels;
    @Autowired RequestLoanUseCase requestLoan;
    @Autowired MarkLoanOutUseCase markOut;
    @Autowired PhysicalContainerRepository containers;

    private TenantId tenant;
    private StorageLocation room;
    private int boxes;

    @BeforeEach
    void setUp() {
        tenant = TenantId.of("mag-" + UUID.randomUUID().toString().substring(0, 8));
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

    private List<StorageLocation> travee(String code, int tablettes) {
        return as(() -> {
            StorageLocation shelf = createLocation.createChild(room.id(), StorageLocationKind.SHELF, code, null);
            setLength.execute(shelf.id(), 1000, "it");
            List<StorageLocation> positions = new java.util.ArrayList<>();
            for (int i = 1; i <= tablettes; i++) {
                StorageLocation rack = createLocation.createChild(shelf.id(), StorageLocationKind.RACK, "" + i, null);
                positions.add(createLocation.createChild(rack.id(), StorageLocationKind.POSITION, "01", null));
            }
            return positions;
        });
    }

    private PhysicalContainer box() {
        String label = "Boîte magasin n° " + (++boxes);
        return as(() -> createBox.execute(label, null, null));
    }

    private PhysicalContainer reload(PhysicalContainer box) {
        return as(() -> containers.findById(tenant, box.id())).orElseThrow();
    }

    private long audits(String action, UUID target) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = ? AND target_id = ?",
            Long.class, action, target);
    }

    // ─── Étiquettes d'emplacement ───────────────────────────────────────────

    @Test
    @DisplayName("Tout emplacement créé (à la main ou par le générateur) a un code d'étiquette ; la base refuse un emplacement sans code")
    void every_position_gets_a_scan_code() {
        travee("T01", 2);
        as(() -> generator.execute(room.id(), new GenerateShelvingUseCase.Template(1, 2, 1, 3, 2, 1000,
            FurnitureType.FIXED_SHELVING, 350, 350, 2200), false, "it"));

        Long positions = jdbc.queryForObject(
            "SELECT COUNT(*) FROM storage_location WHERE tenant_id = ? AND kind = 'POSITION'", Long.class, tenant.value());
        Long withoutCode = jdbc.queryForObject(
            "SELECT COUNT(*) FROM storage_location WHERE tenant_id = ? AND kind = 'POSITION' AND scan_code IS NULL",
            Long.class, tenant.value());
        assertThat(positions).isEqualTo(2 + 2 * 3 * 2);
        assertThat(withoutCode).isZero();

        UUID rack = jdbc.queryForObject(
            "SELECT id FROM storage_location WHERE tenant_id = ? AND kind = 'RACK' LIMIT 1", UUID.class, tenant.value());
        assertThatThrownBy(() -> jdbc.update("""
            INSERT INTO storage_location (id, tenant_id, kind, code, parent_id, path, created_at, updated_at, version)
            VALUES (?, ?, 'POSITION', '99', ?, 'X/99', now(), now(), 0)""", UUID.randomUUID(), tenant.value(), rack))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Planche d'étiquettes : un PDF avec une étiquette par emplacement sous le nœud, impression tracée")
    void position_labels_pdf() {
        travee("T01", 3);
        List<PositionLabelsPdfGenerator.PositionLabel> list = as(() -> labels.list(room.id()));
        assertThat(list).hasSize(3);
        assertThat(list.get(0).address()).isEqualTo("Salle IT (SALLE) · Travée T01 · Tablette 1 · Emplacement 01");

        byte[] pdf = as(() -> labels.generate(room.id(), "it"));
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(audits("POSITION_LABELS_PRINTED", room.id())).isEqualTo(1);
    }

    // ─── Identifier ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scan : une boîte, un emplacement (même tapé en minuscules), un code inconnu")
    void scan_resolves_boxes_and_positions() {
        StorageLocation p = travee("T01", 1).get(0);
        PhysicalContainer b = box();
        as(() -> place.execute(b.id(), p.id(), "it"));

        var boxScan = as(() -> scan.resolve(" " + b.barcode().toLowerCase() + " "));
        assertThat(boxScan.container().container().id()).isEqualTo(b.id());
        assertThat(boxScan.container().address()).endsWith("Travée T01 · Tablette 1 · Emplacement 01");
        assertThat(boxScan.position()).isNull();

        var posScan = as(() -> scan.resolve(p.scanCode().orElseThrow().toLowerCase()));
        assertThat(posScan.position().position().id()).isEqualTo(p.id());
        assertThat(posScan.container()).isNull();

        assertThatThrownBy(() -> as(() -> scan.resolve("EMP-NOPE0000")))
            .isInstanceOf(ScanCodeUnknownException.class);
    }

    // ─── Arrivée (1 scan) ───────────────────────────────────────────────────

    @Test
    @DisplayName("Arrivée : pointée avec qui et quand ; re-pointer ne change rien ; une boîte rangée ou en prêt est refusée")
    void arrival() {
        PhysicalContainer b = box();
        var first = as(() -> receive.execute(b.barcode(), "awa.diop"));
        assertThat(first.alreadyReceived()).isFalse();
        assertThat(reload(b).receivedBy()).contains("awa.diop");
        Instant at = reload(b).receivedAt().orElseThrow();

        var again = as(() -> receive.execute(b.barcode(), "moussa"));
        assertThat(again.alreadyReceived()).isTrue();
        assertThat(reload(b).receivedAt()).contains(at);
        assertThat(audits("CONTAINER_RECEIVED", b.id())).isEqualTo(1);

        StorageLocation p = travee("T01", 1).get(0);
        as(() -> place.execute(b.id(), p.id(), "it"));
        assertThatThrownBy(() -> as(() -> receive.execute(b.barcode(), "awa.diop")))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("déjà rangée")
            .hasMessageContaining("Retirer");

        var loan = as(() -> requestLoan.execute(b.id(), "Awa Diop", "DRH", "Audit",
            Instant.now().plus(Duration.ofDays(7)), "it"));
        as(() -> markOut.execute(loan.id(), "it"));
        assertThatThrownBy(() -> as(() -> receive.execute(b.barcode(), "awa.diop")))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("en prêt");
    }

    // ─── Placer (2 scans) ───────────────────────────────────────────────────

    @Test
    @DisplayName("Placer : une simple demande de prêt laisse la boîte en rayon, on peut la déplacer")
    void requested_loan_does_not_block_a_move() {
        List<StorageLocation> t = travee("T01", 2);
        PhysicalContainer b = box();
        as(() -> place.execute(b.id(), t.get(0).id(), "it"));
        as(() -> requestLoan.execute(b.id(), "Awa Diop", "DRH", "Audit",
            Instant.now().plus(Duration.ofDays(7)), "it"));

        PhysicalContainer moved = as(() -> place.execute(b.id(), t.get(1).id(), "it"));
        assertThat(moved.positionId()).contains(t.get(1).id());
    }

    // ─── Retirer (3 scans) ──────────────────────────────────────────────────

    @Test
    @DisplayName("Retirer : au bon emplacement la boîte repasse en zone tampon, tracé ; puis elle peut être pointée à nouveau")
    void removal_at_the_right_place() {
        StorageLocation p = travee("T01", 1).get(0);
        PhysicalContainer b = box();
        as(() -> receive.execute(b.barcode(), "awa.diop"));
        as(() -> place.execute(b.id(), p.id(), "it"));

        PhysicalContainer removed = as(() -> remove.execute(b.id(), p.id(), "awa.diop"));
        assertThat(removed.positionId()).isEmpty();
        assertThat(reload(b).positionId()).isEmpty();
        assertThat(audits("CONTAINER_REMOVED", b.id())).isEqualTo(1);
        String detail = jdbc.queryForObject(
            "SELECT metadata FROM audit_event WHERE action = 'CONTAINER_REMOVED' AND target_id = ?", String.class, b.id());
        assertThat(detail).contains("from=DKR/SALLE/T01/1/01");

        var back = as(() -> receive.execute(b.barcode(), "moussa"));
        assertThat(back.alreadyReceived()).isFalse();
        assertThat(reload(b).receivedBy()).contains("moussa");
    }

    @Test
    @DisplayName("Retirer au mauvais emplacement : refus avec l'adresse enregistrée, la boîte ne bouge pas")
    void removal_at_the_wrong_place_is_refused() {
        List<StorageLocation> t = travee("T01", 2);
        PhysicalContainer b = box();
        as(() -> place.execute(b.id(), t.get(0).id(), "it"));

        assertThatThrownBy(() -> as(() -> remove.execute(b.id(), t.get(1).id(), "awa.diop")))
            .isInstanceOf(ContainerNotAtPositionException.class)
            .hasMessageContaining("Placer")
            .satisfies(e -> assertThat(((ContainerNotAtPositionException) e).currentAddress())
                .endsWith("Travée T01 · Tablette 1 · Emplacement 01"));
        assertThat(reload(b).positionId()).contains(t.get(0).id());
        assertThat(audits("CONTAINER_REMOVED", b.id())).isZero();
    }

    @Test
    @DisplayName("Retirer : refusé pour une boîte déjà en zone tampon ou sortie en prêt")
    void removal_refused_off_shelf_or_on_loan() {
        StorageLocation p = travee("T01", 1).get(0);
        PhysicalContainer loose = box();
        assertThatThrownBy(() -> as(() -> remove.execute(loose.id(), p.id(), "awa.diop")))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("zone tampon");

        PhysicalContainer lent = box();
        as(() -> place.execute(lent.id(), p.id(), "it"));
        var loan = as(() -> requestLoan.execute(lent.id(), "Awa Diop", "DRH", "Audit",
            Instant.now().plus(Duration.ofDays(7)), "it"));
        as(() -> markOut.execute(loan.id(), "it"));
        assertThatThrownBy(() -> as(() -> remove.execute(lent.id(), p.id(), "awa.diop")))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("fiche fantôme");
        assertThat(reload(lent).positionId()).contains(p.id());
    }
}
