package com.jokkorm.application.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.jokkorm.application.record.GetConsolidatedRecordUseCase.MasterStatus;
import com.jokkorm.application.record.GetConsolidatedRecordUseCase.Result;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.master.SaeMasterPort;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveRecordRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetConsolidatedRecordUseCaseTest {

    private static final TenantId TENANT = TenantId.of("acme");
    private static final Cote COTE = Cote.of("DMG/MP/2024/047");
    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    @Mock PhysicalArchiveRecordRepository repository;
    @Mock SaeMasterPort master;

    private GetConsolidatedRecordUseCase useCase;

    @BeforeEach
    void setup() {
        useCase = new GetConsolidatedRecordUseCase(repository, master);
    }

    @Test
    @DisplayName("maître disponible → status AVAILABLE, champs fusionnés")
    void master_available_merges_fields() {
        PhysicalArchiveRecord local = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        when(repository.findByCote(TENANT, COTE)).thenReturn(Optional.of(local));
        when(master.fetchByCote(TENANT, COTE)).thenReturn(Optional.of(
            new MasterArchiveView(COTE, "Marché public n° 2024-047",
                "finances/marches/2024", 10, "VERSEMENT_AN", false)));

        Result result = useCase.execute(TENANT, COTE);

        assertThat(result.local()).isEqualTo(local);
        assertThat(result.masterStatus()).isEqualTo(MasterStatus.AVAILABLE);
        assertThat(result.master().title()).isEqualTo("Marché public n° 2024-047");
        assertThat(result.master().duaYears()).isEqualTo(10);
    }

    @Test
    @DisplayName("maître répond empty → status UNAVAILABLE, master null")
    void master_empty_returns_unavailable() {
        PhysicalArchiveRecord local = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        when(repository.findByCote(TENANT, COTE)).thenReturn(Optional.of(local));
        when(master.fetchByCote(TENANT, COTE)).thenReturn(Optional.empty());

        Result result = useCase.execute(TENANT, COTE);

        assertThat(result.masterStatus()).isEqualTo(MasterStatus.UNAVAILABLE);
        assertThat(result.master()).isNull();
        assertThat(result.local()).isEqualTo(local);
    }

    @Test
    @DisplayName("maître jette RuntimeException → status UNREACHABLE, vue locale intacte")
    void master_throws_returns_unreachable() {
        PhysicalArchiveRecord local = PhysicalArchiveRecord.register(TENANT, COTE, NOW);
        when(repository.findByCote(TENANT, COTE)).thenReturn(Optional.of(local));
        when(master.fetchByCote(TENANT, COTE)).thenThrow(new RuntimeException("connect timeout"));

        Result result = useCase.execute(TENANT, COTE);

        assertThat(result.masterStatus()).isEqualTo(MasterStatus.UNREACHABLE);
        assertThat(result.master()).isNull();
        assertThat(result.local()).isEqualTo(local);
    }

    @Test
    @DisplayName("record local absent → RecordNotFoundException (maître pas appelé)")
    void local_missing_throws_not_found() {
        when(repository.findByCote(TENANT, COTE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(TENANT, COTE))
            .isInstanceOf(RecordNotFoundException.class)
            .hasMessageContaining("DMG/MP/2024/047");
        // On ne devrait pas appeler le maître si on sait déjà que la cote n'existe pas chez nous.
        org.mockito.Mockito.verifyNoInteractions(master);
    }
}
