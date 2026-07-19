package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.service.DuplicateResolveService;
import com.example.serverprovision.maintenance.reconciliation.service.DuplicateSurvivor;
import com.example.serverprovision.maintenance.reconciliation.service.HashAcceptService;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import com.example.serverprovision.maintenance.reconciliation.service.recheck.DriftRecheckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HF4-5 — [자원 중복 존재] 택일 해소 endpoint 의 사용자 액션 통합 테스트 (컨트롤러 단위 파일 분리 관례).
 * 4 범주 : 성공 2xx(양 갈래) / 400(잘못·누락 survivor — framework enum 바인딩) /
 * 404(DriftNotFound) / 409(모든 ConflictException 하위 실제 트리거 — 신규 factory 동반 시나리오 포함).
 */
@WebMvcTest(controllers = ReconciliationRestController.class)
class ReconciliationRestControllerDuplicateFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean PathReconciliationService reconciliationService;
    @MockitoBean DriftRecheckService driftRecheckService;
    @MockitoBean HashAcceptService hashAcceptService;
    @MockitoBean DuplicateResolveService duplicateResolveService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String URL = "/maintenance/reconciliation/drifts/1/resolve-duplicate";

    // ==== 성공 (양 갈래) ==================================================

    @Test
    @DisplayName("POST resolve-duplicate?survivor=ORIGINAL : 302 + 페이지 redirect + 서비스 위임")
    void resolve_keepOriginal() throws Exception {
        mvc.perform(post(URL).param("survivor", "ORIGINAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maintenance/reconciliation"));

        verify(duplicateResolveService).resolve(1L, DuplicateSurvivor.ORIGINAL);
    }

    @Test
    @DisplayName("POST resolve-duplicate?survivor=DUPLICATE : 302 + 서비스가 승격 갈래로 위임받음")
    void resolve_promoteDuplicate() throws Exception {
        mvc.perform(post(URL).param("survivor", "DUPLICATE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/maintenance/reconciliation"));

        verify(duplicateResolveService).resolve(1L, DuplicateSurvivor.DUPLICATE);
    }

    // ==== 400 — survivor 검증은 framework enum 바인딩이 담당 ================

    @Test
    @DisplayName("POST resolve-duplicate?survivor=BOGUS : enum 변환 실패 → 400")
    void resolve_invalidSurvivor() throws Exception {
        mvc.perform(post(URL).param("survivor", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST resolve-duplicate (survivor 누락) : 400")
    void resolve_missingSurvivor() throws Exception {
        mvc.perform(post(URL))
                .andExpect(status().isBadRequest());
    }

    // ==== 404 ==========================================================

    @Test
    @DisplayName("POST resolve-duplicate : 존재하지 않는 driftId → 404")
    void resolve_notFound() throws Exception {
        doThrow(new DriftNotFoundException(1L))
                .when(duplicateResolveService).resolve(1L, DuplicateSurvivor.ORIGINAL);

        mvc.perform(post(URL).param("survivor", "ORIGINAL"))
                .andExpect(status().isNotFound());
    }

    // ==== 409 — ConflictException 하위 실제 트리거 전수 ======================

    @Test
    @DisplayName("POST resolve-duplicate : 다른 kind 의 direct POST (notApplicable) → 409")
    void resolve_notApplicableKind() throws Exception {
        doThrow(DriftResolutionNotAllowedException.notApplicable(DriftKind.PATH_DRIFT))
                .when(duplicateResolveService).resolve(1L, DuplicateSurvivor.ORIGINAL);

        mvc.perform(post(URL).param("survivor", "ORIGINAL"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST resolve-duplicate : 보고 시점과 상태가 달라짐 (staleState) → 409")
    void resolve_staleState() throws Exception {
        doThrow(DriftResolutionNotAllowedException.staleState())
                .when(duplicateResolveService).resolve(1L, DuplicateSurvivor.ORIGINAL);

        mvc.perform(post(URL).param("survivor", "ORIGINAL"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("HF4-5 신규 factory : 낡은 사본 승격 거절 (duplicateNotPromotable) → 409 — 동반 시나리오 규율")
    void resolve_duplicateNotPromotable() throws Exception {
        doThrow(DriftResolutionNotAllowedException.duplicateNotPromotable(
                "복제본의 내용 지문이 현재 정본 기록과 다릅니다"))
                .when(duplicateResolveService).resolve(1L, DuplicateSurvivor.DUPLICATE);

        mvc.perform(post(URL).param("survivor", "DUPLICATE"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST resolve-duplicate : 전역 resolution-enabled OFF (globalOff) → 409")
    void resolve_globalOff() throws Exception {
        doThrow(DriftResolutionNotAllowedException.globalOff())
                .when(duplicateResolveService).resolve(1L, DuplicateSurvivor.ORIGINAL);

        mvc.perform(post(URL).param("survivor", "ORIGINAL"))
                .andExpect(status().isConflict());
    }
}
