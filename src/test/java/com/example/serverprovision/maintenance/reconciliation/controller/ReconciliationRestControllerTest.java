package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftAutoApplyNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.ReconciliationAlreadyRunningException;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MK1 ReconciliationRestController 통합 테스트.
 * 사용자 액션 4 종 (scan, latest, history, drifts/{id}/{apply,dismiss}) 의 4 범주 (성공·400·404·409) 커버리지.
 */
@WebMvcTest(controllers = {ReconciliationRestController.class, ReconciliationController.class})
class ReconciliationRestControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean PathReconciliationService reconciliationService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private DriftReportResponse sampleReport() {
        DriftResponse drift = new DriftResponse(1L, ResourceType.OS_ISO, 42L,
                DriftKind.PATH_DRIFT, "/old/dvd.iso", "/new/dvd.iso", Instant.now(), null);
        return new DriftReportResponse(10L, Instant.now(), "PT0.45S", false, 17, 1, List.of(), List.of(drift));
    }

    // ==== 성공 경로 ====================================================

    @Test
    @DisplayName("POST /scan : 200 + jobId 반환")
    void scan_success() throws Exception {
        given(reconciliationService.triggerScan(false)).willReturn("job-abc");

        mvc.perform(post("/maintenance/reconciliation/scan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-abc"));
    }

    @Test
    @DisplayName("GET /latest : 보고서 있음 → 200 + 응답 바디")
    void latest_present() throws Exception {
        given(reconciliationService.latestReport()).willReturn(Optional.of(sampleReport()));

        mvc.perform(get("/maintenance/reconciliation/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.driftCount").value(1))
                .andExpect(jsonPath("$.drifts[0].kind").value("PATH_DRIFT"));
    }

    @Test
    @DisplayName("GET /history : 페이지네이션 응답")
    void history_paged() throws Exception {
        Page<DriftReportResponse> page = new PageImpl<>(List.of(sampleReport()));
        given(reconciliationService.history(org.mockito.ArgumentMatchers.any())).willReturn(page);

        mvc.perform(get("/maintenance/reconciliation/history?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10));
    }

    @Test
    @DisplayName("POST /drifts/{id}/apply : 200 + 페이지로 redirect")
    void apply_success() throws Exception {
        mvc.perform(post("/maintenance/reconciliation/drifts/1/apply"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maintenance/reconciliation"));
    }

    @Test
    @DisplayName("POST /drifts/{id}/dismiss : 200 + 페이지로 redirect")
    void dismiss_success() throws Exception {
        mvc.perform(post("/maintenance/reconciliation/drifts/1/dismiss"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maintenance/reconciliation"));
    }

    // ==== 리소스 없음 ===================================================

    @Test
    @DisplayName("GET /latest : 한번도 스캔 없음 → 204")
    void latest_noContent() throws Exception {
        given(reconciliationService.latestReport()).willReturn(Optional.empty());

        mvc.perform(get("/maintenance/reconciliation/latest"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("POST /drifts/{id}/apply : 존재하지 않는 driftId → 404")
    void apply_notFound() throws Exception {
        doThrow(new DriftNotFoundException(999L)).when(reconciliationService).apply(999L);

        mvc.perform(post("/maintenance/reconciliation/drifts/999/apply"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /drifts/{id}/dismiss : 존재하지 않는 driftId → 404")
    void dismiss_notFound() throws Exception {
        doThrow(new DriftNotFoundException(999L)).when(reconciliationService).dismiss(999L);

        mvc.perform(post("/maintenance/reconciliation/drifts/999/dismiss"))
                .andExpect(status().isNotFound());
    }

    // ==== 도메인 충돌 ===================================================

    @Test
    @DisplayName("POST /drifts/{id}/apply : PATH_DRIFT 외 종류 → 409")
    void apply_notAllowedKind() throws Exception {
        doThrow(new DriftAutoApplyNotAllowedException(DriftKind.SIGNATURE_INVALID))
                .when(reconciliationService).apply(1L);

        mvc.perform(post("/maintenance/reconciliation/drifts/1/apply"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /scan : 동시 스캔 → 409")
    void scan_alreadyRunning() throws Exception {
        given(reconciliationService.triggerScan(anyBoolean()))
                .willThrow(new ReconciliationAlreadyRunningException());

        mvc.perform(post("/maintenance/reconciliation/scan"))
                .andExpect(status().isConflict());
    }

    // ==== Deep scan 옵션 =================================================

    @Test
    @DisplayName("POST /scan?deep=true : deep=true 인자로 호출")
    void scan_deep() throws Exception {
        given(reconciliationService.triggerScan(true)).willReturn("job-deep");

        mvc.perform(post("/maintenance/reconciliation/scan").param("deep", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-deep"));
    }
}
