package com.jokkorm.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegalHoldGuardTest {

    private static final TenantId ACME = TenantId.of("acme");
    private static final UUID BOX = UUID.randomUUID();
    private static final Instant T0 = Instant.parse("2026-11-23T10:00:00Z");

    private final PhysicalArchiveRecordRepository records = mock(PhysicalArchiveRecordRepository.class);
    private final SaeMasterPort master = mock(SaeMasterPort.class);
    private final LegalHoldGuard guard = new LegalHoldGuard(records, master);

    private static PhysicalArchiveRecord stored(String cote) {
        var r = PhysicalArchiveRecord.register(ACME, Cote.of(cote), T0);
        r.storeInContainer(BOX, T0);
        return r;
    }

    private void masterSays(String cote, boolean legalHold) {
        when(master.fetchByCote(ACME, Cote.of(cote))).thenReturn(Optional.of(
            new MasterArchiveView(Cote.of(cote), "Dossier", "DIP/DOSIP", 90, "DESTRUCTION", legalHold)));
    }

    @Test
    @DisplayName("aucun gel ni local ni maître : la boîte peut sortir")
    void no_hold() {
        when(records.findByContainer(ACME, BOX)).thenReturn(List.of(stored("A/1"), stored("A/2")));
        masterSays("A/1", false);
        masterSays("A/2", false);

        var verdict = guard.check(ACME, BOX);

        assertThat(verdict.blocked()).isFalse();
        assertThat(verdict.records()).isEqualTo(2);
        assertThatCode(() -> guard.requireNoHold(ACME, BOX, "BOX-1")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("gel posé dans la GED maître sur une seule archive : toute la boîte est retenue")
    void master_hold_blocks() {
        when(records.findByContainer(ACME, BOX)).thenReturn(List.of(stored("A/1"), stored("A/2")));
        masterSays("A/1", false);
        masterSays("A/2", true);

        assertThat(guard.check(ACME, BOX).blockers()).containsExactly(
            "Gel contentieux posé dans la GED maître sur la cote A/2");
        assertThatThrownBy(() -> guard.requireNoHold(ACME, BOX, "BOX-1"))
            .isInstanceOf(LegalHoldException.class)
            .hasMessageContaining("BOX-1").hasMessageContaining("A/2");
    }

    @Test
    @DisplayName("gel local FROZEN : retenue même si le maître ne connaît pas la cote")
    void local_hold_blocks() {
        var frozen = stored("A/1");
        frozen.markFrozen(T0);
        when(records.findByContainer(ACME, BOX)).thenReturn(List.of(frozen));
        when(master.fetchByCote(any(), any())).thenReturn(Optional.empty());

        assertThat(guard.check(ACME, BOX).blockers()).containsExactly(
            "Gel contentieux posé dans JokkoRM sur la cote A/1");
    }

    @Test
    @DisplayName("maître injoignable : gel invérifiable, sortie refusée (fermé en cas de doute)")
    void unreachable_master_blocks() {
        when(records.findByContainer(ACME, BOX)).thenReturn(List.of(stored("A/1")));
        when(master.fetchByCote(any(), any())).thenThrow(new MasterUnreachableException("HTTP 503"));

        var verdict = guard.check(ACME, BOX);

        assertThat(verdict.blocked()).isTrue();
        assertThat(verdict.blockers().get(0)).contains("Gel non vérifiable").contains("A/1");
    }

    @Test
    @DisplayName("cote inconnue du maître et pas de gel local : sortie possible")
    void unknown_in_master_passes() {
        when(records.findByContainer(ACME, BOX)).thenReturn(List.of(stored("A/1")));
        when(master.fetchByCote(any(), any())).thenReturn(Optional.empty());

        assertThat(guard.check(ACME, BOX).blocked()).isFalse();
    }

    @Test
    @DisplayName("boîte vide : rien à vérifier, aucun appel au maître")
    void empty_box() {
        when(records.findByContainer(ACME, BOX)).thenReturn(List.of());

        assertThat(guard.check(ACME, BOX)).isEqualTo(new LegalHoldGuard.Verdict(0, List.of()));
    }
}
