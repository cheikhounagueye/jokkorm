package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.boot.Application;
import com.jokkorm.application.loan.MarkLoanOutUseCase;
import com.jokkorm.application.loan.MarkLoanReturnedUseCase;
import com.jokkorm.application.loan.RequestLoanUseCase;
import com.jokkorm.application.physical.BoxFormatAdminUseCase;
import com.jokkorm.application.physical.ChangeContainerFormatUseCase;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.GetRoomViewUseCase;
import com.jokkorm.application.physical.PhysicalContainerConflictException;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.physical.RoomView;
import com.jokkorm.application.physical.SetStorageLengthUseCase;
import com.jokkorm.application.physical.StorageLocationConflictException;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.physical.BoxFormat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Rangements et déplacements de boîtes comme sur le terrain (pattern 10), contre un vrai
 * Postgres. Chaque action passe par son use case transactionnel, puis l'occupation est
 * relue par la vue de salle : ce que l'écran affiche doit être ce que le rangement accepte.
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class ContainerMoveOccupancyIT {

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
    @Autowired BoxFormatAdminUseCase formats;
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired PlaceContainerUseCase place;
    @Autowired ChangeContainerFormatUseCase changeFormat;
    @Autowired GetRoomViewUseCase roomView;
    @Autowired RequestLoanUseCase requestLoan;
    @Autowired MarkLoanOutUseCase markOut;
    @Autowired MarkLoanReturnedUseCase markReturned;
    @Autowired PhysicalContainerRepository containers;

    /** Tenant neuf à chaque test : créé après la migration 0013, donc sans catalogue au départ. */
    private TenantId tenant;
    private StorageLocation room;

    @BeforeEach
    void setUp() {
        tenant = TenantId.of("move-" + UUID.randomUUID().toString().substring(0, 8));
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

    // ─── Outils ─────────────────────────────────────────────────────────────

    private <T> T as(Callable<T> body) {
        return TenantContext.callWithUnchecked(tenant, body);
    }

    /** Travée de {@code mm} avec {@code tablettes} tablettes d'un emplacement chacune. Renvoie les emplacements. */
    private List<StorageLocation> travee(String code, int mm, int tablettes) {
        return as(() -> {
            StorageLocation shelf = createLocation.createChild(room.id(), StorageLocationKind.SHELF, code, null);
            setLength.execute(shelf.id(), mm, "it");
            List<StorageLocation> positions = new java.util.ArrayList<>();
            for (int i = 1; i <= tablettes; i++) {
                StorageLocation rack = createLocation.createChild(shelf.id(), StorageLocationKind.RACK, "" + i, null);
                positions.add(createLocation.createChild(rack.id(), StorageLocationKind.POSITION, "01", null));
            }
            return positions;
        });
    }

    private UUID format(int spineMm) {
        return as(() -> formats.list().stream().filter(f -> f.spineMm() == spineMm)
            .map(BoxFormat::id).findFirst().orElseThrow());
    }

    private int boxes;

    private PhysicalContainer box(int spineMm) {
        UUID f = format(spineMm);
        String label = "Boîte dos " + spineMm / 10 + " n° " + (++boxes);   // un libellé, une seule boîte
        return as(() -> createBox.execute(label, null, f));
    }

    private PhysicalContainer put(PhysicalContainer box, StorageLocation position) {
        return as(() -> place.execute(box.id(), position.id(), "it"));
    }

    private RoomView.Occupancy occ(StorageLocation position) {
        RoomView view = as(() -> roomView.execute(room.id()));
        return view.shelves().stream().flatMap(s -> s.racks().stream()).flatMap(r -> r.positions().stream())
            .filter(p -> p.position().id().equals(position.id()))
            .findFirst().orElseThrow().occupancy();
    }

    private RoomView.Occupancy roomOcc() {
        return as(() -> roomView.execute(room.id())).occupancy();
    }

    private long audits(String action, UUID target) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_event WHERE action = ? AND target_id = ?",
            Long.class, action, target);
    }

    // ─── Scénarios ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Déplacement A → B : A libère le dos, B le prend, le total de la salle ne bouge pas, mouvement tracé")
    void move_frees_source_and_fills_destination() {
        List<StorageLocation> t = travee("T01", 1000, 2);
        StorageLocation a = t.get(0), b = t.get(1);
        PhysicalContainer d15 = box(150);

        put(d15, a);
        assertThat(occ(a).usedMm()).isEqualTo(150);
        assertThat(occ(b).usedMm()).isZero();
        int roomBefore = roomOcc().usedMm();

        PhysicalContainer moved = put(d15, b);

        assertThat(moved.positionId()).contains(b.id());
        assertThat(occ(a).usedMm()).isZero();
        assertThat(occ(a).boxes()).isZero();
        assertThat(occ(b).usedMm()).isEqualTo(150);
        assertThat(occ(b).boxes()).isEqualTo(1);
        assertThat(roomOcc().usedMm()).isEqualTo(roomBefore).isEqualTo(150);
        assertThat(occ(b).rate()).isEqualTo(0.15);
        assertThat(audits("CONTAINER_PLACED", d15.id())).isEqualTo(1);
        assertThat(audits("CONTAINER_MOVED", d15.id())).isEqualTo(1);
    }

    @Test
    @DisplayName("Tablette de 100 cm : 4 dos 15 + 3 dos 10 = 90 cm, reste 5 cm rangeables, dos 10 et dos 8 refusés")
    void mixed_formats_respect_handling_margin() {
        StorageLocation p = travee("T01", 1000, 1).get(0);
        for (int i = 0; i < 4; i++) put(box(150), p);
        for (int i = 0; i < 3; i++) put(box(100), p);

        RoomView.Occupancy o = occ(p);
        assertThat(o.boxes()).isEqualTo(7);
        assertThat(o.usedMm()).isEqualTo(900);
        assertThat(o.lengthMm()).isEqualTo(1000);
        assertThat(o.placeableMm()).isEqualTo(50);   // 950 rangeables (marge 5 %) − 900

        PhysicalContainer d10 = box(100);
        assertThatThrownBy(() -> put(d10, p))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("marge de manipulation")
            .hasMessageContaining("5 cm");
        assertThatThrownBy(() -> put(box(80), p)).isInstanceOf(PhysicalContainerConflictException.class);
        assertThat(occ(p).usedMm()).isEqualTo(900);
        assertThat(as(() -> containers.findById(tenant, d10.id())).orElseThrow().positionId()).isEmpty();
    }

    @Test
    @DisplayName("Déplacement refusé faute de place : la boîte reste où elle était, rien ne bouge")
    void refused_move_changes_nothing() {
        List<StorageLocation> t = travee("T01", 1000, 2);
        StorageLocation a = t.get(0), full = t.get(1);
        for (int i = 0; i < 4; i++) put(box(200), full);      // 800 mm
        PhysicalContainer d20 = put(box(200), a);

        assertThatThrownBy(() -> put(d20, full)).isInstanceOf(PhysicalContainerConflictException.class);

        assertThat(as(() -> containers.findById(tenant, d20.id())).orElseThrow().positionId()).contains(a.id());
        assertThat(occ(a).usedMm()).isEqualTo(200);
        assertThat(occ(full).usedMm()).isEqualTo(800);
        assertThat(audits("CONTAINER_MOVED", d20.id())).isZero();
    }

    @Test
    @DisplayName("Boîte en prêt : on ne la déplace pas, sa fiche fantôme garde sa place ; au retour elle se range")
    void loaned_box_keeps_its_place() {
        List<StorageLocation> t = travee("T01", 1000, 2);
        StorageLocation a = t.get(0), b = t.get(1);
        PhysicalContainer box = put(box(100), a);
        var loan = as(() -> requestLoan.execute(box.id(), "Awa Diop", "DRH", "Audit",
            Instant.now().plus(Duration.ofDays(7)), "it"));
        as(() -> markOut.execute(loan.id(), "it"));

        assertThatThrownBy(() -> put(box, b))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("en prêt");
        assertThat(occ(a).usedMm()).isEqualTo(100);
        assertThat(occ(a).boxes()).isEqualTo(1);

        as(() -> markReturned.execute(loan.id(), "it"));
        put(box, b);
        assertThat(occ(a).usedMm()).isZero();
        assertThat(occ(b).usedMm()).isEqualTo(100);
    }

    @Test
    @DisplayName("Re-scanner une boîte sur sa propre position ne change rien et n'écrit pas de mouvement")
    void self_move_is_a_no_op() {
        StorageLocation p = travee("T01", 1000, 1).get(0);
        PhysicalContainer first = put(box(100), p);
        PhysicalContainer again = put(first, p);

        assertThat(again.lastMovedAt()).isEqualTo(first.lastMovedAt());
        assertThat(occ(p).usedMm()).isEqualTo(100);
        assertThat(audits("CONTAINER_PLACED", first.id())).isEqualTo(1);
        assertThat(audits("CONTAINER_MOVED", first.id())).isZero();
    }

    @Test
    @DisplayName("Position migrée de la phase 5S (600 mm) : 6 boîtes dos 10, la 7e est refusée")
    void migrated_position_keeps_six_places() {
        StorageLocation p = as(() -> {
            StorageLocation shelf = createLocation.createChild(room.id(), StorageLocationKind.SHELF, "T09", null);
            StorageLocation rack = createLocation.createChild(shelf.id(), StorageLocationKind.RACK, "1", null);
            setLength.execute(rack.id(), 3600, "it");
            StorageLocation pos = null;
            for (int i = 1; i <= 6; i++) {
                StorageLocation created = createLocation.createChild(rack.id(), StorageLocationKind.POSITION,
                    "%02d".formatted(i), null);
                setLength.execute(created.id(), 600, "it");
                if (i == 1) pos = created;
            }
            return pos;
        });
        for (int i = 0; i < 6; i++) put(box(100), p);
        assertThat(occ(p).usedMm()).isEqualTo(600);
        assertThat(occ(p).places()).isEqualTo(6);
        assertThat(occ(p).rate()).isEqualTo(1.0);
        assertThatThrownBy(() -> put(box(100), p))
            .isInstanceOf(PhysicalContainerConflictException.class)
            .hasMessageContaining("plein");
    }

    @Test
    @DisplayName("Longueurs incohérentes refusées : emplacement plus court que ses boîtes")
    void length_cannot_shrink_below_stored_boxes() {
        StorageLocation p = travee("T01", 1000, 1).get(0);
        for (int i = 0; i < 5; i++) put(box(150), p);        // 750 mm
        assertThatThrownBy(() -> as(() -> setLength.execute(p.id(), 700, "it")))
            .isInstanceOf(StorageLocationConflictException.class);
        as(() -> setLength.execute(p.id(), 800, "it"));
        assertThat(occ(p).lengthMm()).isEqualTo(800);
        assertThat(occ(p).usedMm()).isEqualTo(750);
    }

    @Test
    @DisplayName("Correction de format : un dos 10 qui est en réalité un dos 20 doit encore tenir")
    void format_change_rechecks_space() {
        StorageLocation p = travee("T01", 1000, 1).get(0);
        for (int i = 0; i < 4; i++) put(box(200), p);        // 800 mm
        PhysicalContainer d10 = put(box(100), p);            // 900 mm
        UUID d20 = format(200);

        assertThatThrownBy(() -> as(() -> changeFormat.execute(d10.id(), d20, "it")))
            .isInstanceOf(PhysicalContainerConflictException.class);
        assertThat(occ(p).usedMm()).isEqualTo(900);

        UUID d8 = format(80);
        as(() -> changeFormat.execute(d10.id(), d8, "it"));
        assertThat(occ(p).usedMm()).isEqualTo(880);
    }

    @Test
    @DisplayName("Un nouveau tenant reçoit le catalogue standard, dos 10 par défaut")
    void new_tenant_gets_standard_catalogue() {
        List<BoxFormat> all = as(() -> formats.list());
        assertThat(all).extracting(BoxFormat::spineMm).containsExactly(80, 100, 150, 200);
        assertThat(all).filteredOn(BoxFormat::isDefault).extracting(BoxFormat::spineMm).containsExactly(100);
    }
}
