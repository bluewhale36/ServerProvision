package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetDashboardResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetSlotResponse;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetIntegrityService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetReplaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
 * E1-I-1 CP4 — 대시보드 컨트롤러의 HTTP 계층 검증. Mocking 은 Service 까지 — 신규 예외
 * ({@code SystemAssetServingDisabledException})의 409 매핑이 컨트롤러 + advice 실경로다(테스트 규율).
 * 슬롯이 고정 enum 이라 경로 변수 forging(404) 표면이 없으므로 404 범주는 해당 없음.
 */
@WebMvcTest(controllers = DiagnosticAssetController.class)
class DiagnosticAssetControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DiagnosticAssetIntegrityService integrityService;

    @MockitoBean
    DiagnosticAssetReplaceService replaceService;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /system/diagnostic-asset — 200 + 대시보드 모델 + 뷰")
    void dashboard_ok() throws Exception {
        given(integrityService.loadDashboard()).willReturn(stubDashboard());

        mvc.perform(get("/system/diagnostic-asset"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/diagnostic-asset/dashboard"))
                .andExpect(model().attributeExists("dashboard"));
    }

    @Test
    @DisplayName("POST /seal — 302 PRG (봉인 후 대시보드로)")
    void seal_redirects() throws Exception {
        given(integrityService.seal()).willReturn(new SealResult(6, 0));

        mvc.perform(post("/system/diagnostic-asset/seal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/diagnostic-asset"));
        verify(integrityService).seal();
    }

    @Test
    @DisplayName("POST /recheck — 302 PRG (재검증 후 대시보드로)")
    void recheck_redirects() throws Exception {
        mvc.perform(post("/system/diagnostic-asset/recheck"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/diagnostic-asset"));
    }

    @Test
    @DisplayName("POST /seal — 서빙 비활성 → 409 (UI 1차 차단의 direct POST 안전망)")
    void seal_servingDisabled_conflict() throws Exception {
        willThrow(SystemAssetServingDisabledException.servingDisabled())
                .given(integrityService).seal();

        mvc.perform(post("/system/diagnostic-asset/seal"))
                .andExpect(status().isConflict());
    }

    private static SystemAssetDashboardResponse stubDashboard() {
        SystemAssetSlotResponse slot = new SystemAssetSlotResponse(
                "VMLINUZ", "커널 (vmlinuz-lts)", "netboot 아티팩트", "vmlinuz-lts", "단일 파일",
                true, true, "13.0 MB", null, "원본 유지", "n-badge-green", "Alpine 버전 업그레이드 시");
        return new SystemAssetDashboardResponse(
                true, "/opt/provisioning/pxe-assets", "http://localhost:7777", List.of(slot), 1, 6);
    }
}
