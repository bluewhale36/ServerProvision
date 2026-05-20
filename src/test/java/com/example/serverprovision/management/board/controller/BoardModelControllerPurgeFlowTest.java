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

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-2-2 — Board 영구 삭제 (purge) 의 typed-name 검증 흐름 통합 테스트.
 *
 * <p>Board purge 엔드포인트는 본 슬라이스에서 신설된 경로. 2 시나리오.</p>
 */
@WebMvcTest(controllers = BoardModelController.class)
class BoardModelControllerPurgeFlowTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;

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

    // S5-4 — '삭제된 항목 포함' 체크박스의 마커 검증 + inline onchange 부재 회귀.
    @Test
    @DisplayName("GET /management/board — '삭제된 항목 포함' 체크박스에 data-include-deleted-toggle 마커 + inline onchange 부재")
    void renders_includeDeletedToggle_with_dataMarker() throws Exception {
        given(boardModelService.findAllGrouped(false)).willReturn(List.of());

        mvc.perform(get("/management/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-include-deleted-toggle")))
                .andExpect(content().string(not(containsString("onchange=\"window.location"))));
    }
}
