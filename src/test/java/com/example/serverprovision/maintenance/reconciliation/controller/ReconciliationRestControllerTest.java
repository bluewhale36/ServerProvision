package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.example.serverprovision.maintenance.reconciliation.service.recheck.DriftRecheckService driftRecheckService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.example.serverprovision.maintenance.reconciliation.service.HashAcceptService hashAcceptService;

    // HF4-5 — resolve-duplicate endpoint 의존. 본 클래스는 기존 액션만 검증 — 전용 시나리오는
    // ReconciliationRestControllerDuplicateFlowTest 분리 파일이 담당.
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.example.serverprovision.maintenance.reconciliation.service.DuplicateResolveService duplicateResolveService;
    // R9-4 — ReconciliationController 의 격리 대기 배너용 의존.
    @MockitoBean com.example.serverprovision.global.orphan.service.OrphanQuarantineService orphanQuarantineService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private DriftReportResponse sampleReport() {
        DriftResponse drift = new DriftResponse(1L, ResourceType.OS_ISO, 42L, "Rocky Linux 9.6 dvd.iso",
                DriftKind.PATH_DRIFT, "/old/dvd.iso", "/new/dvd.iso", Instant.now(), null);
        // HF4-4 — detectedDriftCount(탐지 스냅샷) 1 · driftCount(미해결 잔수) 1
        return new DriftReportResponse(10L, Instant.now(), "0.45초", false, 17, 1, 1, List.of(), List.of(drift));
    }

    /**
     * HF4-4 — 탐지/미해결 병기 검증용 fixture. drifts 상세는 카운트 계약과 무관해 비운다.
     */
    private DriftReportResponse reportWithCounts(long id, int detectedDriftCount, int driftCount) {
        return new DriftReportResponse(id, Instant.now(), "0.45초", false, 17,
                detectedDriftCount, driftCount, List.of(), List.of());
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
                .andExpect(jsonPath("$.drifts[0].kind").value("PATH_DRIFT"))
                // R9-5 — 실명 스냅샷의 REST 계약 확장 고정.
                .andExpect(jsonPath("$.drifts[0].displayName").value("Rocky Linux 9.6 dvd.iso"));
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

    // ==== HF4-4 — 탐지 스냅샷 · 미해결 잔수 병기 =========================

    @Test
    @DisplayName("HF4-4 GET /history : 탐지/미해결 병기 값 노출 — 신행(4·1) / 구행 대체 적용 결과(3·3)")
    void history_exposesDetectedAndUnresolvedCounts() throws Exception {
        // 신행: 스캔 시점 탐지 4건 중 3건 해결됨. 구행: 스냅샷 0 을 서비스 매핑이 미해결 수(3)로 대체한 결과.
        DriftReportResponse fresh = reportWithCounts(10L, 4, 1);
        DriftReportResponse legacy = reportWithCounts(9L, 3, 3);
        given(reconciliationService.history(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(List.of(fresh, legacy)));

        mvc.perform(get("/maintenance/reconciliation/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].detectedDriftCount").value(4))
                .andExpect(jsonPath("$.content[0].driftCount").value(1))
                .andExpect(jsonPath("$.content[1].detectedDriftCount").value(3))
                .andExpect(jsonPath("$.content[1].driftCount").value(3));
    }

    @Test
    @DisplayName("HF4-4 GET list : C1 배지 '탐지 N · 미해결 M' 병기 + 스캔 범위(extra-roots) 안내 문구 렌더")
    void list_rendersDetectedUnresolvedBadgeAndScanScopeGuidance() throws Exception {
        given(reconciliationService.history(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(List.of(reportWithCounts(10L, 4, 1))));

        mvc.perform(get("/maintenance/reconciliation"))
                .andExpect(status().isOk())
                // 배지 병기 — 템플릿이 DTO 신설 필드를 실제 참조하는지(누락 시 500) 함께 고정
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("탐지 4 · 미해결 1")))
                // O-1 — 상단 배너의 스캔 범위 보강 안내
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("reconciliation.scan.extra-roots")));
    }

    @Test
    @DisplayName("HF4-4 GET list : 탐지 0 보고서는 종전 '0 건' 표기 유지 (병기 불필요 경계)")
    void list_rendersZeroBadgeWhenNothingDetected() throws Exception {
        given(reconciliationService.history(org.mockito.ArgumentMatchers.any()))
                .willReturn(new PageImpl<>(List.of(reportWithCounts(10L, 0, 0))));

        mvc.perform(get("/maintenance/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("0 건")));
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

    // ==== R9-1 — Miller URL 동기화 alias (selectKey/selectId) ============

    @Test
    @DisplayName("GET list : selectKey/selectId alias 가 selectReportId/selectDriftId 모델로 매핑 (reload 위치 보존)")
    void list_acceptsMillerSyncAliases() throws Exception {
        Page<DriftReportResponse> page = new PageImpl<>(List.of(sampleReport()));
        given(reconciliationService.history(org.mockito.ArgumentMatchers.any())).willReturn(page);

        mvc.perform(get("/maintenance/reconciliation?selectKey=10&selectId=1"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .model().attribute("selectReportId", 10L))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .model().attribute("selectDriftId", 1L));
    }

    @Test
    @DisplayName("GET list : 명시 파라미터(selectReportId/selectDriftId)가 alias 보다 우선")
    void list_explicitParamsWinOverAliases() throws Exception {
        Page<DriftReportResponse> page = new PageImpl<>(List.of(sampleReport()));
        given(reconciliationService.history(org.mockito.ArgumentMatchers.any())).willReturn(page);

        mvc.perform(get("/maintenance/reconciliation?selectReportId=10&selectDriftId=1&selectKey=99&selectId=88"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .model().attribute("selectReportId", 10L))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .model().attribute("selectDriftId", 1L));
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
    @DisplayName("POST /drifts/{id}/apply : 자동 적용 불가 종류 → 409")
    void apply_notAllowedKind() throws Exception {
        doThrow(DriftResolutionNotAllowedException.notApplicable(DriftKind.SIGNATURE_INVALID))
                .when(reconciliationService).apply(1L);

        mvc.perform(post("/maintenance/reconciliation/drifts/1/apply"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("S6-2-2 POST /drifts/{id}/apply : 휴지통 기존 사본 충돌로 회수 거절 → 409")
    void apply_trashCopyConflict() throws Exception {
        doThrow(DriftResolutionNotAllowedException.trashCopyConflict("/opt/.soft-deleted/iso/42/dvd_x.iso"))
                .when(reconciliationService).apply(1L);

        mvc.perform(post("/maintenance/reconciliation/drifts/1/apply"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("S6-3-3 POST /drifts/{id}/recheck : 해소/잔존이 JSON resolved 로 구분")
    void recheck_returnsResolvedFlag() throws Exception {
        given(driftRecheckService.recheck(1L)).willReturn(true);
        mvc.perform(post("/maintenance/reconciliation/drifts/1/recheck"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.resolved").value(true));

        given(driftRecheckService.recheck(2L)).willReturn(false);
        mvc.perform(post("/maintenance/reconciliation/drifts/2/recheck"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.resolved").value(false));
    }

    @Test
    @DisplayName("S6-3-4 POST /drifts/{id}/accept-hash : 자원명 확인 통과 → 200 + jobId")
    void acceptHash_startsJob() throws Exception {
        given(hashAcceptService.triggerAccept(1L, "Rocky Linux 9.6 dvd.iso")).willReturn("job-77");

        mvc.perform(post("/maintenance/reconciliation/drifts/1/accept-hash")
                        .param("typedName", "Rocky Linux 9.6 dvd.iso"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.jobId").value("job-77"));
    }

    @Test
    @DisplayName("S6-3-4 POST /drifts/{id}/accept-hash : 스냅샷 부재·상태 변화 → 409")
    void acceptHash_rejectsStale() throws Exception {
        doThrow(DriftResolutionNotAllowedException.staleState())
                .when(hashAcceptService).triggerAccept(2L, "x");

        mvc.perform(post("/maintenance/reconciliation/drifts/2/accept-hash").param("typedName", "x"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("S6-3-3 POST /drifts/{id}/recheck : 미지원 종류(direct POST 안전망) → 409")
    void recheck_notApplicable() throws Exception {
        doThrow(DriftResolutionNotAllowedException.notApplicable(DriftKind.PATH_DRIFT))
                .when(driftRecheckService).recheck(3L);
        mvc.perform(post("/maintenance/reconciliation/drifts/3/recheck"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("S6-2-3 POST /drifts/{id}/apply : 보고 시점과 상태가 달라짐(stale) → 409")
    void apply_staleState() throws Exception {
        doThrow(DriftResolutionNotAllowedException.staleState())
                .when(reconciliationService).apply(1L);

        mvc.perform(post("/maintenance/reconciliation/drifts/1/apply"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /drifts/{id}/apply : 전역 auto-apply OFF (direct POST 안전망) → 409")
    void apply_globalOff() throws Exception {
        doThrow(DriftResolutionNotAllowedException.globalOff())
                .when(reconciliationService).apply(1L);

        mvc.perform(post("/maintenance/reconciliation/drifts/1/apply"))
                .andExpect(status().isConflict());
    }

    // ==== R9-2 — 전역 auto-apply 뷰모델 플래그 =============================

    @Test
    @DisplayName("R9-4 GET list : 격리 대기 건수가 모델로 노출 (안내 배너 데이터 소스)")
    void list_exposesQuarantinePendingCount() throws Exception {
        Page<DriftReportResponse> page = new PageImpl<>(List.of(sampleReport()));
        given(reconciliationService.history(org.mockito.ArgumentMatchers.any())).willReturn(page);
        given(orphanQuarantineService.countPending()).willReturn(3L);

        mvc.perform(get("/maintenance/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .model().attribute("quarantinePendingCount", 3L));
    }

    @Test
    @DisplayName("GET list : resolutionEnabled 플래그가 모델로 노출 (UI 1차 차단의 데이터 소스)")
    void list_exposesAutoApplyEnabledFlag() throws Exception {
        Page<DriftReportResponse> page = new PageImpl<>(List.of(sampleReport()));
        given(reconciliationService.history(org.mockito.ArgumentMatchers.any())).willReturn(page);
        given(reconciliationService.isResolutionEnabled()).willReturn(false);

        mvc.perform(get("/maintenance/reconciliation"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .model().attribute("resolutionEnabled", false));
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
