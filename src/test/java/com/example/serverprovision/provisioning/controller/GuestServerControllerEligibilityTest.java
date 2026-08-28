package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.provisioning.assignment.exception.DefinitionHardwareMismatchException;
import com.example.serverprovision.provisioning.assignment.exception.ServerNotAssignableException;
import com.example.serverprovision.provisioning.assignment.service.AssignmentCommandService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentStartService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U3-5-a CP4 — 할당 가능성 가드의 HTTP 계층 검증.
 *
 * <p>신규 예외 둘({@link ServerNotAssignableException} · {@link DefinitionHardwareMismatchException})이
 * <b>advice 를 실제로 통과해</b> 409 로 나가는지를 본다. 둘 다 advice 에 명시 등록하지 않았고 상위 타입
 * 핸들러가 {@code @ResponseStatus} 를 계층 탐색으로 읽어 흡수하도록 두었으므로, 그 가정이 맞는지를
 * 확인하는 것이 이 테스트의 존재 이유다 — 과거 {@code MissingFilenameException} 이 500 으로 새던 사고가
 * 이 규율의 출처다.</p>
 *
 * <p>Mocking 은 Service 단까지다. 컨트롤러의 {@code @ResponseBody} 와 advice 매핑은 실경로로 실행된다.</p>
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerEligibilityTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean AssignmentCommandService assignmentCommandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean AssignmentStartService assignmentStartService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean GuestServerGroupQueryService groupQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final Long DEF_ID = 5L;
    private static final String BOARD_MISMATCH =
            "이 정의서는 메인보드 MS03-CE0 전용입니다 — 이 서버는 X11SPM 입니다.";
    private static final String DECOMMISSIONED = "회수된 서버에는 세팅 정의서를 할당할 수 없습니다.";

    private String assignUrl(UUID id) {
        return "/provisioning/server/" + id + "/assignment";
    }

    private String reassignUrl(UUID id) {
        return "/provisioning/server/" + id + "/assignment/reassign";
    }

    // ==== 409 — 회수된 서버 ============================================

    @Test
    @DisplayName("POST /assignment — 회수된 서버 direct POST → 409 + 사유")
    void assign_decommissionedServer_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        given(assignmentCommandService.assign(eq(id), eq(DEF_ID)))
                .willThrow(new ServerNotAssignableException(id, DECOMMISSIONED));

        mvc.perform(post(assignUrl(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(DECOMMISSIONED));
    }

    @Test
    @DisplayName("POST /assignment/reassign — 회수된 서버 direct POST → 409")
    void reassign_decommissionedServer_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        given(assignmentCommandService.reassign(eq(id), eq(DEF_ID)))
                .willThrow(new ServerNotAssignableException(id, DECOMMISSIONED));

        mvc.perform(post(reassignUrl(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isConflict());
    }

    // ==== 409 — 하드웨어 불일치 ========================================

    @Test
    @DisplayName("POST /assignment — 잠긴 선택지를 직접 실어 보내면 409 + 요구 보드와 실제 보드를 함께 알린다")
    void assign_hardwareMismatch_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        given(assignmentCommandService.assign(eq(id), eq(DEF_ID)))
                .willThrow(new DefinitionHardwareMismatchException(id, BOARD_MISMATCH));

        mvc.perform(post(assignUrl(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(BOARD_MISMATCH));
    }

    @Test
    @DisplayName("POST /assignment/reassign — 하드웨어 불일치 direct POST → 409")
    void reassign_hardwareMismatch_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        given(assignmentCommandService.reassign(eq(id), eq(DEF_ID)))
                .willThrow(new DefinitionHardwareMismatchException(id, BOARD_MISMATCH));

        mvc.perform(post(reassignUrl(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isConflict());
    }

    // ==== 409 — BIOS 템플릿 드리프트(E3-3 신규 예외 시나리오) ==========================

    @Test
    @DisplayName("POST /assignment — 레지스트리와 어긋난 템플릿의 정의서를 직접 실어 보내면 409 + 어긋난 속성을 알린다")
    void assign_templateStale_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        String reason = "BIOS 템플릿 'MD72-HB3 공장 표준 세팅' 의 값이 보드 레지스트리(F44 · 2026-08-27 채집 · 192.168.1.130)와 어긋납니다 — Whitley0000 = Disabled — 허용 {Disable, Enable}";
        given(assignmentCommandService.assign(eq(id), eq(DEF_ID)))
                .willThrow(new com.example.serverprovision.provisioning.assignment.exception.BiosTemplateStaleException(id, reason));

        mvc.perform(post("/provisioning/server/{id}/assignment", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("{\"definitionId\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(reason));
    }
}
