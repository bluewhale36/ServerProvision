package com.example.serverprovision.maintenance.reconciliation.controller;

import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S3.4 (Silent-500-A) Reconciliation 컨트롤러 보안 통합.
 *
 * <p>본 컨트롤러군은 try/catch 가 없어 silent-500 이 발생하지 않지만, ApiExceptionHandler 가 보안 예외를
 * 분류된 status 로 매핑하는지 사용자 액션 시나리오로 회귀 차단한다.</p>
 */
@WebMvcTest(controllers = {ReconciliationController.class, ReconciliationRestController.class})
class ReconciliationControllerSecurityFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean PathReconciliationService reconciliationService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.example.serverprovision.maintenance.reconciliation.service.recheck.DriftRecheckService driftRecheckService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.example.serverprovision.maintenance.reconciliation.service.HashAcceptService hashAcceptService;
    // R9-4 — ReconciliationController 의 격리 대기 배너용 의존.
    @MockitoBean com.example.serverprovision.global.orphan.service.OrphanQuarantineService orphanQuarantineService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("apply : PathOutsideAllowedRootsException → 403 (auto-propagation 검증)")
    void apply_pathOutside_returns403() throws Exception {
        willThrow(new PathOutsideAllowedRootsException())
                .given(reconciliationService).apply(1L);

        mvc.perform(post("/maintenance/reconciliation/drifts/1/apply"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("apply : DriftNotFoundException → 404 (NotFoundException 매핑)")
    void apply_driftNotFound_returns404() throws Exception {
        willThrow(new DriftNotFoundException(99L))
                .given(reconciliationService).apply(99L);

        mvc.perform(post("/maintenance/reconciliation/drifts/99/apply"))
                .andExpect(status().isNotFound());
    }

}
