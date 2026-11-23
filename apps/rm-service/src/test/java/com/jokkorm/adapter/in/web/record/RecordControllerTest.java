package com.jokkorm.adapter.in.web.record;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jokkorm.adapter.in.web.common.GlobalExceptionHandler;
import com.jokkorm.application.record.FindRecordByCoteUseCase;
import com.jokkorm.application.record.GetConsolidatedRecordUseCase;
import com.jokkorm.application.record.GetConsolidatedRecordUseCase.MasterStatus;
import com.jokkorm.application.record.ListRecordsUseCase;
import com.jokkorm.application.record.RecordNotFoundException;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
import com.jokkorm.domain.master.MasterArchiveView;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import com.jokkorm.domain.record.PhysicalArchiveStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests slice du RecordController avec MockMvc standalone — pas de Spring context lourd,
 * pas de DB. Teste le mapping HTTP, les codes retour et le problem+json.
 */
@ExtendWith(MockitoExtension.class)
class RecordControllerTest {

    private static final TenantId TENANT = TenantId.of("acme");

    @Mock ListRecordsUseCase listRecords;
    @Mock FindRecordByCoteUseCase findByCote;
    @Mock GetConsolidatedRecordUseCase getConsolidated;
    @Mock com.jokkorm.application.record.StoreRecordInContainerUseCase storeInContainer;
    @Mock com.jokkorm.application.physical.RecordMovementUseCase recordMovement;
    @Mock com.jokkorm.application.physical.ListRecordMovementsUseCase listMovements;

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
            .standaloneSetup(new RecordController(listRecords, findByCote, getConsolidated,
                storeInContainer, recordMovement, listMovements))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        // Simule le TenantContextFilter autour de chaque requête.
        if (!TenantContext.isBound()) {
            TenantContext.runWith(TENANT, () -> {});
        }
    }

    @AfterEach
    void cleanup() {
        // runWith relâche automatiquement, mais on force au cas où un test lève.
        while (TenantContext.isBound()) {
            TenantContext.runWith(TENANT, () -> {});
            break;
        }
    }

    @Test
    @DisplayName("GET /records retourne la page paginée")
    void list_returns_page() throws Exception {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(
            TENANT, Cote.of("DMG/MP/2024/047"), Instant.parse("2026-11-21T10:00:00Z"));
        when(listRecords.execute(eq(TENANT), any(), any()))
            .thenReturn(new Page<>(List.of(r), 0, 20, 1L));

        TenantContext.runWith(TENANT, () -> {
            try {
                mvc.perform(get("/api/v1/records").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].cote").value("DMG/MP/2024/047"))
                    .andExpect(jsonPath("$.content[0].status").value("REGISTERED"))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.last").value(true));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("GET /records?status=STORED propage le filtre")
    void list_forwards_status_filter() throws Exception {
        when(listRecords.execute(eq(TENANT), eq(PhysicalArchiveStatus.STORED), any()))
            .thenReturn(new Page<>(List.of(), 0, 20, 0L));

        TenantContext.runWith(TENANT, () -> {
            try {
                mvc.perform(get("/api/v1/records").param("status", "STORED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(0));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("GET /records/{cote} retourne 404 problem+json quand absent")
    void find_by_cote_returns_404() throws Exception {
        when(findByCote.execute(eq(TENANT), any()))
            .thenThrow(new RecordNotFoundException("missing"));

        TenantContext.runWith(TENANT, () -> {
            try {
                mvc.perform(get("/api/v1/records/by-cote").param("cote", "UNKNOWN"))
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.type").value("https://jokkorm.io/errors/record-not-found"))
                    .andExpect(jsonPath("$.status").value(404));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("GET /records?page=-1 retourne 400 problem+json")
    void list_rejects_invalid_page() throws Exception {
        TenantContext.runWith(TENANT, () -> {
            try {
                mvc.perform(get("/api/v1/records").param("page", "-1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://jokkorm.io/errors/invalid-argument"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("GET /records/consolidated — maître AVAILABLE : champs fusionnés dans la réponse")
    void consolidated_merges_master_fields_when_available() throws Exception {
        PhysicalArchiveRecord local = PhysicalArchiveRecord.register(
            TENANT, Cote.of("DMG/MP/2024/047"), Instant.parse("2026-11-22T10:00:00Z"));
        MasterArchiveView masterView = new MasterArchiveView(
            Cote.of("DMG/MP/2024/047"),
            "Marché public n° 2024-047",
            "finances/marches/2024",
            10, "VERSEMENT_AN", false, "https://ged.example/archive/42");
        when(getConsolidated.execute(eq(TENANT), any()))
            .thenReturn(new GetConsolidatedRecordUseCase.Result(local, masterView, MasterStatus.AVAILABLE));

        TenantContext.runWith(TENANT, () -> {
            try {
                mvc.perform(get("/api/v1/records/consolidated").param("cote", "DMG/MP/2024/047"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.local.cote").value("DMG/MP/2024/047"))
                    .andExpect(jsonPath("$.local.status").value("REGISTERED"))
                    .andExpect(jsonPath("$.master.status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.master.title").value("Marché public n° 2024-047"))
                    .andExpect(jsonPath("$.master.duaYears").value(10))
                    .andExpect(jsonPath("$.master.finalFate").value("VERSEMENT_AN"))
                    .andExpect(jsonPath("$.master.legalHold").value(false))
                    .andExpect(jsonPath("$.master.masterPageUrl").value("https://ged.example/archive/42"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("GET /records/consolidated — maître UNAVAILABLE : vue physique seule, master.status renseigné")
    void consolidated_returns_physical_only_when_master_unavailable() throws Exception {
        PhysicalArchiveRecord local = PhysicalArchiveRecord.register(
            TENANT, Cote.of("X/1"), Instant.parse("2026-11-22T10:00:00Z"));
        when(getConsolidated.execute(eq(TENANT), any()))
            .thenReturn(new GetConsolidatedRecordUseCase.Result(local, null, MasterStatus.UNAVAILABLE));

        TenantContext.runWith(TENANT, () -> {
            try {
                mvc.perform(get("/api/v1/records/consolidated").param("cote", "X/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.local.cote").value("X/1"))
                    .andExpect(jsonPath("$.master.status").value("UNAVAILABLE"))
                    .andExpect(jsonPath("$.master.title").doesNotExist());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("GET /records/consolidated — record local absent : 404 problem+json")
    void consolidated_returns_404_when_local_missing() throws Exception {
        when(getConsolidated.execute(eq(TENANT), any()))
            .thenThrow(new RecordNotFoundException("missing"));

        TenantContext.runWith(TENANT, () -> {
            try {
                mvc.perform(get("/api/v1/records/consolidated").param("cote", "UNKNOWN"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.type").value("https://jokkorm.io/errors/record-not-found"));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
