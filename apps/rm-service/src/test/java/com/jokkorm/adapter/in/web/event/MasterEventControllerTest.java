package com.jokkorm.adapter.in.web.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jokkorm.adapter.in.web.common.GlobalExceptionHandler;
import com.jokkorm.application.record.ApplyLegalHoldUseCase;
import com.jokkorm.application.record.HandleCoteAssignedEventUseCase;
import com.jokkorm.commons.tenancy.TenantContext;
import com.jokkorm.commons.tenancy.TenantId;
import com.jokkorm.domain.record.Cote;
import com.jokkorm.domain.record.PhysicalArchiveRecord;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class MasterEventControllerTest {

    private static final TenantId TENANT = TenantId.of("acme");

    @Mock HandleCoteAssignedEventUseCase handleCoteAssigned;
    @Mock ApplyLegalHoldUseCase applyLegalHold;

    private MockMvc mvc() {
        return MockMvcBuilders
            .standaloneSetup(new MasterEventController(handleCoteAssigned, applyLegalHold))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    @DisplayName("POST /events/master/cote-assigned → 202 + record")
    void cote_assigned_returns_202() throws Exception {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(
            TENANT, Cote.of("DMG/MP/2024/047"), Instant.parse("2026-11-21T10:00:00Z"));
        when(handleCoteAssigned.execute(eq(TENANT), any())).thenReturn(r);

        TenantContext.runWith(TENANT, () -> {
            try {
                mvc().perform(post("/api/v1/events/master/cote-assigned")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cote\":\"DMG/MP/2024/047\"}"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.cote").value("DMG/MP/2024/047"))
                    .andExpect(jsonPath("$.status").value("REGISTERED"));
                verify(handleCoteAssigned).execute(eq(TENANT), eq(Cote.of("DMG/MP/2024/047")));
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("POST /events/master/cote-assigned sans cote → 400 validation")
    void cote_assigned_rejects_blank_cote() throws Exception {
        TenantContext.runWith(TENANT, () -> {
            try {
                mvc().perform(post("/api/v1/events/master/cote-assigned")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cote\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.type").value("https://jokkorm.io/errors/validation-failed"))
                    .andExpect(jsonPath("$.fieldErrors.cote").exists());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    @Test
    @DisplayName("POST /events/master/legal-hold FREEZE → 202")
    void legal_hold_freeze() throws Exception {
        PhysicalArchiveRecord r = PhysicalArchiveRecord.register(
            TENANT, Cote.of("DMG/MP/2024/047"), Instant.parse("2026-11-21T10:00:00Z"));
        when(applyLegalHold.execute(eq(TENANT), any(), eq(ApplyLegalHoldUseCase.Action.FREEZE)))
            .thenReturn(r);

        TenantContext.runWith(TENANT, () -> {
            try {
                mvc().perform(post("/api/v1/events/master/legal-hold")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"cote\":\"DMG/MP/2024/047\",\"action\":\"FREEZE\"}"))
                    .andExpect(status().isAccepted());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}
