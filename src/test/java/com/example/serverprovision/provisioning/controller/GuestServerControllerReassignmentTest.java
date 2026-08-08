package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.provisioning.assignment.dto.response.ReassignmentResponse;
import com.example.serverprovision.provisioning.assignment.exception.NoActiveAssignmentToReassignException;
import com.example.serverprovision.provisioning.assignment.exception.ReassignAfterStartException;
import com.example.serverprovision.provisioning.assignment.service.AssignmentCommandService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentStartService;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U3-2-a CP4 — 세팅 정의서 재할당 엔드포인트(XHR JSON)의 HTTP 계층 검증. Mocking 은 Service 단까지 —
 * 컨트롤러 {@code @ResponseBody} + advice(ApiExceptionHandler) 의 2xx/400/404/409 매핑이 실경로다.
 * 신규 예외 두 종({@code ReassignAfterStartException} · {@code NoActiveAssignmentToReassignException}) 을
 * 409 시나리오가 실트리거한다(테스트 규율 — 새 예외는 발생 시나리오 통합 테스트 동반).
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerReassignmentTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean AssignmentCommandService assignmentCommandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean AssignmentStartService assignmentStartService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final Long DEF_ID = 5L;

    private String url(UUID id) {
        return "/provisioning/server/" + id + "/assignment/reassign";
    }

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("POST /assignment/reassign — 재할당 성공 200 + 새 활성 · 이전 supersede 바디")
    void reassign_success_returnsNewAndSuperseded() throws Exception {
        UUID id = UUID.randomUUID();
        given(assignmentCommandService.reassign(eq(id), eq(DEF_ID))).willReturn(
                new ReassignmentResponse(2L, 1L, DEF_ID, "web-standard",
                        List.of(ProvisioningPhase.FIRMWARE_SETTING, ProvisioningPhase.OS_INSTALLING)));

        mvc.perform(post(url(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentId").value(2))
                .andExpect(jsonPath("$.supersededAssignmentId").value(1))
                .andExpect(jsonPath("$.definitionName").value("web-standard"))
                .andExpect(jsonPath("$.ownedPhases[0]").value("FIRMWARE_SETTING"))
                .andExpect(jsonPath("$.ownedPhases[1]").value("OS_INSTALLING"));
    }

    // ==== 400 — Bean Validation ======================================

    @Test
    @DisplayName("POST /assignment/reassign — definitionId 누락 → 400 + 필드 에러")
    void reassign_missingDefinitionId_returns400() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post(url(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("definitionId"));
    }

    // ==== 404 ========================================================

    @Test
    @DisplayName("POST /assignment/reassign — 없는 게스트 → 404")
    void reassign_guestNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(assignmentCommandService.reassign(eq(id), eq(DEF_ID)))
                .willThrow(new GuestServerNotFoundException(id));

        mvc.perform(post(url(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /assignment/reassign — 없는 정의서(forging 포함) → 404")
    void reassign_definitionNotFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(assignmentCommandService.reassign(eq(id), eq(DEF_ID)))
                .willThrow(new SettingNotFoundException(DEF_ID));

        mvc.perform(post(url(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isNotFound());
    }

    // ==== 409 — 신규 예외 실트리거 ===================================

    @Test
    @DisplayName("POST /assignment/reassign — 개시 후 재할당(direct POST) → 409 (ReassignAfterStartException)")
    void reassign_afterStart_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        given(assignmentCommandService.reassign(eq(id), eq(DEF_ID)))
                .willThrow(new ReassignAfterStartException(id, "이미 개시되어 재할당할 수 없습니다(회수 후 재등록 필요)"));

        mvc.perform(post(url(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /assignment/reassign — 갈아끼울 활성 없음(stale) → 409 (NoActiveAssignmentToReassignException)")
    void reassign_noActive_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        given(assignmentCommandService.reassign(eq(id), eq(DEF_ID)))
                .willThrow(new NoActiveAssignmentToReassignException(id));

        mvc.perform(post(url(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isConflict());
    }
}
