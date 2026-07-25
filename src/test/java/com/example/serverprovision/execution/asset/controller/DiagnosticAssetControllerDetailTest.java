package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.dto.response.AssetVersionView;
import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetActivationService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetIntegrityService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetVersionService;
import com.example.serverprovision.global.history.AssetHistorySettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.example.serverprovision.execution.asset.controller.DiagnosticAssetTestFixtures.apkovl;
import static com.example.serverprovision.execution.asset.controller.DiagnosticAssetTestFixtures.vmlinuz;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * E1-I-2-b-2 재구성 CP4 — 자산 상세 페이지의 HTTP 계층 검증. 교체 가능 슬롯(버전 이력·롤백)과 비교체 슬롯
 * (읽기전용), 그리고 없는 슬롯 이름(forging)의 404 를 검증한다. 상세는 loadDashboard 를 필터해 단일 슬롯을 얻는다.
 */
@WebMvcTest(controllers = DiagnosticAssetController.class)
class DiagnosticAssetControllerDetailTest {

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
    void stubDetailRender() {
        given(integrityService.isServing()).willReturn(true);
        given(integrityService.loadSlot(DiagnosticAsset.VMLINUZ)).willReturn(vmlinuz());
        given(integrityService.loadSlot(DiagnosticAsset.APKOVL)).willReturn(apkovl());
        given(versionService.listVersions(DiagnosticAsset.VMLINUZ)).willReturn(
                List.of(new AssetVersionView(1L, "2026-07-25 00:00:00", "13.0 MB", "abcdef012345")));
    }

    @Test
    @DisplayName("GET /{slot} — 교체 가능 슬롯 상세 200 (slot·versions·servingActive)")
    void detail_replaceable_ok() throws Exception {
        mvc.perform(get("/system/diagnostic-asset/{slot}", "VMLINUZ"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/diagnostic-asset/detail"))
                .andExpect(model().attributeExists("slot", "versions", "servingActive"));
    }

    @Test
    @DisplayName("GET /{slot} — 비교체 슬롯(apkovl) 상세 200 (읽기전용, 버전 빈 목록)")
    void detail_nonReplaceable_ok() throws Exception {
        mvc.perform(get("/system/diagnostic-asset/{slot}", "APKOVL"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/diagnostic-asset/detail"))
                .andExpect(model().attributeExists("slot", "versions", "servingActive"));
    }

    @Test
    @DisplayName("GET /{slot} — 없는 슬롯 이름(forging) → 404")
    void detail_unknownSlot_notFound() throws Exception {
        mvc.perform(get("/system/diagnostic-asset/{slot}", "bogus"))
                .andExpect(status().isNotFound());
    }
}
