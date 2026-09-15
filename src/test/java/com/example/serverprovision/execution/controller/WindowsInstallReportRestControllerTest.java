package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.response.WindowsInstallCompletionResponse;
import com.example.serverprovision.execution.engine.windows.WindowsInstallCompletionService;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.exception.AgentReportRejectedException;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import com.example.serverprovision.global.security.springsecurity.guest.GuestTestSupport;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E4-1-a-4 CP4 — 완료 보고 창구(D-4)의 HTTP 계층. Mocking 은 서비스까지 — 토큰 헤더 · @Valid 제약 · 기존 JSON advice 의
 * 404/409/400 매핑이 실경로다. 소비 규약(닫기 · 회수 · 전진)은 {@code WindowsInstallCompletionServiceTest}.
 */
@WebMvcTest(controllers = WindowsInstallReportRestController.class)
class WindowsInstallReportRestControllerTest {

    private static final String TOKEN = "a3f9d2c8b41e4f7a9c0d5e6f7a8b9c1d";
    private static final GuestPrincipal GUEST = new GuestPrincipal(UUID.randomUUID(), UUID.randomUUID());   // S19-1
    private static final String URL = "/api/pxe/v1/agent/windows/complete";
    private static final String BODY = """
            {"computerName":"SPV-14174000","osVersion":"Microsoft Windows Server 2025 Standard 10.0.26100",
             "driversAdded":47,"problemDeviceCount":1,"problemDevices":["Unknown device (ACPI\\\\INT34C6)"],
             "setupCompleteLogTail":"Added driver packages:  47"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean WindowsInstallCompletionService completionService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void authenticateAsGuest() {
        GuestTestSupport.authenticateAs(GUEST.guestServerId(), GUEST.systemUUID());
    }

    @AfterEach
    void clearGuest() {
        GuestTestSupport.clear();
    }

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("200 — 닫힘 · 종단(provisioningCompleted true · nextPhase null) · 요청 필드가 서비스에 그대로 전달")
    void complete_closed() throws Exception {
        given(completionService.complete(eq(GUEST), any())).willReturn(new WindowsInstallCompletionResponse(true, true, null));

        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(true))
                .andExpect(jsonPath("$.provisioningCompleted").value(true))
                .andExpect(jsonPath("$.nextPhase").doesNotExist());
        verify(completionService).complete(eq(GUEST), org.mockito.ArgumentMatchers.argThat(r ->
                r.computerName().equals("SPV-14174000") && r.driversAdded() == 47 && r.problemDevices().size() == 1));
    }

    @Test
    @DisplayName("200 — 중복 보고는 closed:false(no-op) · 다음 phase 가 있으면 nextPhase 직렬화")
    void complete_noopWithNextPhase() throws Exception {
        given(completionService.complete(eq(GUEST), any())).willReturn(new WindowsInstallCompletionResponse(false, false, ProvisioningPhase.TESTING));

        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed").value(false))
                .andExpect(jsonPath("$.nextPhase").value("TESTING"));
    }

    @Test
    @DisplayName("200 — installedDiskUniqueId(E4-1-a-6)가 서비스에 그대로 전달된다")
    void complete_carriesInstalledDiskUniqueId() throws Exception {
        given(completionService.complete(eq(GUEST), any())).willReturn(new WindowsInstallCompletionResponse(true, true, null));
        String body = "{\"computerName\":\"SPV-14174000\",\"driversAdded\":47,\"problemDeviceCount\":0,"
                + "\"installedDiskUniqueId\":\"600605B00D18AA1E322943670F84791D\"}";

        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(completionService).complete(eq(GUEST), org.mockito.ArgumentMatchers.argThat(r ->
                "600605B00D18AA1E322943670F84791D".equals(r.installedDiskUniqueId())));
    }

    // ==== 400 ====================================================

    @Test
    @DisplayName("400 — installedDiskUniqueId 65자 → 필드 메시지, 서비스 미호출(E4-1-a-6)")
    void complete_diskUniqueIdTooLong400() throws Exception {
        String tooLong = "x".repeat(65);
        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"computerName\":\"SPV-1\",\"driversAdded\":0,\"problemDeviceCount\":0,\"installedDiskUniqueId\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='installedDiskUniqueId')]").exists());
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    @DisplayName("400 — computerName 16자(NetBIOS 15) · problemDevices 51개 · driversAdded 음수 → 필드 메시지, 서비스 미호출")
    void complete_validation400() throws Exception {
        String many = IntStream.rangeClosed(1, 51).mapToObj(i -> "\"d" + i + "\"").collect(Collectors.joining(","));
        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"computerName\":\"SPV-0123456789AB\",\"driversAdded\":-1,\"problemDeviceCount\":51,\"problemDevices\":[" + many + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='computerName')].message").value(org.hamcrest.Matchers.hasItem("computerName 은 15자 이내여야 합니다(NetBIOS).")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='problemDevices')].message").value(org.hamcrest.Matchers.hasItem("problemDevices 는 50개 이내여야 합니다.")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='driversAdded')]").exists());
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    @DisplayName("400 — computerName 공백 → 필드 메시지(토큰 헤더 누락은 S19-1 부터 게스트 체인의 401 — GuestChainSecurityTest)")
    void complete_blankComputerName400() throws Exception {
        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"computerName\":\" \",\"driversAdded\":0,\"problemDeviceCount\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='computerName')]").exists());
        verify(completionService, never()).complete(any(), any());
    }

    // ==== 401 · 409 ====================================================

    @Test
    @DisplayName("401 — 게스트 principal 없음(체인 뒤에서는 필터의 401 · 슬라이스에서는 @CurrentGuest resolver)")
    void complete_noGuest401() throws Exception {
        GuestTestSupport.clear();
        mvc.perform(post(URL).header("X-Guest-Token", "deadbeef").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    @DisplayName("409 — 미진행(실패 · 종단 · 회수) / 커서 phase 불일치 / 열린 행 없음 — 세 팩토리 모두 Conflict 매핑")
    void complete_rejected409() throws Exception {
        UUID id = UUID.randomUUID();
        for (AgentReportRejectedException ex : new AgentReportRejectedException[]{
                AgentReportRejectedException.notProvisioning(id),
                AgentReportRejectedException.phaseMismatch(id, ProvisioningPhaseStep.OS_INSTALLING, ProvisioningPhaseStep.RAID_APPLYING),
                AgentReportRejectedException.noOpenStep(id, ProvisioningPhaseStep.OS_INSTALLING)}) {
            given(completionService.complete(eq(GUEST), any())).willThrow(ex);
            mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(ex.getMessage()));
        }
    }

    // ==== R15-2 — 항목별 설치 결과 installs ====================================================

    @Test
    @DisplayName("200 — installs(folder · mode · exitCode)가 서비스에 그대로 전달된다 · 생략하면 빈 목록")
    void complete_carriesInstalls() throws Exception {
        given(completionService.complete(eq(GUEST), any())).willReturn(new WindowsInstallCompletionResponse(true, true, null));
        String body = "{\"computerName\":\"SPV-14174000\",\"driversAdded\":47,\"problemDeviceCount\":0,"
                + "\"installs\":[{\"folder\":\"4_aspeed-driver\",\"mode\":\"MSI\",\"exitCode\":0},{\"folder\":\"-\",\"mode\":\"LEGACY\",\"exitCode\":0}]}";

        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        verify(completionService).complete(eq(GUEST), org.mockito.ArgumentMatchers.argThat(r ->
                r.installsOrEmpty().size() == 2 && r.installsOrEmpty().get(0).folder().equals("4_aspeed-driver")
                        && r.installsOrEmpty().get(0).mode().equals("MSI") && r.installsOrEmpty().get(0).exitCode() == 0));

        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verify(completionService).complete(eq(GUEST), org.mockito.ArgumentMatchers.argThat(r -> r.installsOrEmpty().isEmpty()));
    }

    @Test
    @DisplayName("400 — installs 51 개 · 항목의 folder 121자 → @Valid 거절, 서비스 미호출")
    void complete_installsOutOfBounds400() throws Exception {
        String many = IntStream.range(0, 51)
                .mapToObj(i -> "{\"folder\":\"f" + i + "\",\"mode\":\"INF\",\"exitCode\":0}")
                .collect(Collectors.joining(","));
        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"computerName\":\"SPV-1\",\"driversAdded\":0,\"problemDeviceCount\":0,\"installs\":[" + many + "]}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post(URL).header("X-Guest-Token", TOKEN).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"computerName\":\"SPV-1\",\"driversAdded\":0,\"problemDeviceCount\":0,"
                                + "\"installs\":[{\"folder\":\"" + "x".repeat(121) + "\",\"mode\":\"INF\",\"exitCode\":0}]}"))
                .andExpect(status().isBadRequest());
        verify(completionService, never()).complete(any(), any());
    }
}
