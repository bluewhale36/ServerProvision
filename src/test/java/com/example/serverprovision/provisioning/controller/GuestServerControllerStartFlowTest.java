package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.exception.ProvisioningStartRejectedException;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E1-0a CP4 — 프로비저닝 개시 플로우 통합 테스트 (DEC-26). Mocking 은 Service 단까지 —
 * plain form PRG 컨트롤러 + {@code WebExceptionHandler} 의 404/409 매핑이 실제로 실행된다.
 * 상세 렌더 3분기(미개시 버튼 노출 / 개시됨 비노출 / 회수 비노출)는 startable 플래그가
 * 서버 가드와 같은 SSOT 에서 왔음을 바디로 검증한다.
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerStartFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 12, 12, 0);

    /** 신호 상태만 다른 상세 fixture — progress 3분기(미개시 / 개시됨 / 회수됨)를 만든다. */
    private GuestServerDetailResponse detail(
            UUID id, GuestServerStatus status, LocalDateTime decommissionedAt,
            LocalDateTime startedAt, boolean startable) {
        return new GuestServerDetailResponse(
                id, "web-01", null, null, UUID.randomUUID(), null,
                status, decommissionedAt, T, T,
                null, List.of(),
                new GuestServerDetailResponse.Progress(
                        ProvisioningPhase.BOOTSTRAPPING, T, null, startedAt, null, null, null, startable),
                List.of());
    }

    private String startActionUrl(UUID id) {
        return "/provisioning/server/" + id + "/start";
    }

    // ==== 성공 ========================================================

    @Test
    @DisplayName("POST /{id}/start — 개시 성공 302 redirect + service 호출")
    void start_success_redirects() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/provisioning/server/{id}/start", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server/" + id));

        verify(commandService).startProvisioning(id);
    }

    // ==== 404 / 409 (안전망 — 신규 예외 실트리거) ======================

    @Test
    @DisplayName("POST /{id}/start — 없는 id → 404 (advice)")
    void start_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new GuestServerNotFoundException(id)).given(commandService).startProvisioning(id);

        mvc.perform(post("/provisioning/server/{id}/start", id).accept(MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{id}/start — 이미 개시된 서버(direct POST) → 409 (ProvisioningStartRejected)")
    void start_alreadyStarted_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(ProvisioningStartRejectedException.alreadyStarted(id))
                .given(commandService).startProvisioning(id);

        mvc.perform(post("/provisioning/server/{id}/start", id).accept(MediaType.TEXT_HTML))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /{id}/start — 회수된 서버(direct POST) → 409 (ProvisioningStartRejected)")
    void start_decommissioned_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(ProvisioningStartRejectedException.decommissioned(id))
                .given(commandService).startProvisioning(id);

        mvc.perform(post("/provisioning/server/{id}/start", id).accept(MediaType.TEXT_HTML))
                .andExpect(status().isConflict());
    }

    // ==== 상세 렌더 3분기 — UI 1차 차단(startable SSOT) ================

    @Test
    @DisplayName("상세 렌더 — 미개시 서버: 개시 버튼 폼 노출 + '개시 전' 표시")
    void detail_notStarted_showsStartButton() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(
                detail(id, GuestServerStatus.REGISTERED, null, null, true));

        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(startActionUrl(id))))
                .andExpect(content().string(containsString("— (개시 전)")));
    }

    @Test
    @DisplayName("상세 렌더 — 개시된 서버: 버튼 비노출 + 개시 시각 표시")
    void detail_started_hidesStartButton() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(
                detail(id, GuestServerStatus.PROVISIONING, null, T, false));

        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(startActionUrl(id)))))
                .andExpect(content().string(containsString("2026-07-12 12:00:00")));
    }

    @Test
    @DisplayName("상세 렌더 — 회수된 서버: 버튼 비노출 (UI 1차 차단 — 409 는 direct POST 전용)")
    void detail_decommissioned_hidesStartButton() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(
                detail(id, GuestServerStatus.DECOMMISSIONED, T, null, false));

        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(startActionUrl(id)))));
    }
}
