package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.response.OsInstallCompletionResponse;
import com.example.serverprovision.execution.engine.rhel.RhelInstallCompletionService;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.global.exception.UserFacingMessage;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import com.example.serverprovision.global.security.springsecurity.guest.GuestTestSupport;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RHEL 완료 보고 창구의 HTTP 계층. 서비스까지만 mock 이고 @Valid 제약과 JSON advice 의 400 · 409 매핑은 실제로 돈다.
 * 404 범주는 없다. 이 창구는 NotFoundException 을 던지는 경로가 없다(Windows 창구와 같음).
 */
@WebMvcTest(controllers = RhelInstallReportRestController.class)
class RhelInstallReportRestControllerTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";
    private static final GuestPrincipal GUEST = new GuestPrincipal(UUID.randomUUID(), UUID.randomUUID());
    private static final String URL = "/api/pxe/v1/agent/rhel/complete";
    private static final String BODY = """
            {"osId":"rocky","osVersion":"9.5","osPrettyName":"Rocky Linux 9.5 (Blue Onyx)",
             "kernel":"5.14.0-503.14.1.el9_5.x86_64","hostname":"localhost",
             "rootDisk":"wwn-0x600508e000000000","systemUuid":"4c4c4544-0042-3510-8051-b4c04f4e3332"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean RhelInstallCompletionService completionService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void authenticateAsGuest() {
        GuestTestSupport.authenticateAs(GUEST.guestServerId(), GUEST.systemUUID());
    }

    @AfterEach
    void clearGuest() {
        GuestTestSupport.clear();
    }

    @Test
    @DisplayName("200 — 닫힘 · 종단, 요청 필드가 서비스에 그대로 간다")
    void complete_closed() throws Exception {
        given(completionService.complete(eq(GUEST), any())).willReturn(new OsInstallCompletionResponse(true, true, null));

        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.provisioningCompleted").value(true))
                .andExpect(jsonPath("$.nextPhase").doesNotExist());
        verify(completionService).complete(eq(GUEST), argThat(r -> r.kernel().equals("5.14.0-503.14.1.el9_5.x86_64")
                && r.rootDisk().equals("wwn-0x600508e000000000")
                && r.systemUuid().equals("4c4c4544-0042-3510-8051-b4c04f4e3332")
                && r.osPrettyName().equals("Rocky Linux 9.5 (Blue Onyx)")));
    }

    @Test
    @DisplayName("200 — 중복 보고는 closed:false, 다음 phase 가 있으면 nextPhase")
    void complete_noopWithNextPhase() throws Exception {
        given(completionService.complete(eq(GUEST), any()))
                .willReturn(new OsInstallCompletionResponse(false, false, ProvisioningPhase.OS_SETTING));

        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.nextPhase").value("OS_SETTING"));
    }

    @Test
    @DisplayName("200 — 커널 말고는 비어도 받는다(최소 설치에서 못 읽은 값)")
    void complete_onlyKernel() throws Exception {
        given(completionService.complete(eq(GUEST), any())).willReturn(new OsInstallCompletionResponse(true, true, null));

        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kernel\":\"5.14.0\",\"rootDisk\":\"\",\"systemUuid\":\"\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("400 — kernel 빈 값은 필드 메시지, 서비스를 부르지 않는다")
    void complete_blankKernel400() throws Exception {
        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kernel\":\" \",\"osId\":\"rocky\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='kernel')].message").value(Matchers.hasItem("kernel 은 필수입니다.")));
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    @DisplayName("400 — 길이 초과(osPrettyName 129 자 · systemUuid 37 자)는 필드 메시지")
    void complete_tooLong400() throws Exception {
        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kernel\":\"5.14\",\"osPrettyName\":\"" + "x".repeat(129) + "\",\"systemUuid\":\"" + "u".repeat(37) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='osPrettyName')].message")
                        .value(Matchers.hasItem("osPrettyName 은 128자 이내여야 합니다.")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='systemUuid')]").exists());
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    @DisplayName("401 — 게스트 principal 없음")
    void complete_noGuest401() throws Exception {
        GuestTestSupport.clear();
        mvc.perform(post(URL).header("X-Guest-Token", "deadbeef").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    @DisplayName("409 — 미진행 · 커서 phase 불일치 · 열린 행 없음 세 경우 모두 Conflict")
    void complete_rejected409() throws Exception {
        UUID id = UUID.randomUUID();
        for (AgentReportRejectedException ex : new AgentReportRejectedException[]{
                AgentReportRejectedException.notProvisioning(id),
                AgentReportRejectedException.phaseMismatch(id, ProvisioningPhaseStep.OS_INSTALLING, ProvisioningPhaseStep.RAID_APPLYING),
                AgentReportRejectedException.noOpenStep(id, ProvisioningPhaseStep.OS_INSTALLING)}) {
            given(completionService.complete(eq(GUEST), any())).willThrow(ex);
            mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(UserFacingMessage.withoutInternalIds(ex.getMessage())));
        }
    }
}
