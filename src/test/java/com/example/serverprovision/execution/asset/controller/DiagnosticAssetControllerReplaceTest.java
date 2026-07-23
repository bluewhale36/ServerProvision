package com.example.serverprovision.execution.asset.controller;

import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetNotReplaceableException;
import com.example.serverprovision.execution.asset.exception.DiagnosticAssetReplaceEmptyException;
import com.example.serverprovision.execution.asset.exception.SystemAssetServingDisabledException;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetIntegrityService;
import com.example.serverprovision.execution.asset.service.DiagnosticAssetReplaceService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1-I-2-a CP4 — 교체 엔드포인트의 HTTP 계층 검증. slot 파싱(404)은 컨트롤러 실경로이고, 409/400 은
 * Service 가 던지는 신규 예외의 advice 매핑이 실경로다(테스트 규율 — 신규 예외는 실트리거 시나리오 동반).
 */
@WebMvcTest(controllers = DiagnosticAssetController.class)
class DiagnosticAssetControllerReplaceTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DiagnosticAssetIntegrityService integrityService;

    @MockitoBean
    DiagnosticAssetReplaceService replaceService;

    @MockitoBean
    JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static MockMultipartFile upload() {
        return new MockMultipartFile("file", "vmlinuz-lts", "application/octet-stream", "new-bytes".getBytes());
    }

    @Test
    @DisplayName("POST /{slot}/replace — 302 PRG + service 호출")
    void replace_redirects() throws Exception {
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "VMLINUZ").file(upload()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/system/diagnostic-asset"));
        verify(replaceService).replace(eq(DiagnosticAsset.VMLINUZ), any(MultipartFile.class));
    }

    @Test
    @DisplayName("없는 slot 이름 → 404 (컨트롤러 parseSlot)")
    void replace_unknownSlot_notFound() throws Exception {
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "NOPE").file(upload()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비대상(apkovl) 교체 → 409")
    void replace_notReplaceable_conflict() throws Exception {
        willThrow(DiagnosticAssetNotReplaceableException.of(DiagnosticAsset.APKOVL))
                .given(replaceService).replace(eq(DiagnosticAsset.APKOVL), any(MultipartFile.class));
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "APKOVL").file(upload()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("서빙 비활성 교체 → 409")
    void replace_servingDisabled_conflict() throws Exception {
        willThrow(SystemAssetServingDisabledException.servingDisabled())
                .given(replaceService).replace(eq(DiagnosticAsset.VMLINUZ), any(MultipartFile.class));
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "VMLINUZ").file(upload()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("빈 파일 교체 → 400")
    void replace_emptyFile_badRequest() throws Exception {
        willThrow(DiagnosticAssetReplaceEmptyException.of(DiagnosticAsset.VMLINUZ))
                .given(replaceService).replace(eq(DiagnosticAsset.VMLINUZ), any(MultipartFile.class));
        mvc.perform(multipart("/system/diagnostic-asset/{slot}/replace", "VMLINUZ").file(upload()))
                .andExpect(status().isBadRequest());
    }
}
