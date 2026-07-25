package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetActivationService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetIntegrityService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetVersionService;
import com.example.serverprovision.global.history.AssetHistorySettingsService;
import com.example.serverprovision.global.history.dto.response.AssetHistorySettingsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.example.serverprovision.execution.asset.controller.DiagnosticAssetTestFixtures.dashboard;
import static com.example.serverprovision.execution.asset.controller.DiagnosticAssetTestFixtures.vmlinuz;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * E1-I-2-b-2 재구성 CP4 — 운영 설정 페이지의 HTTP 계층 검증. 보존 개수 저장(유효 self-PRG / 무효 재렌더)과
 * 전 슬롯 봉인(→설정 PRG / 서빙 비활성 409)을 검증한다. 봉인이 대시보드가 아닌 설정으로 옮겨진 것이 핵심.
 */
@WebMvcTest(controllers = DiagnosticAssetController.class)
class DiagnosticAssetControllerSettingsTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DiagnosticAssetIntegrityService integrityService;
    @MockitoBean
    DiagnosticAssetActivationService activationService;
    @MockitoBean
    DiagnosticAssetVersionService versionService;
    @MockitoBean
    AssetHistorySettingsService settingsService;
    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void stubSettingsRender() {
        given(integrityService.loadDashboard()).willReturn(dashboard(vmlinuz()));   // 봉인 카드 맥락
        given(settingsService.getRetentionCount()).willReturn(3);
        given(settingsService.current()).willReturn(new AssetHistorySettingsResponse(3, null));
    }

    @Test
    @DisplayName("GET /settings — 200 + 보존폼·현재값·봉인맥락 모델 + 뷰")
    void settings_get_ok() throws Exception {
        mvc.perform(get("/system/diagnostic-asset/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/diagnostic-asset/settings"))
                .andExpect(model().attributeExists("retentionForm", "retentionSettings", "dashboard"));
    }

    @Test
    @DisplayName("POST /settings — 유효 보존 개수 → 302 self-PRG(/settings) + service 호출")
    void updateSettings_valid_redirects() throws Exception {
        mvc.perform(post("/system/diagnostic-asset/settings").param("retentionCount", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/diagnostic-asset/settings"));
        verify(settingsService).update(5);
    }

    @Test
    @DisplayName("POST /settings — 보존 개수 0 → 재렌더(settings) + 폼 에러(update 미호출)")
    void updateSettings_invalid_rerenders() throws Exception {
        mvc.perform(post("/system/diagnostic-asset/settings").param("retentionCount", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/diagnostic-asset/settings"))
                .andExpect(model().attributeHasFieldErrors("retentionForm", "retentionCount"));
    }

    @Test
    @DisplayName("POST /seal — 302 PRG(/settings) + service 호출")
    void seal_redirects() throws Exception {
        given(integrityService.seal()).willReturn(new SealResult(6, 0));

        mvc.perform(post("/system/diagnostic-asset/seal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/diagnostic-asset/settings"));
        verify(integrityService).seal();
    }

    @Test
    @DisplayName("POST /seal — 서빙 비활성 → 409 (UI 1차 차단의 direct POST 안전망)")
    void seal_servingDisabled_conflict() throws Exception {
        willThrow(SystemAssetServingDisabledException.servingDisabled())
                .given(integrityService).seal();

        mvc.perform(post("/system/diagnostic-asset/seal"))
                .andExpect(status().isConflict());
    }
}
