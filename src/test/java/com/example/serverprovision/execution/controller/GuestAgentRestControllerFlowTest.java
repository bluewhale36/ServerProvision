package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.response.AgentCheckinResponse;
import com.example.serverprovision.execution.dto.response.StepCloseResponse;
import com.example.serverprovision.execution.dto.response.StepOpenResponse;
import com.example.serverprovision.execution.engine.diagnose.AgentReportService;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.ProvisioningHistoryNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.example.serverprovision.execution.engine.raid.RaidApplyPayload;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1-0b CP4 — 에이전트 채널(JSON) 통합. Mocking 은 Service 까지 — 토큰 헤더 계약 · @Valid ·
 * 기존 ApiExceptionHandler 의 404/400 매핑이 실경로다. 전이·멱등·markFailed 의 내부 규약은
 * {@code AgentReportServiceTest} 가 검증한다.
 */
@WebMvcTest(controllers = GuestAgentRestController.class)
class GuestAgentRestControllerFlowTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";

    @Autowired MockMvc mvc;

    @MockitoBean AgentReportService agentReportService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("POST /agent/checkin — 새 지시 값 RAID_INVENTORY 직렬화 (E3.5-1)")
    void checkin_carriesRaidInventoryDirective() throws Exception {
        given(agentReportService.checkin(TOKEN))
                .willReturn(new AgentCheckinResponse(AgentDirective.RAID_INVENTORY, "guest-01"));

        mvc.perform(post("/api/pxe/v1/agent/checkin").header("X-Guest-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directive").value("RAID_INVENTORY"));
    }

    @Test
    @DisplayName("POST /agent/checkin — 200 + 지시 골격(WAIT) + 배너용 서버명(E1-1, DEC-33)")
    void checkin_returnsDirective() throws Exception {
        given(agentReportService.checkin(TOKEN))
                .willReturn(new AgentCheckinResponse(AgentDirective.WAIT, "rack-a-03"));

        mvc.perform(post("/api/pxe/v1/agent/checkin").header("X-Guest-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directive").value("WAIT"))
                .andExpect(jsonPath("$.serverName").value("rack-a-03"));
    }

    @Test
    @DisplayName("POST /agent/steps — DIAGNOSTIC_BOOTING(E1-1 신규 상수) 역직렬화 + 201")
    void openStep_diagnosticBooting_returns201() throws Exception {
        UUID stepId = UUID.randomUUID();
        given(agentReportService.openStep(TOKEN, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING))
                .willReturn(new StepOpenResponse(stepId));

        mvc.perform(post("/api/pxe/v1/agent/steps").header("X-Guest-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stepCode\":\"DIAGNOSTIC_BOOTING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stepId").value(stepId.toString()));
    }

    @Test
    @DisplayName("POST /agent/steps — 201 + stepId (RUNNING 열림)")
    void openStep_returns201WithStepId() throws Exception {
        UUID stepId = UUID.randomUUID();
        given(agentReportService.openStep(TOKEN, ProvisioningPhaseStep.INFORMATION_COLLECTING))
                .willReturn(new StepOpenResponse(stepId));

        mvc.perform(post("/api/pxe/v1/agent/steps").header("X-Guest-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stepCode\":\"INFORMATION_COLLECTING\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stepId").value(stepId.toString()));
    }

    @Test
    @DisplayName("POST /agent/steps/{id}/close — 200 + 다음 지시 바디(E1-2 — REBOOT 의 유일한 운반로)")
    void closeStep_returns200() throws Exception {
        UUID stepId = UUID.randomUUID();
        given(agentReportService.closeStep(eq(TOKEN), eq(stepId), eq(ProvisioningStatus.FAILED), any()))
                .willReturn(new StepCloseResponse(AgentDirective.WAIT));

        mvc.perform(post("/api/pxe/v1/agent/steps/{id}/close", stepId).header("X-Guest-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\",\"statusMeta\":\"{\\\"reason\\\":\\\"x\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directive").value("WAIT"));

        verify(agentReportService).closeStep(eq(TOKEN), eq(stepId),
                eq(ProvisioningStatus.FAILED), any());
    }

    @Test
    @DisplayName("close 응답 directive=REBOOT — 완주 지시가 close 바디로 운반된다 (E1-2)")
    void closeStep_carriesReboot() throws Exception {
        UUID stepId = UUID.randomUUID();
        given(agentReportService.closeStep(eq(TOKEN), eq(stepId), eq(ProvisioningStatus.SUCCEEDED), any()))
                .willReturn(new StepCloseResponse(AgentDirective.REBOOT));

        mvc.perform(post("/api/pxe/v1/agent/steps/{id}/close", stepId).header("X-Guest-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCEEDED\",\"statusMeta\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directive").value("REBOOT"));
    }

    // ==== 404 — 토큰 사칭 · stepId forging ============================

    @Test
    @DisplayName("토큰 불일치 — checkin/open/close 3종 모두 404 (존재 비노출)")
    void tokenMismatch_returns404() throws Exception {
        willThrow(GuestServerNotFoundException.byToken()).given(agentReportService).checkin(any());
        willThrow(GuestServerNotFoundException.byToken()).given(agentReportService).openStep(any(), any());
        willThrow(GuestServerNotFoundException.byToken())
                .given(agentReportService).closeStep(any(), any(), any(), any());

        mvc.perform(post("/api/pxe/v1/agent/checkin").header("X-Guest-Token", "bad"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/pxe/v1/agent/steps").header("X-Guest-Token", "bad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stepCode\":\"OS_INSTALLING\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/pxe/v1/agent/steps/{id}/close", UUID.randomUUID())
                        .header("X-Guest-Token", "bad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUCCEEDED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("프로비저닝 중 아닌 서버(미개시·회수·종단) 보고 — 409 AgentReportRejected (게이트 우회 거절)")
    void notProvisioning_returns409() throws Exception {
        willThrow(AgentReportRejectedException.notProvisioning(UUID.randomUUID()))
                .given(agentReportService).checkin(TOKEN);
        willThrow(AgentReportRejectedException.notProvisioning(UUID.randomUUID()))
                .given(agentReportService).openStep(any(), any());

        mvc.perform(post("/api/pxe/v1/agent/checkin").header("X-Guest-Token", TOKEN))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/pxe/v1/agent/steps").header("X-Guest-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"stepCode\":\"INFORMATION_COLLECTING\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("open — 커서 phase 밖 step 보고 — 409 AgentReportRejected(phaseMismatch, ES-2 게이트)")
    void openStep_phaseMismatch_returns409() throws Exception {
        willThrow(AgentReportRejectedException.phaseMismatch(
                UUID.randomUUID(), ProvisioningPhaseStep.BIOS_UPDATING, ProvisioningPhaseStep.INFORMATION_COLLECTING))
                .given(agentReportService).openStep(TOKEN, ProvisioningPhaseStep.BIOS_UPDATING);

        mvc.perform(post("/api/pxe/v1/agent/steps").header("X-Guest-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stepCode\":\"BIOS_UPDATING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("현재 진행 phase 밖의 step")));
    }

    @Test
    @DisplayName("잘못된 stepId(타 게스트 forging 포함) — 404 ProvisioningHistoryNotFound")
    void unknownStepId_returns404() throws Exception {
        UUID stepId = UUID.randomUUID();
        willThrow(new ProvisioningHistoryNotFoundException(stepId))
                .given(agentReportService).closeStep(any(), eq(stepId), any(), any());

        mvc.perform(post("/api/pxe/v1/agent/steps/{id}/close", stepId).header("X-Guest-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUCCEEDED\"}"))
                .andExpect(status().isNotFound());
    }

    // ==== 400 — 계약 위반 ============================================

    @Test
    @DisplayName("open — stepCode 누락 → 400 (@NotNull)")
    void open_missingStepCode_returns400() throws Exception {
        mvc.perform(post("/api/pxe/v1/agent/steps").header("X-Guest-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("close — RUNNING 으로 닫기 → 400 (종결 상태만 허용, @AssertTrue)")
    void close_nonTerminalStatus_returns400() throws Exception {
        mvc.perform(post("/api/pxe/v1/agent/steps/{id}/close", UUID.randomUUID())
                        .header("X-Guest-Token", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"RUNNING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("토큰 헤더 누락 → 400 (MissingRequestHeader)")
    void missingTokenHeader_returns400() throws Exception {
        mvc.perform(post("/api/pxe/v1/agent/checkin"))
                .andExpect(status().isBadRequest());
    }

    // ==== E3.5-3 — 집행 payload · 칩 힌트 · 검증 지시 직렬화 (진리표 V12) ====

    @Test
    @DisplayName("POST /agent/checkin — RAID_APPLY payload 와 칩 힌트 직렬화 (E3.5-3 결정 1 · 검수 반영)")
    void checkin_carriesRaidApplyPayloadAndChipHint() throws Exception {
        RaidApplyPayload payload = new RaidApplyPayload(true,
                java.util.List.of(new RaidApplyPayload.VolumeSpec("spvR1V1", RaidLevel.RAID1,
                        java.util.List.of("252:0", "252:1"), null, java.util.List.of(), null)),
                java.util.List.of("252:4"));
        given(agentReportService.checkin(TOKEN)).willReturn(new AgentCheckinResponse(
                AgentDirective.RAID_APPLY, "guest-01", payload, RaidChipFamily.agentChipHint()));

        mvc.perform(post("/api/pxe/v1/agent/checkin").header("X-Guest-Token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directive").value("RAID_APPLY"))
                .andExpect(jsonPath("$.raidApply.deleteExisting").value(true))
                .andExpect(jsonPath("$.raidApply.volumes[0].name").value("spvR1V1"))
                .andExpect(jsonPath("$.raidApply.volumes[0].level").value("RAID1"))
                .andExpect(jsonPath("$.raidApply.volumes[0].slots[1]").value("252:1"))
                .andExpect(jsonPath("$.raidApply.jbod[0]").value("252:4"))
                .andExpect(jsonPath("$.raidChips").value("1000:0097=MPT_IR 1000:005d=MEGARAID"));
    }

    @Test
    @DisplayName("POST /agent/steps/{id}/close — RAID_VERIFY 지시 직렬화 · payload 없는 지시는 필드가 비어 있다")
    void close_carriesRaidVerifyDirective() throws Exception {
        UUID stepId = UUID.randomUUID();
        given(agentReportService.closeStep(eq(TOKEN), eq(stepId), any(), any()))
                .willReturn(new StepCloseResponse(AgentDirective.RAID_VERIFY, null,
                        RaidChipFamily.agentChipHint()));

        mvc.perform(post("/api/pxe/v1/agent/steps/{id}/close", stepId)
                        .header("X-Guest-Token", TOKEN)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCEEDED\",\"statusMeta\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.directive").value("RAID_VERIFY"))
                .andExpect(jsonPath("$.raidChips").exists());
    }

    @Test
    @DisplayName("POST /agent/steps — RAID_APPLYING · RAID_VERIFYING(E3.5-3 실배선 step) 역직렬화 + 201")
    void openStep_raidApplySteps_return201() throws Exception {
        for (String step : java.util.List.of("RAID_APPLYING", "RAID_VERIFYING")) {
            UUID stepId = UUID.randomUUID();
            given(agentReportService.openStep(eq(TOKEN), any())).willReturn(new StepOpenResponse(stepId));

            mvc.perform(post("/api/pxe/v1/agent/steps")
                            .header("X-Guest-Token", TOKEN)
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"stepCode\":\"" + step + "\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.stepId").value(stepId.toString()));
        }
    }
}
