package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.service.TypedNameVerifier;
import com.example.serverprovision.management.board.service.BoardModelNudgeService;
import com.example.serverprovision.management.common.nudge.exception.NudgeAlreadyResolvedException;
import com.example.serverprovision.management.common.nudge.exception.NudgeNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R3-2 — {@link BoardModelNudgeController} 통합 테스트 (메타 nudge confirm : proceed / replace / cancel).
 *
 * <p>fat {@code BoardModelController} 3분할 후의 회귀 안전망. Mocking 은 {@link BoardModelNudgeService} +
 * {@link TypedNameVerifier} 단까지만. {@code @ControllerAdvice} 의 예외 → status 매핑은 실제로 실행된다.</p>
 *
 * <p>시나리오 7 : 성공 3 (proceed / replace JSON, cancel 204) + 400 1 (replace typedName 불일치)
 * + 404 1 (없는 nudgeId proceed) + 409 1 (이미 처리된 nudge replace).</p>
 */
@WebMvcTest(controllers = BoardModelNudgeController.class)
class BoardModelNudgeControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean BoardModelNudgeService boardModelNudgeService;
    @MockitoBean TypedNameVerifier typedNameVerifier;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final UUID NUDGE_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("POST /nudge/{nudgeId}/proceed — 신규 등록 → JSON(id + selectUrl)")
    void nudgeProceed_returnsJson() throws Exception {
        given(boardModelNudgeService.proceed(eq(NUDGE_ID))).willReturn(77L);

        mvc.perform(post("/management/board/nudge/" + NUDGE_ID + "/proceed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.redirect").value("/management/board?selectId=77"));
    }

    @Test
    @DisplayName("POST /nudge/{nudgeId}/replace — 충돌 자원 교체 → JSON(id + selectUrl)")
    void nudgeReplace_returnsJson() throws Exception {
        given(boardModelNudgeService.replace(eq(NUDGE_ID), eq(9L))).willReturn(88L);

        mvc.perform(post("/management/board/nudge/" + NUDGE_ID + "/replace")
                        .param("targetId", "9")
                        .param("typedName", "Asus P13R-E"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(88))
                .andExpect(jsonPath("$.redirect").value("/management/board?selectId=88"));
    }

    @Test
    @DisplayName("POST /nudge/{nudgeId}/cancel — 세션 폐기 → 204 No Content")
    void nudgeCancel_returns204() throws Exception {
        willDoNothing().given(boardModelNudgeService).cancel(eq(NUDGE_ID));

        mvc.perform(post("/management/board/nudge/" + NUDGE_ID + "/cancel"))
                .andExpect(status().isNoContent());
    }

    // ==== 400 — replace typed-name 불일치 =============================

    @Test
    @DisplayName("POST /nudge/{nudgeId}/replace — typedName 불일치 → TypedNameMismatch 400 (advice)")
    void nudgeReplace_typedNameMismatch_returns400() throws Exception {
        willThrow(new TypedNameMismatchException("Asus P13R-E", "wrong"))
                .given(typedNameVerifier)
                .verify(eq(ResourceType.BOARD_MODEL), eq(9L), eq("wrong"));

        mvc.perform(post("/management/board/nudge/" + NUDGE_ID + "/replace")
                        .param("targetId", "9")
                        .param("typedName", "wrong"))
                .andExpect(status().isBadRequest());
    }

    // ==== 404 — 없는 nudgeId ==========================================

    @Test
    @DisplayName("POST /nudge/{nudgeId}/proceed — 없는 nudgeId → NudgeNotFound 404 (advice)")
    void nudgeProceed_notFound_returns404() throws Exception {
        willThrow(new NudgeNotFoundException(NUDGE_ID))
                .given(boardModelNudgeService).proceed(eq(NUDGE_ID));

        mvc.perform(post("/management/board/nudge/" + NUDGE_ID + "/proceed"))
                .andExpect(status().isNotFound());
    }

    // ==== 409 — 이미 처리된 nudge =====================================

    @Test
    @DisplayName("POST /nudge/{nudgeId}/replace — 이미 처리된 세션 → NudgeAlreadyResolved 409 (advice)")
    void nudgeReplace_alreadyResolved_returns409() throws Exception {
        // typedName 없이 호출 → verifier 우회, service 가 던지는 경로.
        willThrow(new NudgeAlreadyResolvedException(NUDGE_ID))
                .given(boardModelNudgeService).replace(eq(NUDGE_ID), eq(9L));

        mvc.perform(post("/management/board/nudge/" + NUDGE_ID + "/replace")
                        .param("targetId", "9"))
                .andExpect(status().isConflict());
    }
}
