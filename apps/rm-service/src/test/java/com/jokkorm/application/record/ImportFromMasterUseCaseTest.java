package com.jokkorm.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jokkorm.commons.role.ForbiddenRoleException;
import com.jokkorm.commons.role.RoleContext;
import com.jokkorm.commons.role.UserRole;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.MasterUnreachableException;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ImportFromMasterUseCaseTest {

    private static final TenantId ACME = TenantId.of("acme");
    private static final Cote COTE = Cote.of("FR094080_2026_AE0002");

    private final SaeMasterPort master = mock(SaeMasterPort.class);
    private final PhysicalArchiveRecordRepository records = mock(PhysicalArchiveRecordRepository.class);
    private final HandleCoteAssignedEventUseCase register = mock(HandleCoteAssignedEventUseCase.class);
    private final ImportFromMasterUseCase useCase = new ImportFromMasterUseCase(master, records, register);

    @AfterEach
    void clear() {
        RoleContext.clear();
    }

    private ImportFromMasterUseCase.Result run(UserRole role) {
        RoleContext.set(Set.of(role));
        return TenantContext.callWithUnchecked(ACME, () -> useCase.execute(COTE, "ccharles"));
    }

    @Test
    @DisplayName("cote connue du maître : enveloppe physique créée au nom de l'archiviste")
    void imports_known_cote() {
        var view = new MasterArchiveView(COTE, "CONNU", "DIP/DOSIP", 90, "DESTRUCTION", false);
        when(master.fetchByCote(ACME, COTE)).thenReturn(Optional.of(view));
        when(records.findByCote(ACME, COTE)).thenReturn(Optional.empty());
        var record = PhysicalArchiveRecord.register(ACME, COTE, Instant.parse("2026-11-23T10:00:00Z"));
        when(register.execute(ACME, COTE, "ccharles")).thenReturn(record);

        var result = run(UserRole.ARCHIVIST);

        assertThat(result.created()).isTrue();
        assertThat(result.master().title()).isEqualTo("CONNU");
        verify(register).execute(ACME, COTE, "ccharles");
    }

    @Test
    @DisplayName("cote inconnue du maître : 404, rien n'est créé")
    void unknown_cote_creates_nothing() {
        when(master.fetchByCote(ACME, COTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> run(UserRole.ARCHIVIST)).isInstanceOf(RecordNotFoundException.class);
        verify(register, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("maître en panne : l'erreur remonte, rien n'est créé")
    void unreachable_master_creates_nothing() {
        when(master.fetchByCote(ACME, COTE)).thenThrow(new MasterUnreachableException("HTTP 401"));

        assertThatThrownBy(() -> run(UserRole.ARCHIVIST)).isInstanceOf(MasterUnreachableException.class);
        verify(register, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("demandeur : import refusé")
    void requester_cannot_import() {
        assertThatThrownBy(() -> run(UserRole.REQUESTER)).isInstanceOf(ForbiddenRoleException.class);
        verify(master, never()).fetchByCote(any(), any());
    }
}
