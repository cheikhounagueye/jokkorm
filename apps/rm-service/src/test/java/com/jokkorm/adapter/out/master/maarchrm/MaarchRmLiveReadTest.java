package com.jokkorm.adapter.out.master.maarchrm;

import static org.assertj.core.api.Assertions.assertThat;

import com.jokkorm.application.master.MasterWatchService;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveRef;
import com.jokkorm.domain.master.MasterEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Vérification en LECTURE SEULE contre un vrai Maarch RM (jamais en CI) :
 * {@code MAARCHRM_LIVE_URL}, {@code MAARCHRM_LIVE_LOGIN}, {@code MAARCHRM_LIVE_PASSWORD}, et en option
 * {@code MAARCHRM_LIVE_COTE} + {@code MAARCHRM_LIVE_EXPECTED} (dernier état envoyé par JokkoRM).
 * N'appelle que les lectures de l'adapter, jamais le service (qui peut écrire). N'affiche que
 * type, identifiant, date et auteur des événements.
 */
@EnabledIfEnvironmentVariable(named = "MAARCHRM_LIVE_URL", matches = "https?://.+")
class MaarchRmLiveReadTest {

    private static final TenantId TENANT = TenantId.of("live");

    private MaarchRmSaeMasterAdapter adapter(int page) {
        var a = new MaarchRmSaeMasterAdapter(new MaarchRmProperties(System.getenv("MAARCHRM_LIVE_URL"), "service", "basic",
            System.getenv("MAARCHRM_LIVE_LOGIN"), System.getenv("MAARCHRM_LIVE_PASSWORD"), null, 10_000), Clock.systemUTC());
        a.journalPage = page;
        return a;
    }

    @Test
    @DisplayName("Maarch réel : la pagination (pages de 2) retrouve exactement les événements d'une seule grande page")
    void pagination_matches_single_page_on_real_maarch() {
        Instant from = Instant.now().minus(Duration.ofDays(30));
        var kinds = EnumSet.allOf(MasterEvent.Kind.class);

        List<MasterEvent> big = adapter(500).readEvents(TENANT, from, kinds);
        List<MasterEvent> paged = adapter(2).readEvents(TENANT, from, kinds);

        big.forEach(e -> System.out.printf("[live] %s %s %s par %s%n", e.occurredAt(), e.kind(), e.id(), e.actor()));
        assertThat(big).isNotEmpty();
        assertThat(paged).extracting(MasterEvent::id).containsExactlyElementsOf(big.stream().map(MasterEvent::id).toList());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "MAARCHRM_LIVE_EXPECTED", matches = ".+")
    @DisplayName("Maarch réel : l'emplacement affiché est bien celui envoyé par JokkoRM → aucun faux écart")
    void no_false_discrepancy_on_real_maarch() {
        String cote = System.getenv("MAARCHRM_LIVE_COTE");
        String expected = System.getenv("MAARCHRM_LIVE_EXPECTED");
        var a = adapter(500);
        List<MasterEvent> mods = a.readEvents(TENANT, Instant.now().minus(Duration.ofDays(30)),
            EnumSet.of(MasterEvent.Kind.METADATA_MODIFIED));
        int checked = 0;
        int discrepancies = 0;
        for (MasterEvent e : mods) {
            Optional<MasterArchiveRef> ref = a.describe(TENANT, e.masterArchiveId());
            if (ref.isEmpty() || !ref.get().cote().equals(cote)) continue;
            checked++;
            if (!MasterWatchService.same(ref.get().displayedPhysicalStatus(), expected)) discrepancies++;
        }
        System.out.printf("[live] %s : %d modification(s) vérifiée(s), %d écart(s)%n", cote, checked, discrepancies);
        assertThat(checked).isPositive();
        assertThat(discrepancies).isZero();
    }
}
