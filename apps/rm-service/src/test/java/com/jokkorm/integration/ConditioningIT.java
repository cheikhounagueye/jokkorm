package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jokkorm.application.loan.RequestLoanUseCase;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.physical.CreateStorageLocationUseCase;
import com.jokkorm.application.physical.MagasinScanService;
import com.jokkorm.application.physical.PlaceContainerUseCase;
import com.jokkorm.application.physical.SetStorageLengthUseCase;
import com.jokkorm.application.record.ApplyLegalHoldUseCase;
import com.jokkorm.application.record.ConditionRecordsUseCase;
import com.jokkorm.application.record.ConditioningRefusedException;
import com.jokkorm.application.record.RecordContainerConflictException;
import com.jokkorm.application.record.StoreRecordInContainerUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.PageRequest;
import com.jokkorm.domain.physical.ContainerState;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.physical.PhysicalContainerRepository;
import com.jokkorm.domain.physical.StorageLocation;
import com.jokkorm.domain.physical.StorageLocationKind;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * Conditionnement (étude du 24 novembre 2026, lot 1) contre un vrai Postgres : liste « À
 * conditionner », mise en boîte en zone tampon (D1), tout ou rien, capacité (R4) même sous
 * deux mises en boîte simultanées, boîte en prêt ou fermée.
 */
@SpringBootTest(classes = Application.class)
@Testcontainers
@ActiveProfiles("test")
class ConditioningIT {

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
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired PlaceContainerUseCase place;
    @Autowired MagasinScanService scan;
    @Autowired RequestLoanUseCase requestLoan;
    @Autowired ApplyLegalHoldUseCase legalHold;
    @Autowired ConditionRecordsUseCase condition;
    @Autowired StoreRecordInContainerUseCase store;
    @Autowired PhysicalArchiveRecordRepository records;
    @Autowired PhysicalContainerRepository containers;

    private TenantId tenant;
    private int boxes;
    private int cotes;

    @BeforeEach
    void setUp() {
        tenant = TenantId.of("cond-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?)", tenant.value(), "IT " + tenant.value());
        RoleContext.set(Set.of(UserRole.ARCHIVIST));
    }

    @AfterEach
    void tearDown() {
        RoleContext.clear();
    }

    private <T> T as(Callable<T> body) {
        return TenantContext.callWithUnchecked(tenant, body);
    }

    private PhysicalContainer box(Integer capacity) {
        String label = "Boîte conditionnement n° " + (++boxes);
        return as(() -> createBox.execute(label, capacity, null));
    }

    /** Un dossier enregistré il y a {@code minutesAgo} minutes, dans aucune boîte. */
    private String registered(int minutesAgo) {
        String cote = "COND/%03d".formatted(++cotes);
        as(() -> records.save(PhysicalArchiveRecord.register(tenant, Cote.of(cote),
            Instant.now().minus(Duration.ofMinutes(minutesAgo)))));
        return cote;
    }

    private PhysicalArchiveRecord record(String cote) {
        return as(() -> records.findByCote(tenant, Cote.of(cote))).orElseThrow();
    }

    private List<String> pendingCotes() {
        return as(() -> condition.pending(PageRequest.of(0, 50))).content().stream()
            .map(r -> r.cote().value()).toList();
    }

    @Test
    @DisplayName("À conditionner : sans boîte, enregistrés ou gelés, les plus anciens d'abord ; une levée de gel y laisse le dossier")
    void pendingList() {
        String recent = registered(1);
        String old = registered(30);
        String frozen = registered(10);
        String boxed = registered(20);
        PhysicalContainer b = box(null);
        as(() -> store.execute(Cote.of(boxed), b.barcode(), false, "it"));
        as(() -> legalHold.execute(tenant, Cote.of(frozen), ApplyLegalHoldUseCase.Action.FREEZE));

        assertThat(pendingCotes()).containsExactly(old, frozen, recent);

        as(() -> legalHold.execute(tenant, Cote.of(frozen), ApplyLegalHoldUseCase.Action.RELEASE));
        assertThat(record(frozen).status()).isEqualTo(PhysicalArchiveStatus.REGISTERED);
        assertThat(pendingCotes()).containsExactly(old, frozen, recent);
    }

    @Test
    @DisplayName("Mise en boîte groupée dans une boîte en zone tampon : tout passe, une trace par dossier")
    void conditionIntoBufferBox() {
        String a = registered(3);
        String b = registered(2);
        PhysicalContainer buffer = box(10);
        assertThat(buffer.positionId()).isEmpty();

        ConditionRecordsUseCase.Result r = as(() -> condition.execute(buffer.barcode(), List.of(a, b, a), "awa.diop"));

        assertThat(r.recordCount()).isEqualTo(2);
        assertThat(r.stored()).hasSize(2);
        assertThat(r.container().state()).isEqualTo(ContainerState.OPEN);
        assertThat(record(a).status()).isEqualTo(PhysicalArchiveStatus.STORED);
        assertThat(record(a).containerId()).contains(buffer.id());
        assertThat(pendingCotes()).doesNotContain(a, b);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_event WHERE action = 'RECORD_STORED' AND tenant_id = ? AND actor = 'awa.diop'",
            Long.class, tenant.value())).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM physical_movement WHERE tenant_id = ? AND motive LIKE '%path=zone-tampon%'",
            Long.class, tenant.value())).isEqualTo(2L);

        // Puis on la place : elle quitte la zone tampon avec ses dossiers.
        StorageLocation position = as(() -> {
            StorageLocation site = createLocation.createSite("DKR", "Dakar");
            StorageLocation room = createLocation.createChild(site.id(), StorageLocationKind.STOREHOUSE, "SALLE", null);
            StorageLocation shelf = createLocation.createChild(room.id(), StorageLocationKind.SHELF, "T01", null);
            setLength.execute(shelf.id(), 1000, "it");
            StorageLocation rack = createLocation.createChild(shelf.id(), StorageLocationKind.RACK, "1", null);
            return createLocation.createChild(rack.id(), StorageLocationKind.POSITION, "01", null);
        });
        as(() -> place.execute(buffer.id(), position.id(), "it"));
        assertThat(as(() -> scan.resolve(buffer.barcode())).container().address()).contains("Emplacement 01");
        assertThat(record(a).status()).isEqualTo(PhysicalArchiveStatus.STORED);
    }

    @Test
    @DisplayName("Tout ou rien : un seul refus et rien n'est écrit ; chaque refus a sa raison")
    void allOrNothing() {
        String ok = registered(5);
        String frozen = registered(4);
        String elsewhere = registered(3);
        PhysicalContainer other = box(null);
        as(() -> store.execute(Cote.of(elsewhere), other.barcode(), false, "it"));
        as(() -> legalHold.execute(tenant, Cote.of(frozen), ApplyLegalHoldUseCase.Action.FREEZE));
        PhysicalContainer target = box(null);

        assertThatThrownBy(() -> as(() -> condition.execute(target.barcode(),
                List.of(ok, frozen, elsewhere, "COND/INCONNUE"), "it")))
            .isInstanceOfSatisfying(ConditioningRefusedException.class, e -> assertThat(e.refusals())
                .extracting(ConditioningRefusedException.Refusal::cote, ConditioningRefusedException.Refusal::reason)
                .containsExactlyInAnyOrder(
                    org.assertj.core.groups.Tuple.tuple(frozen, "legal-hold"),
                    org.assertj.core.groups.Tuple.tuple(elsewhere, "record-in-other-container"),
                    org.assertj.core.groups.Tuple.tuple("COND/INCONNUE", "record-not-found")));

        assertThat(record(ok).containerId()).isEmpty();
        assertThat(as(() -> records.countByContainer(tenant, target.id()))).isZero();
        assertThat(as(() -> containers.findById(tenant, target.id())).orElseThrow().state())
            .isEqualTo(ContainerState.EMPTY);
    }

    @Test
    @DisplayName("Capacité (R4) : une boîte pleine refuse, en groupe comme à l'unité, et la recherche donne la jauge")
    void capacity() {
        PhysicalContainer small = box(2);
        String first = registered(9);
        as(() -> condition.execute(small.barcode(), List.of(first), "it"));

        String second = registered(8);
        String third = registered(7);
        assertThatThrownBy(() -> as(() -> condition.execute(small.barcode(), List.of(second, third), "it")))
            .isInstanceOfSatisfying(ConditioningRefusedException.class, e -> {
                assertThat(e.refusals()).singleElement().satisfies(f -> {
                    assertThat(f.cote()).isNull();
                    assertThat(f.reason()).isEqualTo("container-full");
                    assertThat(f.message()).contains("1 dossiers sur 2").contains("il reste 1 place pour 2 dossiers");
                });
            });

        as(() -> store.execute(Cote.of(second), small.barcode(), false, "it"));
        assertThatThrownBy(() -> as(() -> store.execute(Cote.of(third), small.barcode(), false, "it")))
            .isInstanceOfSatisfying(RecordContainerConflictException.class, e ->
                assertThat(e.reason()).isEqualTo(RecordContainerConflictException.Reason.CONTAINER_FULL))
            .hasMessageContaining("pleine : 2 dossiers sur 2");

        assertThat(as(() -> scan.search(small.barcode(), 5))).singleElement()
            .satisfies(h -> assertThat(h.recordCount()).isEqualTo(2));
    }

    @Test
    @DisplayName("Capacité sous deux mises en boîte simultanées : une seule passe")
    void capacityUnderConcurrency() throws Exception {
        PhysicalContainer one = box(1);
        String a = registered(2);
        String b = registered(1);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (String cote : List.of(a, b)) {
                results.add(pool.submit(() -> {
                    RoleContext.set(Set.of(UserRole.ARCHIVIST));
                    try {
                        start.await();
                        as(() -> store.execute(Cote.of(cote), one.barcode(), false, "it"));
                        return true;
                    } catch (RecordContainerConflictException e) {
                        return false;
                    } finally {
                        RoleContext.clear();
                    }
                }));
            }
            start.countDown();
            long passed = 0;
            for (Future<Boolean> f : results) {
                if (f.get(30, TimeUnit.SECONDS)) passed++;
            }
            assertThat(passed).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(as(() -> records.countByContainer(tenant, one.id()))).isEqualTo(1);
    }

    @Test
    @DisplayName("Une boîte en prêt ou fermée ne reçoit pas de dossier")
    void loanOrClosedBox() {
        PhysicalContainer onLoan = box(null);
        as(() -> requestLoan.execute(onLoan.id(), "Awa Diop", "DRH", "Audit",
            Instant.now().plus(Duration.ofDays(7)), "it"));
        String cote = registered(1);

        assertThatThrownBy(() -> as(() -> store.execute(Cote.of(cote), onLoan.barcode(), false, "it")))
            .isInstanceOfSatisfying(RecordContainerConflictException.class, e ->
                assertThat(e.reason()).isEqualTo(RecordContainerConflictException.Reason.CONTAINER_ON_LOAN));
        assertThatThrownBy(() -> as(() -> condition.execute(onLoan.barcode(), List.of(cote), "it")))
            .isInstanceOfSatisfying(ConditioningRefusedException.class, e ->
                assertThat(e.refusals()).extracting(ConditioningRefusedException.Refusal::reason)
                    .containsExactly("container-on-loan"));

        PhysicalContainer sealed = box(null);
        jdbc.update("UPDATE physical_container SET state = 'SEALED' WHERE id = ?", sealed.id());
        assertThatThrownBy(() -> as(() -> store.execute(Cote.of(cote), sealed.barcode(), false, "it")))
            .isInstanceOfSatisfying(RecordContainerConflictException.class, e ->
                assertThat(e.reason()).isEqualTo(RecordContainerConflictException.Reason.CONTAINER_CLOSED));
        assertThat(record(cote).containerId()).isEmpty();
    }
}
