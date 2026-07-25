package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.service.DiagnosticAssetActivationService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetIntegrityService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetVersionService;
import com.example.serverprovision.global.history.AssetHistorySettingsService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * E1-I-2-b-2 재구성 CP4 — 대시보드(status-only)·재검증의 HTTP 계층 검증. 대시보드 GET 은 이제 {@code dashboard}
 * 모델 하나만 담고 교체·롤백·보존 위젯을 렌더하지 않는다(상세·설정으로 이동). 상세·설정 엔드포인트는 별도 테스트.
 */
@WebMvcTest(controllers = DiagnosticAssetController.class)
class DiagnosticAssetControllerTest {

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

    @Test
    @DisplayName("GET /system/diagnostic-asset — 200 status-only (dashboard 모델만, 교체·보존 위젯 없음)")
    void dashboard_statusOnly() throws Exception {
        given(integrityService.loadDashboard()).willReturn(dashboard(vmlinuz()));

        mvc.perform(get("/system/diagnostic-asset"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/diagnostic-asset/dashboard"))
                .andExpect(model().attributeExists("dashboard"))
                .andExpect(model().attributeDoesNotExist("versionsBySlot", "retentionSettings", "retentionForm"));
    }

    @Test
    @DisplayName("POST /recheck — 302 PRG (대시보드로, 비변경 새로고침)")
    void recheck_redirects() throws Exception {
        mvc.perform(post("/system/diagnostic-asset/recheck"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/diagnostic-asset"));
    }
}
