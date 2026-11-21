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
import com.jokkorm.application.record.ListRecordsUseCase;
import com.jokkorm.application.record.RecordNotFoundException;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.common.Page;
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

    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders
            .standaloneSetup(new RecordController(listRecords, findByCote))
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
}
