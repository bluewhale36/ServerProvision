package com.example.serverprovision.management.board.controller;

import com.example.serverprovision.global.lifecycle.dto.ResourceListQuery;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S8-3 — 메인보드 모델 목록의 조회 띠 흐름(HTTP 계층).
 *
 * <p>조건 바인딩(서비스 인자) · 조회 띠 렌더(선택 보존 속성 · 조건만 푼 초기화 주소) · 조건 중 빈 결과 안내 ·
 * 오류값 400 · 상태 변경 뒤 리다이렉트의 조건 보존({@code ReturnQueryInterceptor}). 서비스는 mock 이고
 * 컨트롤러 · 바인딩 · 인터셉터 · 템플릿은 실제로 실행된다.</p>
 */
@WebMvcTest(controllers = {BoardModelMetadataController.class, BoardModelLifecycleController.class})
class BoardModelListQueryFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService boardModelService;
    @MockitoBean com.example.serverprovision.management.board.service.metadata.BoardModelLifecycleService boardModelLifecycleService;

    @MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /management/board — 조건이 ResourceListQuery 로 묶여 서비스에 가고, 조회 띠가 값 · 선택 보존 · 조건만 푼 초기화 주소를 그린다")
    void list_bindsQuery_rendersBar() throws Exception {
        given(boardModelService.findAllGrouped(any())).willReturn(List.of());
        mvc.perform(get("/management/board?q=ab&enabled=false&deprecated=true&includeDeleted=true&selectKey=GIGABYTE&selectId=5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-list-query")))
                .andExpect(content().string(containsString("data-list-keep=\"selectKey selectId\"")))
                .andExpect(content().string(containsString("value=\"ab\"")))
                .andExpect(content().string(containsString("href=\"/management/board?includeDeleted=true&amp;selectKey=GIGABYTE&amp;selectId=5\"")));

        verify(boardModelService).findAllGrouped(new ResourceListQuery("ab", false, true, true));
    }

    @Test
    @DisplayName("GET /management/board — 조건 중 결과 0 건이면 '조건에 맞는 항목이 없습니다' · 조건이 없으면 그 안내가 없다")
    void list_filteredEmpty_showsGuideOnlyWhenFiltered() throws Exception {
        given(boardModelService.findAllGrouped(any())).willReturn(List.of());
        mvc.perform(get("/management/board?q=zz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 항목이 없습니다.")));
        mvc.perform(get("/management/board"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("조건에 맞는 항목이 없습니다."))));
    }

    @Test
    @DisplayName("GET /management/board?enabled=maybe — 알 수 없는 값은 바인딩 400 · 서비스는 불리지 않는다")
    void list_invalidFlag_400() throws Exception {
        mvc.perform(get("/management/board?enabled=maybe"))
                .andExpect(status().isBadRequest());

        verify(boardModelService, never()).findAllGrouped(any());
    }

    @Test
    @DisplayName("POST /management/board/3/toggle — returnQuery 의 조건 · 묶음 선택이 리다이렉트에 붙고 옛 selectId 는 버려진다(S8-3 Q1)")
    void stateChange_redirectKeepsListQuery() throws Exception {
        mvc.perform(post("/management/board/3/toggle")
                        .param("returnQuery", "/management/board?q=MS&enabled=true&selectKey=GIGABYTE&selectId=9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/management/board?selectId=3&q=MS&enabled=true&selectKey=GIGABYTE"));
    }

    @Test
    @DisplayName("POST /management/board/3/toggle — returnQuery 가 다른 화면 경로면 리다이렉트를 건드리지 않는다")
    void stateChange_foreignReturnPath_ignored() throws Exception {
        mvc.perform(post("/management/board/3/toggle")
                        .param("returnQuery", "/management/setting?q=ab"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/management/board?selectId=3"));
    }
}
