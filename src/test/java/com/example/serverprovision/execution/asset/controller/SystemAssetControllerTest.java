package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.dto.SealResult;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetAreaGroupResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetOverviewResponse;
import com.example.serverprovision.execution.asset.dto.response.SystemAssetSlotResponse;
import com.example.serverprovision.execution.asset.exception.SystemAssetAreaNotFoundException;
import com.example.serverprovision.execution.asset.exception.SystemAssetSealNotSupportedException;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.asset.service.SystemAssetDashboardService;
import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.global.history.AssetHistorySettingsService;
import com.example.serverprovision.global.history.dto.response.AssetHistorySettingsResponse;
import com.example.serverprovision.global.marker.exception.MarkerWriteFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * E1-I-3-a CP4 — 통합 시스템 자산 컨트롤러의 HTTP 계층 검증. 대시보드·운영 설정·영역 봉인·재검증을 실제
 * 상태코드·리다이렉트·flash·model 로 확인한다(구 진단 대시보드/설정 테스트를 새 URL 로 이관, 단언 동형 유지).
 * 봉인만 영역 인자를 받아 다중화하며, 없는 영역(404)·미지원/미구성(409)·마커 IO 실패(500)의 advice 매핑은
 * Service mock 이 던지는 예외를 컨트롤러 try/catch 없이 실제로 흘려 검증한다(Mockito 대체는 서비스 단까지).
 */
@WebMvcTest(controllers = SystemAssetController.class)
class SystemAssetControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SystemAssetDashboardService dashboardService;
    @MockitoBean
    AssetHistorySettingsService settingsService;
    @MockitoBean
    com.example.serverprovision.execution.asset.service.SealedFileInspector sealedFileInspector;
    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void stubRenderContext() {
        given(dashboardService.loadOverview()).willReturn(overview());
        given(settingsService.getRetentionCount()).willReturn(3);
        given(settingsService.current()).willReturn(new AssetHistorySettingsResponse(3, null));
    }

    // ── 조회 화면 (성공) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /system/asset — 200 + overview 모델(영역 2개·집계값 렌더)")
    void dashboard_ok() throws Exception {
        mvc.perform(get("/system/asset"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/asset/dashboard"))
                .andExpect(model().attributeExists("overview"))
                .andExpect(content().string(allOf(
                        containsString("진단 리눅스 부팅 자산"),
                        containsString("TFTP 부팅 인프라 자산"),
                        containsString("전체 정상 2 / 2"))));
    }

    @Test
    @DisplayName("GET /system/asset/settings — 200 + 보존폼·현재값·봉인맥락 모델")
    void settings_get_ok() throws Exception {
        mvc.perform(get("/system/asset/settings"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/asset/settings"))
                .andExpect(model().attributeExists("retentionForm", "retentionSettings", "overview"));
    }

    // ── 액션 PRG (성공) ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /system/asset/settings — 유효 보존 개수 → 302 self-PRG + update 호출")
    void updateSettings_valid_redirects() throws Exception {
        mvc.perform(post("/system/asset/settings").param("retentionCount", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/asset/settings"));
        verify(settingsService).update(5);
    }

    @Test
    @DisplayName("POST /system/asset/DIAGNOSTIC/seal — 302 대시보드 PRG + flash + seal 호출")
    void seal_diagnostic_redirects() throws Exception {
        given(dashboardService.seal("DIAGNOSTIC")).willReturn(new SealResult(6, 0));

        mvc.perform(post("/system/asset/DIAGNOSTIC/seal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/asset"))
                .andExpect(flash().attributeExists("flashMessage"));
        verify(dashboardService).seal("DIAGNOSTIC");
    }

    @Test
    @DisplayName("POST /system/asset/TFTP/seal — 302 대시보드 PRG + flash + seal 호출(다중 영역)")
    void seal_tftp_redirects() throws Exception {
        given(dashboardService.seal("TFTP")).willReturn(new SealResult(1, 0));

        mvc.perform(post("/system/asset/TFTP/seal"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/asset"))
                .andExpect(flash().attributeExists("flashMessage"));
        verify(dashboardService).seal("TFTP");
    }

    @Test
    @DisplayName("POST /system/asset/recheck — 302 대시보드 PRG + 해시 캐시 무효화(강제 신선 재검증)")
    void recheck_redirects() throws Exception {
        mvc.perform(post("/system/asset/recheck"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/asset"))
                .andExpect(flash().attributeExists("flashMessage"));
        verify(sealedFileInspector).invalidateHashCache();
    }

    // ── 400 : 보존 개수 필드 검증(SSR 폼은 재렌더 + 필드 에러) ──────────────────

    @Test
    @DisplayName("POST /system/asset/settings — 보존 개수 0 → 재렌더 + 필드 에러(update 미호출)")
    void updateSettings_zero_fieldError() throws Exception {
        mvc.perform(post("/system/asset/settings").param("retentionCount", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/asset/settings"))
                .andExpect(model().attributeHasFieldErrors("retentionForm", "retentionCount"));
        verify(settingsService, never()).update(anyInt());
    }

    @Test
    @DisplayName("POST /system/asset/settings — 숫자 아닌 값 → 재렌더 + 필드 에러(타입 불일치)")
    void updateSettings_nonNumeric_fieldError() throws Exception {
        mvc.perform(post("/system/asset/settings").param("retentionCount", "abc"))
                .andExpect(status().isOk())
                .andExpect(view().name("system/asset/settings"))
                .andExpect(model().attributeHasFieldErrors("retentionForm", "retentionCount"));
        verify(settingsService, never()).update(anyInt());
    }

    // ── 404 : 영역 키 위조 ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /system/asset/NOPE/seal — 없는 영역 키 → 404 (SystemAssetAreaNotFound)")
    void seal_unknownArea_notFound() throws Exception {
        willThrow(SystemAssetAreaNotFoundException.of("NOPE")).given(dashboardService).seal("NOPE");

        mvc.perform(post("/system/asset/NOPE/seal"))
                .andExpect(status().isNotFound());
    }

    // ── 409 : 미구성(재사용) · 미지원(신규) ────────────────────────────────────

    @Test
    @DisplayName("POST /system/asset/DIAGNOSTIC/seal — 서빙 비활성 → 409 (ServingDisabled, direct POST 안전망)")
    void seal_servingDisabled_conflict() throws Exception {
        willThrow(SystemAssetServingDisabledException.servingDisabled()).given(dashboardService).seal("DIAGNOSTIC");

        mvc.perform(post("/system/asset/DIAGNOSTIC/seal"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /system/asset/{area}/seal — 서비스가 봉인 미지원을 신고 → 409 (SealNotSupported advice 매핑)")
    void seal_notSupported_conflict() throws Exception {
        // 여기서는 서비스 mock 이 던지는 SealNotSupported 를 컨트롤러가 409 로 흘리는 advice 매핑만 검증한다.
        // 현재 프로덕션 영역(DIAGNOSTIC·TFTP)은 둘 다 봉인을 지원하므로, "봉인 미지원 영역의 default seal()
        // 이 실제로 이 예외를 던지는가"라는 domain trigger 는 SystemAssetAreaDefaultsTest 가 확장점 단에서 검증한다.
        willThrow(SystemAssetSealNotSupportedException.of(SystemAssetAreaKey.DIAGNOSTIC)).given(dashboardService).seal("DIAGNOSTIC");

        mvc.perform(post("/system/asset/DIAGNOSTIC/seal"))
                .andExpect(status().isConflict());
    }

    // ── 500 : 봉인 중 마커 IO 실패 ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /system/asset/DIAGNOSTIC/seal — 마커 기록 IO 실패 → 500 (MarkerWriteFailed)")
    void seal_markerWriteFailed_serverError() throws Exception {
        willThrow(new MarkerWriteFailedException("marker 기록 실패", new IOException("disk full")))
                .given(dashboardService).seal("DIAGNOSTIC");

        mvc.perform(post("/system/asset/DIAGNOSTIC/seal"))
                .andExpect(status().isInternalServerError());
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    /** 봉인 지원·서빙 활성인 영역 2개(진단·TFTP), 각 정상 슬롯 1행. 대시보드·설정 화면 렌더에 충분하다. */
    private static SystemAssetOverviewResponse overview() {
        SystemAssetAreaGroupResponse diagnostic = new SystemAssetAreaGroupResponse(
                "DIAGNOSTIC", "진단 리눅스 부팅 자산", "CONFIGURED",
                List.of(row("VMLINUZ", "커널 (vmlinuz-lts)", "vmlinuz-lts")), 1, 1, true, List.of());
        SystemAssetAreaGroupResponse tftp = new SystemAssetAreaGroupResponse(
                "TFTP", "TFTP 부팅 인프라 자산", "CONFIGURED",
                List.of(row("IPXE_EFI", "iPXE 부트 (ipxe.efi)", "ipxe.efi")), 1, 1, true, List.of());
        return new SystemAssetOverviewResponse(List.of(diagnostic, tftp), 2, 2);
    }

    private static SystemAssetSlotResponse row(String key, String label, String filename) {
        return new SystemAssetSlotResponse(
                key, label, "netboot 아티팩트", filename, "단일 파일",
                true, false, "13.0 MB", null, "원본 유지", "n-badge-green", "패키지 업데이트 시");
    }
}
