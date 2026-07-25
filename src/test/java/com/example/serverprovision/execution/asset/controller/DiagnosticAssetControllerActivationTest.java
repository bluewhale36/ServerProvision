package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetNotReplaceableException;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetReplaceEmptyException;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetActivationService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetIntegrityService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetVersionService;
import com.example.serverprovision.global.history.AssetHistorySettingsService;
import com.example.serverprovision.global.history.AssetVersionKey;
import com.example.serverprovision.global.history.exception.AssetVersionNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1-I-2-a / b-2 CP4 — 활성화 엔드포인트(교체·롤백)의 HTTP 계층 검증. slot 파싱(404)은 컨트롤러 실경로,
 * 409/400/404 는 Service 가 던지는 예외의 advice 매핑이 실경로다(테스트 규율 — 신규 예외는 실트리거 동반).
 */
@WebMvcTest(controllers = DiagnosticAssetController.class)
class DiagnosticAssetControllerActivationTest {

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

    private static MockMultipartFile upload() {
        return new MockMultipartFile("file", "vmlinuz-lts", "application/octet-stream", "new-bytes".getBytes());
    }

    // ── 교체(replace) ────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /{slot}/replace — 302 PRG + service 호출")
    void replace_redirects() throws Exception {
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "VMLINUZ").file(upload()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/diagnostic-asset/VMLINUZ"));
        verify(activationService).replace(eq(DiagnosticAsset.VMLINUZ), any(MultipartFile.class));
    }

    @Test
    @DisplayName("교체 — 없는 slot 이름 → 404 (컨트롤러 parseSlot)")
    void replace_unknownSlot_notFound() throws Exception {
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "NOPE").file(upload()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("교체 — 비대상(apkovl) → 409")
    void replace_notReplaceable_conflict() throws Exception {
        willThrow(DiagnosticAssetNotReplaceableException.of(DiagnosticAsset.APKOVL))
                .given(activationService).replace(eq(DiagnosticAsset.APKOVL), any(MultipartFile.class));
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "APKOVL").file(upload()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("교체 — 서빙 비활성 → 409")
    void replace_servingDisabled_conflict() throws Exception {
        willThrow(SystemAssetServingDisabledException.servingDisabled())
                .given(activationService).replace(eq(DiagnosticAsset.VMLINUZ), any(MultipartFile.class));
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "VMLINUZ").file(upload()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("교체 — 빈 파일 → 400")
    void replace_emptyFile_badRequest() throws Exception {
        willThrow(DiagnosticAssetReplaceEmptyException.of(DiagnosticAsset.VMLINUZ))
                .given(activationService).replace(eq(DiagnosticAsset.VMLINUZ), any(MultipartFile.class));
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "VMLINUZ").file(upload()))
                .andExpect(status().isBadRequest());
    }

    // ── 롤백(rollback) — E1-I-2-b-2 신규 ─────────────────────────────────────

    @Test
    @DisplayName("POST /{slot}/versions/{id}/rollback — 302 PRG + service 호출")
    void rollback_redirects() throws Exception {
        mvc.perform(post("/system/diagnostic-asset/{slot}/versions/{id}/rollback", "VMLINUZ", 3))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/diagnostic-asset/VMLINUZ"));
        verify(activationService).rollback(DiagnosticAsset.VMLINUZ, 3L);
    }

    @Test
    @DisplayName("롤백 — 없는 slot 이름 → 404 (컨트롤러 parseSlot)")
    void rollback_unknownSlot_notFound() throws Exception {
        mvc.perform(post("/system/diagnostic-asset/{slot}/versions/{id}/rollback", "NOPE", 1))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("롤백 — 없는/타슬롯 버전 → 404 (openVersion)")
    void rollback_versionNotFound_notFound() throws Exception {
        willThrow(AssetVersionNotFoundException.of(new AssetVersionKey("DIAGNOSTIC", "vmlinuz-lts"), 99L))
                .given(activationService).rollback(DiagnosticAsset.VMLINUZ, 99L);
        mvc.perform(post("/system/diagnostic-asset/{slot}/versions/{id}/rollback", "VMLINUZ", 99))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("롤백 — 비대상(apkovl) → 409")
    void rollback_notReplaceable_conflict() throws Exception {
        willThrow(DiagnosticAssetNotReplaceableException.of(DiagnosticAsset.APKOVL))
                .given(activationService).rollback(DiagnosticAsset.APKOVL, 1L);
        mvc.perform(post("/system/diagnostic-asset/{slot}/versions/{id}/rollback", "APKOVL", 1))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("롤백 — 서빙 비활성 → 409")
    void rollback_servingDisabled_conflict() throws Exception {
        willThrow(SystemAssetServingDisabledException.servingDisabled())
                .given(activationService).rollback(DiagnosticAsset.VMLINUZ, 1L);
        mvc.perform(post("/system/diagnostic-asset/{slot}/versions/{id}/rollback", "VMLINUZ", 1))
                .andExpect(status().isConflict());
    }
}
