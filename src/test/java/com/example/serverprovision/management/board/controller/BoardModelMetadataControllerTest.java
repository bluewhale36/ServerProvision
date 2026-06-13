package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.global.lifecycle.LifecycleStage;
import com.example.serverprovision.management.board.dto.response.BoardModelResponse;
import com.example.serverprovision.management.board.dto.response.VendorGroupResponse;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.BoardModelNudgeRequiredException;
import com.example.serverprovision.management.board.service.BoardModelService;
import com.example.serverprovision.management.common.nudge.dto.NudgeRequiredResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * R3-2 — {@link BoardModelMetadataController} 통합 테스트 (메타 CRUD : list / new / create / edit / update).
 *
 * <p>fat {@code BoardModelController} 3분할 후의 회귀 안전망 — 분할로 advice 매핑 / HTTP status 가
 * 깨지지 않았음을 보장한다. 순수 리팩토링이므로 새 HTTP 행동은 없다.</p>
 *
 * <p>Mocking 은 {@link BoardModelService} 단까지만. controller 의 redirect / view 선택 +
 * {@code @ControllerAdvice} (Web / Api) 의 예외 → status 매핑은 실제로 실행된다.</p>
 *
 * <p>시나리오 9 : 성공 5 (list / newForm / editForm / create / update) + 400 2 (create 검증 / update 검증)
 * + 404 1 (editForm) + 409 1 (create 메타 충돌).</p>
 */
@WebMvcTest(controllers = BoardModelMetadataController.class)
class BoardModelMetadataControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean BoardModelService boardModelService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static BoardModelResponse activeBoard() {
        return new BoardModelResponse(
                3L, Vendor.ASUS, "P13R-E", "desc",
                0, 0, 0, true, false, false, LifecycleStage.ACTIVE);
    }

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("GET /management/board — 목록 200 + list 뷰")
    void list_returns200() throws Exception {
        given(boardModelService.findAllGrouped(false)).willReturn(List.of(
                VendorGroupResponse.of(Vendor.ASUS, List.of(activeBoard()))));

        mvc.perform(get("/management/board"))
                .andExpect(status().isOk())
                .andExpect(view().name("management/board/list"))
                .andExpect(model().attributeExists("boardGroups"));
    }

    // S5-4 (흡수) — '삭제된 항목 포함' 체크박스의 마커 검증 + inline onchange 부재 회귀.
    @Test
    @DisplayName("GET /management/board — '삭제된 항목 포함' 체크박스에 data-include-deleted-toggle 마커 + inline onchange 부재")
    void renders_includeDeletedToggle_with_dataMarker() throws Exception {
        given(boardModelService.findAllGrouped(false)).willReturn(List.of());

        mvc.perform(get("/management/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-include-deleted-toggle")))
                .andExpect(content().string(not(containsString("onchange=\"window.location"))));
    }

    // R3-1 (흡수) — 집계/모달 카피에 board-scoped Subprogram(드라이버·유틸리티) 포함 렌더 검증.
    @Test
    @DisplayName("GET /management/board — 모달 카피(deprecate 신설 포함) + '현재 연결' 에 드라이버·유틸리티(subprogramCount) 노출")
    void renders_subprogramCount_inModalAndConnections() throws Exception {
        BoardModelResponse board = new BoardModelResponse(
                1L, Vendor.GIGABYTE, "MS03-CE0", "",
                2, 1, 3, true, false, false, LifecycleStage.ACTIVE);
        given(boardModelService.findAllGrouped(false)).willReturn(List.of(
                VendorGroupResponse.of(Vendor.GIGABYTE, List.of(board))));

        mvc.perform(get("/management/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("드라이버·유틸리티")))
                .andExpect(content().string(containsString("함께 Deprecated 표시돼요")));
    }

    @Test
    @DisplayName("GET /management/board/new — 신규 폼 200 + new 뷰")
    void newForm_returns200() throws Exception {
        mvc.perform(get("/management/board/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("management/board/new"))
                .andExpect(model().attributeExists("boardModelForm", "vendorOptions"));
    }

    @Test
    @DisplayName("GET /management/board/{id}/edit — 수정 폼 200 + edit 뷰")
    void editForm_returns200() throws Exception {
        given(boardModelService.findById(3L)).willReturn(activeBoard());

        mvc.perform(get("/management/board/3/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("management/board/edit"))
                .andExpect(model().attributeExists("boardModelForm", "boardModelId", "vendorLabel"));
    }

    @Test
    @DisplayName("POST /management/board (JSON) — 생성 성공 200 + redirect body")
    void create_success_returns200WithRedirect() throws Exception {
        given(boardModelService.create(any())).willReturn(42L);

        mvc.perform(post("/management/board")
                        .param("vendor", "ASUS")
                        .param("modelName", "P13R-E")
                        .param("description", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.redirect").value("/management/board?selectId=42"));
    }

    @Test
    @DisplayName("POST /management/board/{id}/edit — 수정 성공 302 redirect (selectId 보존)")
    void update_success_returns302() throws Exception {
        // boardModelService.update 는 void — 기본 mock 동작(do nothing) 사용.

        mvc.perform(post("/management/board/3/edit")
                        .param("modelName", "P13R-E-rev2")
                        .param("description", "new desc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/board?selectId=3"));
    }

    // ==== 400 검증 실패 ===============================================

    @Test
    @DisplayName("POST /management/board (JSON) — modelName 누락 → 400 + fieldErrors")
    void create_validationFailure_returns400() throws Exception {
        mvc.perform(post("/management/board")
                        .param("vendor", "ASUS")
                        .param("modelName", "")
                        .param("description", "desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("modelName"));
    }

    @Test
    @DisplayName("POST /management/board/{id}/edit — modelName 누락 → 폼 뷰 재렌더(200, edit)")
    void update_validationFailure_rerendersForm() throws Exception {
        given(boardModelService.findById(3L)).willReturn(activeBoard());

        mvc.perform(post("/management/board/3/edit")
                        .param("modelName", "")
                        .param("description", "desc"))
                .andExpect(status().isOk())
                .andExpect(view().name("management/board/edit"))
                .andExpect(model().attributeExists("boardModelId", "vendorLabel"));
    }

    // ==== 404 ========================================================

    @Test
    @DisplayName("GET /management/board/{id}/edit — 없는 id → BoardModelNotFound 404 (advice)")
    void editForm_notFound_returns404() throws Exception {
        willThrow(new BoardModelNotFoundException(999L))
                .given(boardModelService).findById(999L);

        mvc.perform(get("/management/board/999/edit"))
                .andExpect(status().isNotFound());
    }

    // ==== 409 ========================================================

    @Test
    @DisplayName("POST /management/board (JSON) — 메타 충돌(nudge required) → 409 (advice)")
    void create_metaConflict_returns409() throws Exception {
        NudgeRequiredResponse payload = NudgeRequiredResponse.of(
                java.util.UUID.randomUUID(), List.of(), Instant.now().plusSeconds(300));
        willThrow(new BoardModelNudgeRequiredException(payload))
                .given(boardModelService).create(any());

        mvc.perform(post("/management/board")
                        .param("vendor", "ASUS")
                        .param("modelName", "P13R-E")
                        .param("description", "desc"))
                .andExpect(status().isConflict());
    }
}
