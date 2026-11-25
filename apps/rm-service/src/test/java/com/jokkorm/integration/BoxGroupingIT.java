package com.jokkorm.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import com.jokkorm.adapter.out.master.noop.NoopSaeMasterAdapter;
import com.jokkorm.application.physical.CreatePhysicalContainerUseCase;
import com.jokkorm.application.record.ConditionRecordsUseCase;
import com.jokkorm.application.record.ConditioningRefusedException;
import com.jokkorm.application.record.ConditioningRefusedException.Refusal;
import com.jokkorm.application.record.RecordContainerConflictException;
import com.jokkorm.application.record.RecordContainerConflictException.Reason;
import com.jokkorm.application.record.StoreRecordInContainerUseCase;
import com.jokkorm.boot.Application;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MasterRecordSnapshot;
import com.jokkorm.domain.master.MasterRecordSnapshotRepository;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.physical.PhysicalContainer;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Règles R1 (un service producteur par boîte) et R2 (un sort final par boîte), bloquantes (étude du
 * 24 novembre 2026, D2), contre un vrai Postgres.
 *
 * <p>Espace {@code grpdemo} : maître de démonstration (service et sort final tirés de la cote).
 * Espace {@code grpmaitre} : maître de test dont on écrit les fiches et qu'on peut couper, pour les
 * cas que la démonstration ne sait pas produire (même service et autre sort final, fiche incomplète,
 * liaison coupée, fiche corrigée à l'instant). Espace aléatoire : sans maître, règles inactives.
 */
@SpringBootTest(classes = {Application.class, BoxGroupingIT.Config.class})
@Testcontainers
@ActiveProfiles("test")
class BoxGroupingIT {

    private static final TenantId DEMO = TenantId.of("grpdemo");
    private static final TenantId SCRIPTED = TenantId.of("grpmaitre");

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
        registry.add("jokkorm.master.tenants." + DEMO.value(), () -> "demo");
        registry.add("jokkorm.master.tenants." + SCRIPTED.value(), () -> ScriptedMaster.KIND);
        registry.add("jokkorm.master.snapshot.worker-enabled", () -> "false");
    }

    /** Maître de test : répond ce qu'on lui a écrit, ou ne répond plus. */
    static class ScriptedMaster extends NoopSaeMasterAdapter {
        static final String KIND = "scripted";
        static final Map<String, MasterArchiveView> FICHES = new ConcurrentHashMap<>();
        static volatile boolean down;
        static final AtomicInteger CALLS = new AtomicInteger();

        @Override
        public String kind() { return KIND; }

        @Override
        public String displayName() { return "Maître de test"; }

        @Override
        public Optional<MasterArchiveView> fetchByCote(TenantId tenant, Cote cote) {
            CALLS.incrementAndGet();
            if (down) throw new MasterUnreachableException("liaison coupée (test)");
            return Optional.ofNullable(FICHES.get(cote.value()));
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        ScriptedMaster scriptedMaster() {
            return new ScriptedMaster();
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CreatePhysicalContainerUseCase createBox;
    @Autowired ConditionRecordsUseCase condition;
    @Autowired StoreRecordInContainerUseCase store;
    @Autowired PhysicalArchiveRecordRepository records;
    @Autowired MasterRecordSnapshotRepository snapshots;

    @BeforeEach
    void setUp() {
        for (TenantId t : List.of(DEMO, SCRIPTED)) {
            jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
                t.value(), "IT " + t.value());
        }
        ScriptedMaster.down = false;
        RoleContext.set(Set.of(UserRole.ARCHIVIST));
    }

    @AfterEach
    void tearDown() {
        ScriptedMaster.down = false;
        RoleContext.clear();
    }

    private static <T> T in(TenantId tenant, Callable<T> body) {
        return TenantContext.callWithUnchecked(tenant, body);
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private PhysicalContainer box(TenantId tenant) {
        return in(tenant, () -> createBox.execute("Boîte R1R2 " + suffix(), null, null));
    }

    /** Dossier enregistré dans aucune boîte (sa fiche sera lue chez le maître à la mise en boîte). */
    private PhysicalArchiveRecord registered(TenantId tenant, String cote) {
        return in(tenant, () -> records.save(PhysicalArchiveRecord.register(tenant, Cote.of(cote), Instant.now())));
    }

    /** Dossier du maître de test, avec sa fiche. */
    private String fiche(String service, String fate, String fateSource) {
        String cote = "GRP/" + suffix();
        ScriptedMaster.FICHES.put(cote, new MasterArchiveView(Cote.of(cote), "Dossier " + cote, "test", 10, fate,
            false, null, service, LocalDate.of(2036, 12, 31), null, fateSource));
        registered(SCRIPTED, cote);
        return cote;
    }

    private void storeIn(TenantId tenant, String cote, PhysicalContainer box) {
        in(tenant, () -> store.execute(Cote.of(cote), box.barcode(), false, "it"));
    }

    private Optional<UUID> containerOf(TenantId tenant, String cote) {
        return in(tenant, () -> records.findByCote(tenant, Cote.of(cote))).orElseThrow().containerId();
    }

    @Test
    @DisplayName("R1 avec le maître de démonstration : un autre service est refusé, le même passe")
    void serviceRuleWithDemoMaster() {
        String dmg = "DMG/2016/" + suffix();
        String fin = "FIN/2016/" + suffix();   // même sort final (versement), autre service
        String dmg2 = "DMG/2017/" + suffix();
        List.of(dmg, fin, dmg2).forEach(c -> registered(DEMO, c));
        PhysicalContainer b = box(DEMO);
        storeIn(DEMO, dmg, b);

        assertThatThrownBy(() -> storeIn(DEMO, fin, b))
            .isInstanceOfSatisfying(RecordContainerConflictException.class, e -> {
                assertThat(e.reason()).isEqualTo(Reason.SERVICE_MISMATCH);
                assertThat(e.currentBarcode()).isEqualTo(b.barcode());
            })
            .hasMessageContaining("relève du service FIN")
            .hasMessageContaining("dossiers du service DMG");
        assertThat(containerOf(DEMO, fin)).isEmpty();

        storeIn(DEMO, dmg2, b);
        assertThat(containerOf(DEMO, dmg2)).contains(b.id());
    }

    @Test
    @DisplayName("Mise en boîte groupée dans une boîte vide : une sélection qui mêle services et sorts finaux est refusée, comptes à l'appui")
    void mixedSelectionIsRefused() {
        String rh1 = "RH/2023/" + suffix();
        String rh2 = "RH/2023/" + suffix();
        String dmg = "DMG/2016/" + suffix();
        List.of(rh1, rh2, dmg).forEach(c -> registered(DEMO, c));
        PhysicalContainer b = box(DEMO);

        assertThatThrownBy(() -> in(DEMO, () -> condition.execute(b.barcode(), List.of(rh1, rh2, dmg), "it")))
            .isInstanceOfSatisfying(ConditioningRefusedException.class, e -> {
                assertThat(e.refusals()).extracting(Refusal::cote, Refusal::reason)
                    .containsExactly(tuple(null, "selection-mixed"), tuple(null, "selection-mixed"));
                assertThat(e.refusals().get(0).message()).contains("2 services").contains("RH : 2").contains("DMG : 1");
                assertThat(e.refusals().get(1).message()).contains("« destruction » : 2").contains("« versement » : 1");
            });
        assertThat(containerOf(DEMO, rh1)).isEmpty();

        ConditionRecordsUseCase.Result ok = in(DEMO, () -> condition.execute(b.barcode(), List.of(rh1, rh2), "it"));
        assertThat(ok.recordCount()).isEqualTo(2);
        // La boîte a désormais un service : un dossier d'un autre service est refusé nommément.
        assertThatThrownBy(() -> in(DEMO, () -> condition.execute(b.barcode(), List.of(dmg), "it")))
            .isInstanceOfSatisfying(ConditioningRefusedException.class, e ->
                assertThat(e.refusals()).extracting(Refusal::cote, Refusal::reason)
                    .containsExactly(tuple(dmg, "box-service-mismatch")));
    }

    @Test
    @DisplayName("R2 seule : même service, autre sort final, refusé ; le message nomme les deux sorts")
    void fateRuleAlone() {
        String destroy = fiche("DRH", "DESTRUCTION", "destruction");
        String keep = fiche("drh ", "CONSERVATION", "preservation");   // même service, écrit autrement
        String destroy2 = fiche("Drh", "DESTRUCTION", "destruction");
        PhysicalContainer b = box(SCRIPTED);
        storeIn(SCRIPTED, destroy, b);

        assertThatThrownBy(() -> storeIn(SCRIPTED, keep, b))
            .isInstanceOfSatisfying(RecordContainerConflictException.class, e ->
                assertThat(e.reason()).isEqualTo(Reason.FATE_MISMATCH))
            .hasMessageContaining("« conservation »")
            .hasMessageContaining("« destruction »");

        storeIn(SCRIPTED, destroy2, b);   // casse et espaces ignorés pour le service
        assertThat(containerOf(SCRIPTED, destroy2)).contains(b.id());
    }

    @Test
    @DisplayName("Fiche incomplète ou illisible : refus qui dit quoi compléter, et nomme une valeur non reconnue")
    void incompleteMasterRecord() {
        String noService = fiche(null, "DESTRUCTION", "destruction");
        String strange = fiche("DRH", null, "tri-sélectif");
        String nothing = fiche(" ", null, null);
        String unknown = "GRP/INCONNU-" + suffix();
        registered(SCRIPTED, unknown);   // le maître ne la connaît pas
        PhysicalContainer b = box(SCRIPTED);

        assertThatThrownBy(() -> in(SCRIPTED, () -> condition.execute(b.barcode(),
                List.of(noService, strange, nothing, unknown), "it")))
            .isInstanceOfSatisfying(ConditioningRefusedException.class, e -> {
                assertThat(e.refusals()).extracting(Refusal::cote, Refusal::reason).containsExactlyInAnyOrder(
                    tuple(noService, "master-facts-missing"),
                    tuple(strange, "master-facts-missing"),
                    tuple(nothing, "master-facts-missing"),
                    tuple(unknown, "master-facts-missing"));
                assertThat(message(e, noService)).contains("n'indique pas de service producteur");
                assertThat(message(e, strange)).contains("« tri-sélectif »").contains("n'est pas reconnu");
                assertThat(message(e, nothing)).contains("ni service producteur ni sort final");
                assertThat(message(e, unknown)).contains("inconnu de la GED maître");
            });
        assertThat(in(SCRIPTED, () -> records.countByContainer(SCRIPTED, b.id()))).isZero();
    }

    @Test
    @DisplayName("Liaison coupée : avec la copie locale on range quand même ; sans copie, refus « réessayez »")
    void masterDown() {
        String seen = fiche("DAF", "DESTRUCTION", "destruction");
        String seen2 = fiche("DAF", "DESTRUCTION", "destruction");
        String neverRead = fiche("DAF", "DESTRUCTION", "destruction");
        PhysicalContainer b = box(SCRIPTED);
        storeIn(SCRIPTED, seen, b);   // copies prises pendant que le maître répond
        UUID seen2Id = in(SCRIPTED, () -> records.findByCote(SCRIPTED, Cote.of(seen2))).orElseThrow().id();
        snapshots.save(MasterRecordSnapshot.of(SCRIPTED, seen2Id,
            ScriptedMaster.KIND, ScriptedMaster.FICHES.get(seen2), Instant.now()));

        ScriptedMaster.down = true;
        storeIn(SCRIPTED, seen2, b);
        assertThat(containerOf(SCRIPTED, seen2)).contains(b.id());

        assertThatThrownBy(() -> storeIn(SCRIPTED, neverRead, b))
            .isInstanceOfSatisfying(RecordContainerConflictException.class, e ->
                assertThat(e.reason()).isEqualTo(Reason.MASTER_FACTS_UNREACHABLE))
            .hasMessageContaining("Réessayez quand la liaison sera rétablie");

        // Plusieurs dossiers sans copie, liaison coupée : un seul appel au maître, pas un délai d'attente
        // par dossier sous le verrou de la boîte.
        String a = fiche("DAF", "DESTRUCTION", "destruction");
        String c = fiche("DAF", "DESTRUCTION", "destruction");
        PhysicalContainer empty = box(SCRIPTED);
        ScriptedMaster.CALLS.set(0);
        assertThatThrownBy(() -> in(SCRIPTED, () -> condition.execute(empty.barcode(), List.of(neverRead, a, c), "it")))
            .isInstanceOfSatisfying(ConditioningRefusedException.class, e ->
                assertThat(e.refusals()).extracting(Refusal::reason).containsOnly("master-facts-unreachable").hasSize(3));
        assertThat(ScriptedMaster.CALLS.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fiche corrigée à l'instant dans le maître : relue avant de refuser, le dossier entre")
    void correctedInMasterIsReread() {
        String drh = fiche("DRH", "DESTRUCTION", "destruction");
        String moved = fiche("DAF", "DESTRUCTION", "destruction");
        PhysicalContainer b = box(SCRIPTED);
        storeIn(SCRIPTED, drh, b);
        UUID movedId = in(SCRIPTED, () -> records.findByCote(SCRIPTED, Cote.of(moved))).orElseThrow().id();
        // Copie d'hier : service DAF.
        snapshots.save(MasterRecordSnapshot.of(SCRIPTED, movedId,
            ScriptedMaster.KIND, ScriptedMaster.FICHES.get(moved), Instant.now().minusSeconds(86_000)));
        // L'archiviste corrige la fiche dans le maître : le dossier est de la DRH.
        ScriptedMaster.FICHES.put(moved, new MasterArchiveView(Cote.of(moved), "Dossier", "test", 10, "DESTRUCTION",
            false, null, "DRH", LocalDate.of(2036, 12, 31), null, "destruction"));

        storeIn(SCRIPTED, moved, b);

        assertThat(containerOf(SCRIPTED, moved)).contains(b.id());
        assertThat(snapshots.find(SCRIPTED, movedId).orElseThrow().serviceCode()).isEqualTo("DRH");
    }

    @Test
    @DisplayName("Transfert : la boîte d'arrivée est contrôlée ; une boîte déjà mélangée ne reçoit plus rien mais se vide")
    void transferAndMixedBox() {
        String drh = fiche("DRH", "DESTRUCTION", "destruction");
        String daf = fiche("DAF", "DESTRUCTION", "destruction");
        String drh2 = fiche("DRH", "DESTRUCTION", "destruction");
        // Boîte mélangée d'avant les règles (reprise de l'existant) : écrite directement.
        PhysicalContainer mixed = box(SCRIPTED);
        for (String c : List.of(drh, daf)) {
            in(SCRIPTED, () -> {
                PhysicalArchiveRecord r = records.findByCote(SCRIPTED, Cote.of(c)).orElseThrow();
                r.storeInContainer(mixed.id(), Instant.now());
                return records.save(r);
            });
        }

        assertThatThrownBy(() -> storeIn(SCRIPTED, drh2, mixed))
            .isInstanceOfSatisfying(RecordContainerConflictException.class, e ->
                assertThat(e.reason()).isEqualTo(Reason.BOX_MIXED))
            .hasMessageContaining("mélange déjà les services").hasMessageContaining("DRH").hasMessageContaining("DAF");

        // Reconditionner : sortir le dossier DAF vers une boîte DAF (vide) est permis.
        PhysicalContainer dafBox = box(SCRIPTED);
        in(SCRIPTED, () -> store.execute(Cote.of(daf), dafBox.barcode(), true, "it"));
        assertThat(containerOf(SCRIPTED, daf)).contains(dafBox.id());
        storeIn(SCRIPTED, drh2, mixed);   // la boîte n'a plus que la DRH
        assertThat(containerOf(SCRIPTED, drh2)).contains(mixed.id());

        // Ranger un dossier DRH (déjà en boîte) dans la boîte DAF : R1 refuse d'abord, l'écran ne
        // propose donc pas un transfert voué à l'échec.
        assertThatThrownBy(() -> in(SCRIPTED, () -> store.execute(Cote.of(drh), dafBox.barcode(), false, "it")))
            .isInstanceOfSatisfying(RecordContainerConflictException.class, e ->
                assertThat(e.reason()).isEqualTo(Reason.SERVICE_MISMATCH));
        // Transférer un dossier DRH vers la boîte DAF : refusé.
        assertThatThrownBy(() -> in(SCRIPTED, () -> store.execute(Cote.of(drh), dafBox.barcode(), true, "it")))
            .isInstanceOfSatisfying(RecordContainerConflictException.class, e ->
                assertThat(e.reason()).isEqualTo(Reason.SERVICE_MISMATCH));
        assertThat(containerOf(SCRIPTED, drh)).contains(mixed.id());
    }

    @Test
    @DisplayName("Espace sans maître : ni service ni sort final connus, aucune règle de regroupement")
    void standaloneTenantIsNotChecked() {
        TenantId alone = TenantId.of("grp-" + UUID.randomUUID().toString().substring(0, 8));
        jdbc.update("INSERT INTO tenant (id, display_name) VALUES (?, ?)", alone.value(), "IT seul");
        registered(alone, "RH/2023/A");
        registered(alone, "DMG/2016/B");
        PhysicalContainer b = box(alone);

        assertThat(in(alone, () -> condition.execute(b.barcode(), List.of("RH/2023/A", "DMG/2016/B"), "it"))
            .recordCount()).isEqualTo(2);
    }

    private static String message(ConditioningRefusedException e, String cote) {
        return e.refusals().stream().filter(r -> cote.equals(r.cote())).findFirst().orElseThrow().message();
    }
}
