package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
import com.example.serverprovision.management.board.service.BoardModelNudgeService;
import com.example.serverprovision.management.board.service.BoardModelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-2-2 — Board 영구 삭제 (purge) 의 typed-name 검증 흐름 통합 테스트.
 *
 * <p>Board purge 엔드포인트는 본 슬라이스에서 신설된 경로. 2 시나리오.</p>
 */
@WebMvcTest(controllers = BoardModelController.class)
class BoardModelControllerPurgeFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean BoardModelService boardModelService;
    @MockitoBean BoardModelNudgeService boardModelNudgeService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("Board purge — typedName 일치 → 302 redirect, includeDeleted 보존")
    void purge_typedNameMatches_returns302() throws Exception {
        willDoNothing().given(boardModelService)
                .purgeWithTypedNameCheck(eq(3L), eq("Asus P13R-E"));

        mvc.perform(post("/management/board/3/purge").param("typedName", "Asus P13R-E"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/management/board?includeDeleted=true"));
    }

    @Test
    @DisplayName("Board purge — typedName 불일치 → 400")
    void purge_typedNameMismatch_returns400() throws Exception {
        willThrow(new TypedNameMismatchException("Asus P13R-E", "wrong"))
                .given(boardModelService)
                .purgeWithTypedNameCheck(eq(3L), eq("wrong"));

        mvc.perform(post("/management/board/3/purge").param("typedName", "wrong"))
                .andExpect(status().isBadRequest());
    }
}
