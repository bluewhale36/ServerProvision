package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.exception.GuestServerNotDecommissionedException;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.global.exception.TypedNameMismatchException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U6 CP4 — 회수 서버 영구 삭제 플로우 통합 테스트. Mocking 은 Service 단까지 — 검증(@Valid) ·
 * 예외 → HTTP 매핑(400 · 409 · 404)이 실제로 실행된다. 성공은 목록으로의 PRG 를 검증한다
 * (보던 상세가 사라지는 액션 — 화면 JS 가 redirect 도착지를 따라간다).
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerPurgeFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean AssignmentCommandService assignmentCommandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean AssignmentStartService assignmentStartService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean GuestServerGroupQueryService groupQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID ID = UUID.randomUUID();

    @Test
    @DisplayName("성공 — 삭제 후 목록으로 PRG (302 /provisioning/server)")
    void purge_success_redirectsToList() throws Exception {
        mvc.perform(post("/provisioning/server/{id}/purge", ID).param("typedSuffix", "b7c04f464331"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server"));

        verify(commandService).purge(ID, "b7c04f464331");
    }

    @Test
    @DisplayName("400 — 빈 suffix 는 Bean Validation 이 끊는다 (서비스 미도달)")
    void purge_blankSuffix_badRequest() throws Exception {
        mvc.perform(post("/provisioning/server/{id}/purge", ID).param("typedSuffix", " "))
                .andExpect(status().isBadRequest());

        verify(commandService, never()).purge(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("400 — suffix 값 불일치 (TypedNameMismatchException 재사용 매핑)")
    void purge_suffixMismatch_badRequest() throws Exception {
        willThrow(new TypedNameMismatchException("b7c04f464331", "wrong"))
                .given(commandService).purge(any(UUID.class), anyString());

        mvc.perform(post("/provisioning/server/{id}/purge", ID).param("typedSuffix", "wrong"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("409 — 회수되지 않은 서버 (direct POST 안전망)")
    void purge_notDecommissioned_conflict() throws Exception {
        willThrow(new GuestServerNotDecommissionedException(ID))
                .given(commandService).purge(any(UUID.class), anyString());

        mvc.perform(post("/provisioning/server/{id}/purge", ID).param("typedSuffix", "b7c04f464331"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("404 — 없는 서버")
    void purge_missing_notFound() throws Exception {
        willThrow(new GuestServerNotFoundException(ID))
                .given(commandService).purge(any(UUID.class), anyString());

        mvc.perform(post("/provisioning/server/{id}/purge", ID).param("typedSuffix", "b7c04f464331"))
                .andExpect(status().isNotFound());
    }
}
