package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.ProvisioningMarkFailedRejectedException;
import com.example.serverprovision.execution.exception.ProvisioningRetryRejectedException;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1-2 CP4 — 운영자 액션 2종(수동 실패 전환 · 재시도, DEC-4)의 HTTP 계층 검증. Mocking 은 Service 까지 —
 * 신규 예외({@code ProvisioningMarkFailedRejectedException}/{@code ProvisioningRetryRejectedException})의
 * 409 매핑과 404 forging 이 실경로다(테스트 규율 — 신규 예외는 실트리거 시나리오 동반).
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerActionTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // ==== 성공 (PRG) ==================================================

    @Test
    @DisplayName("POST /{id}/mark-failed — 302 PRG (무보고 침묵의 운영자 판단 실패 처리)")
    void markFailed_redirects() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/provisioning/server/{id}/mark-failed", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provisioning/server/" + id));
        verify(commandService).markFailedManually(id);
    }

    @Test
    @DisplayName("POST /{id}/retry — 302 PRG (실패 신호 해제 후 커서 유지 재개)")
    void retry_redirects() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/provisioning/server/{id}/retry", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provisioning/server/" + id));
        verify(commandService).retry(id);
    }

    // ==== 409 — UI 1차 차단을 우회한 direct POST 안전망 =================

    @Test
    @DisplayName("mark-failed — 진행 중 아님(미개시·완주·회수) → 409")
    void markFailed_notProvisioning_conflict() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(ProvisioningMarkFailedRejectedException.notProvisioning(id))
                .given(commandService).markFailedManually(id);
        mvc.perform(post("/provisioning/server/{id}/mark-failed", id))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("retry — 실패 상태 아님 → 409")
    void retry_notFailed_conflict() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(ProvisioningRetryRejectedException.notFailed(id))
                .given(commandService).retry(id);
        mvc.perform(post("/provisioning/server/{id}/retry", id))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("retry — 펌웨어 flash 실패 차단(retryBlocked SSOT — 벽돌 리스크) → 409")
    void retry_firmwareBlocked_conflict() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(ProvisioningRetryRejectedException.firmwareBlocked(id, ProvisioningPhaseStep.BIOS_UPDATING))
                .given(commandService).retry(id);
        mvc.perform(post("/provisioning/server/{id}/retry", id))
                .andExpect(status().isConflict());
    }

    // ==== 404 ========================================================

    @Test
    @DisplayName("없는 서버 id — mark-failed·retry 모두 404")
    void unknownServer_notFound() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new GuestServerNotFoundException(id)).given(commandService).markFailedManually(id);
        willThrow(new GuestServerNotFoundException(id)).given(commandService).retry(id);

        mvc.perform(post("/provisioning/server/{id}/mark-failed", id)).andExpect(status().isNotFound());
        mvc.perform(post("/provisioning/server/{id}/retry", id)).andExpect(status().isNotFound());
    }
}
