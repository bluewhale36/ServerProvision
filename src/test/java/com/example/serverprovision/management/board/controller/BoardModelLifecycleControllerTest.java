package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.management.board.exception.BoardModelNotFoundException;
import com.example.serverprovision.management.board.exception.DuplicateBoardModelException;
import com.example.serverprovision.management.board.exception.IllegalBoardModelStateException;
import com.example.serverprovision.management.board.service.BoardModelService;
import com.example.serverprovision.management.common.dto.response.RestoreResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R3-2 — {@link BoardModelLifecycleController} 통합 테스트 (lifecycle 상태 전이 :
 * toggle / delete / restore / purge / deprecate / undeprecate).
 *
 * <p>fat {@code BoardModelController} 3분할 후의 회귀 안전망. 기존
 * {@code BoardModelControllerPurgeFlowTest} 의 purge typed-name 시나리오 +
 * {@code BoardModelControllerRestoreFlowTest} 의 restore cascade 변이 시나리오를 흡수했다.</p>
 *
 * <p>Mocking 은 {@link BoardModelService} 단까지만. controller 의 redirect +
 * {@code @ControllerAdvice} 의 예외 → status 매핑은 실제로 실행된다.</p>
 *
 * <p>시나리오 12 : 성공 9 (toggle / restore×3 / deprecate / undeprecate / delete / purge + restore cascade=true)
 * + 400 1 (purge typed-name 불일치) + 404 1 (없는 id toggle) + 409 2 (lifecycle invariant / restore 충돌).</p>
 */
@WebMvcTest(controllers = BoardModelLifecycleController.class)
class BoardModelLifecycleControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean BoardModelService boardModelService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("POST /{id}/toggle — 302 redirect (selectId 보존)")
    void toggle_returns302() throws Exception {
        willDoNothing().given(boardModelService).toggleEnabled(eq(3L));

        mvc.perform(post("/management/board/3/toggle"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/board?selectId=3"));
    }

    @Test
    @DisplayName("POST /{id}/deprecate — 302 redirect (selectId 보존)")
    void deprecate_returns302() throws Exception {
        willDoNothing().given(boardModelService).deprecate(eq(3L));

        mvc.perform(post("/management/board/3/deprecate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/board?selectId=3"));
    }

    @Test
    @DisplayName("POST /{id}/undeprecate — 302 redirect (selectId 보존)")
    void undeprecate_returns302() throws Exception {
        willDoNothing().given(boardModelService).undeprecate(eq(3L));

        mvc.perform(post("/management/board/3/undeprecate"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/board?selectId=3"));
    }

    @Test
    @DisplayName("POST /{id}/delete — 302 redirect (목록으로, 선택 복원 없음)")
    void delete_returns302() throws Exception {
        willDoNothing().given(boardModelService).softDelete(eq(3L));

        mvc.perform(post("/management/board/3/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/board"));
    }

    @Test
    @DisplayName("POST /{id}/purge — typedName 일치 → 302 redirect (includeDeleted 보존)")
    void purge_typedNameMatches_returns302() throws Exception {
        willDoNothing().given(boardModelService)
                .purgeWithTypedNameCheck(eq(3L), eq("Asus P13R-E"));

        mvc.perform(post("/management/board/3/purge").param("typedName", "Asus P13R-E"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/board?includeDeleted=true"));
    }

    // ── restore cascade 변이 (기존 RestoreFlowTest 흡수) ──────────────

    @Test
    @DisplayName("POST /{id}/restore cascade=true — 하위 BIOS/BMC 복구 → 302 redirect")
    void restore_cascadeTrue_returns302() throws Exception {
        given(boardModelService.restore(eq(3L), eq(true))).willReturn(new RestoreResponse(2));

        mvc.perform(post("/management/board/3/restore").param("cascade", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/board?selectId=3"));
    }

    @Test
    @DisplayName("POST /{id}/restore cascade=false — 부모만 복구 → 302 redirect")
    void restore_cascadeFalse_returns302() throws Exception {
        given(boardModelService.restore(eq(3L), eq(false))).willReturn(RestoreResponse.none());

        mvc.perform(post("/management/board/3/restore").param("cascade", "false"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("POST /{id}/restore — cascade 파라미터 누락 시 default false → 302 redirect")
    void restore_noCascadeParam_defaultsFalse() throws Exception {
        given(boardModelService.restore(eq(3L), eq(false))).willReturn(RestoreResponse.none());

        mvc.perform(post("/management/board/3/restore"))
                .andExpect(status().is3xxRedirection());
    }

    // ==== 400 — purge typed-name 불일치 (기존 PurgeFlowTest 흡수) ======

    @Test
    @DisplayName("POST /{id}/purge — typedName 불일치 → TypedNameMismatch 400 (advice)")
    void purge_typedNameMismatch_returns400() throws Exception {
        willThrow(new TypedNameMismatchException("Asus P13R-E", "wrong"))
                .given(boardModelService)
                .purgeWithTypedNameCheck(eq(3L), eq("wrong"));

        mvc.perform(post("/management/board/3/purge").param("typedName", "wrong"))
                .andExpect(status().isBadRequest());
    }

    // ==== 404 ========================================================

    @Test
    @DisplayName("POST /{id}/toggle — 없는 id → BoardModelNotFound 404 (advice)")
    void toggle_notFound_returns404() throws Exception {
        willThrow(new BoardModelNotFoundException(999L))
                .given(boardModelService).toggleEnabled(eq(999L));

        mvc.perform(post("/management/board/999/toggle"))
                .andExpect(status().isNotFound());
    }

    // ==== 409 ========================================================

    @Test
    @DisplayName("POST /{id}/restore — 이미 활성/부재 → IllegalBoardModelState 409 (advice)")
    void restore_illegalState_returns409() throws Exception {
        willThrow(new IllegalBoardModelStateException("이미 활성 상태이거나 존재하지 않는 메인보드 모델입니다. id=3"))
                .given(boardModelService).restore(eq(3L), eq(false));

        mvc.perform(post("/management/board/3/restore"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /{id}/restore cascade=true — 활성 (vendor, modelName) 충돌 → Duplicate 409 (advice)")
    void restore_cascade_duplicate_returns409() throws Exception {
        willThrow(new DuplicateBoardModelException(Vendor.ASUS, "P13R-E"))
                .given(boardModelService).restore(eq(3L), eq(true));

        mvc.perform(post("/management/board/3/restore").param("cascade", "true"))
                .andExpect(status().isConflict());
    }
}
