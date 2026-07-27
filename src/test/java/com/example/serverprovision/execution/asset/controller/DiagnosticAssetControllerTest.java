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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1-I-3-a CP4 — 진단 자산 컨트롤러 축소 후 남은 구 대시보드 URL 검증. 현황 대시보드·운영 설정·전역 봉인·
 * 재검증은 통합 시스템 자산 컨트롤러({@code /system/asset})로 승격됐고(단언은 {@code SystemAssetControllerTest}
 * 로 이관), 여기서는 구 진단 대시보드 URL 이 통합 화면으로 302 리다이렉트되는지만 지킨다(즐겨찾기·기존 링크 보존).
 * 상세·교체·롤백·슬롯 위조 404 는 {@code DiagnosticAssetControllerDetailTest}/{@code ...ActivationTest} 가 유지한다.
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
    @DisplayName("GET /system/diagnostic-asset — 구 대시보드 URL 302 → /system/asset")
    void oldDashboard_redirectsToSystemAsset() throws Exception {
        mvc.perform(get("/system/diagnostic-asset"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/asset"));
    }
}
