package com.jokkorm.application.master;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MasterRecordSnapshot;
import com.jokkorm.domain.master.MasterRecordSnapshotRepository;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D3 : la copie de rangement ne s'écrit qu'à partir d'une réponse du maître. */
class MasterSnapshotServiceTest {

    private static final TenantId T = TenantId.of("acme");
    private static final Instant NOW = Instant.parse("2026-11-25T18:00:00Z");

    private final SaeMasterPort master = mock(SaeMasterPort.class);
    private final MasterRecordSnapshotRepository repo = mock(MasterRecordSnapshotRepository.class);
    private final MasterSnapshotService service =
        new MasterSnapshotService(master, repo, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofDays(1));

    private static PhysicalArchiveRecord record(String cote) {
        return PhysicalArchiveRecord.register(T, Cote.of(cote), NOW);
    }

    private static MasterArchiveView view(String cote) {
        return new MasterArchiveView(Cote.of(cote), "Titre", "dip/dosip", 90, "DESTRUCTION", false, null,
            "DIP", LocalDate.parse("2116-11-24"), null, "destruction");
    }

    @Test
    @DisplayName("Sans copie : lue chez le maître, enregistrée, et rendue connue")
    void lazyFetch() {
        PhysicalArchiveRecord r = record("FR094080_2026_AE0004");
        when(repo.findAll(eq(T), anyCollection())).thenReturn(Map.of());
        when(master.masterKind(T)).thenReturn("maarchrm");
        when(master.fetchByCote(T, r.cote())).thenReturn(Optional.of(view(r.cote().value())));

        var facts = service.factsFor(T, List.of(r));

        assertThat(facts.get(r.id())).isInstanceOf(MasterSnapshotService.Facts.Known.class);
        var s = ((MasterSnapshotService.Facts.Known) facts.get(r.id())).snapshot();
        assertThat(s.serviceCode()).isEqualTo("DIP");
        assertThat(s.finalFate()).isEqualTo("DESTRUCTION");
        assertThat(s.disposalDate()).isEqualTo(LocalDate.parse("2116-11-24"));
        assertThat(s.masterKind()).isEqualTo("maarchrm");
        assertThat(s.fetchedAt()).isEqualTo(NOW);
        verify(repo).save(any());
    }

    @Test
    @DisplayName("Copie existante : servie sans appeler le maître")
    void cached() {
        PhysicalArchiveRecord r = record("FR094080_2026_AE0005");
        MasterRecordSnapshot s = new MasterRecordSnapshot(T, r.id(), "maarchrm", "DIP", "DESTRUCTION", "destruction",
            null, null, false, NOW.minusSeconds(60));
        when(repo.findAll(eq(T), anyCollection())).thenReturn(Map.of(r.id(), s));

        assertThat(service.factsFor(T, List.of(r)).get(r.id()))
            .isEqualTo(new MasterSnapshotService.Facts.Known(s));
        verify(master, never()).fetchByCote(any(), any());
    }

    @Test
    @DisplayName("Maître injoignable et pas de copie : « injoignable », rien d'écrit (l'appelant refusera)")
    void unreachable() {
        PhysicalArchiveRecord r = record("FR094080_2026_AE0006");
        when(repo.findAll(eq(T), anyCollection())).thenReturn(Map.of());
        when(master.fetchByCote(T, r.cote())).thenThrow(new MasterUnreachableException("délai dépassé"));

        assertThat(service.factsFor(T, List.of(r)).get(r.id()))
            .isInstanceOf(MasterSnapshotService.Facts.Unreachable.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("Cote inconnue du maître : « inconnue », rien d'écrit")
    void notFound() {
        PhysicalArchiveRecord r = record("FR094080_2026_AE0007");
        when(repo.findAll(eq(T), anyCollection())).thenReturn(Map.of());
        when(master.fetchByCote(T, r.cote())).thenReturn(Optional.empty());

        assertThat(service.factsFor(T, List.of(r)).get(r.id()))
            .isInstanceOf(MasterSnapshotService.Facts.NotFound.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("Relecture périodique : rien sans maître ; s'arrête au premier maître injoignable")
    void refreshStale() {
        when(master.isStandalone(T)).thenReturn(true);
        assertThat(service.refreshStale(T, 50)).isZero();
        verify(repo, never()).findStale(any(), any(), eq(50));

        when(master.isStandalone(T)).thenReturn(false);
        var a = new MasterRecordSnapshotRepository.Stale(UUID.randomUUID(), Cote.of("FR094080_2026_AE0004"));
        var b = new MasterRecordSnapshotRepository.Stale(UUID.randomUUID(), Cote.of("FR094080_2026_AE0005"));
        when(repo.findStale(T, NOW.minus(Duration.ofDays(1)), 50)).thenReturn(List.of(a, b));
        when(master.fetchByCote(T, a.cote())).thenThrow(new MasterUnreachableException("panne"));

        assertThat(service.refreshStale(T, 50)).isZero();
        verify(master, never()).fetchByCote(T, b.cote());
    }
}
