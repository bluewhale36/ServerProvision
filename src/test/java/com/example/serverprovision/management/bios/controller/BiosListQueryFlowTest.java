package com.example.serverprovision.management.bios.controller;

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
 * S8-3 — BIOS 목록의 조회 띠 흐름(HTTP 계층).
 *
 * <p>조건 바인딩(서비스 인자) · 조회 띠 렌더(선택 보존 속성 · 조건만 푼 초기화 주소) · 조건 중 빈 결과 안내 ·
 * 오류값 400 · 상태 변경 뒤 리다이렉트의 조건 보존({@code ReturnQueryInterceptor}). 서비스는 mock 이고
 * 컨트롤러 · 바인딩 · 인터셉터 · 템플릿은 실제로 실행된다.</p>
 */
@WebMvcTest(controllers = {BiosMetadataController.class, BiosLifecycleController.class})
class BiosListQueryFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean com.example.serverprovision.management.bios.service.BiosService biosService;
    @MockitoBean com.example.serverprovision.management.board.service.metadata.BoardModelMetadataService boardModelService;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosLifecycleService biosLifecycleService;
    @MockitoBean com.example.serverprovision.global.lifecycle.DeleteIntentRegistry deleteIntentRegistry;
    @MockitoBean com.example.serverprovision.management.bios.service.BiosFirmwareFilePolicy biosFirmwareFilePolicy;

    @MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /management/bios — 조건이 ResourceListQuery 로 묶여 서비스에 가고, 조회 띠가 값 · 선택 보존 · 조건만 푼 초기화 주소를 그린다")
    void list_bindsQuery_rendersBar() throws Exception {
        given(biosService.findAllGrouped(any())).willReturn(List.of());
        mvc.perform(get("/management/bios?q=ab&enabled=false&deprecated=true&includeDeleted=true&selectBoardId=10&selectId=5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-list-query")))
                .andExpect(content().string(containsString("data-list-keep=\"selectBoardId selectId\"")))
                .andExpect(content().string(containsString("value=\"ab\"")))
                .andExpect(content().string(containsString("href=\"/management/bios?includeDeleted=true&amp;selectBoardId=10&amp;selectId=5\"")));

        verify(biosService).findAllGrouped(new ResourceListQuery("ab", false, true, true));
    }

    @Test
    @DisplayName("GET /management/bios — 조건 중 결과 0 건이면 '조건에 맞는 항목이 없습니다' · 조건이 없으면 그 안내가 없다")
    void list_filteredEmpty_showsGuideOnlyWhenFiltered() throws Exception {
        given(biosService.findAllGrouped(any())).willReturn(List.of());
        mvc.perform(get("/management/bios?q=zz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 항목이 없습니다.")));
        mvc.perform(get("/management/bios"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("조건에 맞는 항목이 없습니다."))));
    }

    @Test
    @DisplayName("GET /management/bios?enabled=maybe — 알 수 없는 값은 바인딩 400 · 서비스는 불리지 않는다")
    void list_invalidFlag_400() throws Exception {
        mvc.perform(get("/management/bios?enabled=maybe"))
                .andExpect(status().isBadRequest());

        verify(biosService, never()).findAllGrouped(any());
    }

    @Test
    @DisplayName("POST /management/bios/10/bios/5/toggle — returnQuery 의 조건 · 묶음 선택이 리다이렉트에 붙고 옛 selectId 는 버려진다(S8-3 Q1)")
    void stateChange_redirectKeepsListQuery() throws Exception {
        mvc.perform(post("/management/bios/10/bios/5/toggle")
                        .param("returnQuery", "/management/bios?q=F1&selectBoardId=10&selectId=9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/management/bios?selectId=5&q=F1&selectBoardId=10"));
    }

    @Test
    @DisplayName("POST /management/bios/10/bios/5/toggle — returnQuery 가 다른 화면 경로면 리다이렉트를 건드리지 않는다")
    void stateChange_foreignReturnPath_ignored() throws Exception {
        mvc.perform(post("/management/bios/10/bios/5/toggle")
                        .param("returnQuery", "/management/setting?q=ab"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/management/bios?selectId=5"));
    }

    @Test
    @DisplayName("GET /management/bios — 조건이 걸리면 순위 드래그를 끄고 같은 자리에 꺼진 핸들 + 프로젝트 툴팁 · 조건이 없으면 켠다(plan D8 · CP6 수정)")
    void list_filtered_disablesRankDrag() throws Exception {
        given(biosService.findAllGrouped(any())).willReturn(List.of(new com.example.serverprovision.management.bios.dto.response.BoardWithBiosListResponse(
                10L, com.example.serverprovision.management.board.enums.Vendor.GIGABYTE, "Gigabyte", "MS03-CE0", false, 5L, List.of(new com.example.serverprovision.management.bios.dto.response.BiosResponse(
                5L, 10L, "fw", "F10", "/fw/F10", "startup.nsh", "h", 1, 1L, null,
                com.example.serverprovision.global.marker.IntegrityStatus.NOT_VERIFIED,
                true, false, false, false, false, false)))));

        mvc.perform(get("/management/bios?q=ab"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-rank-enabled=\"false\"")))
                .andExpect(content().string(containsString("n-rank-handle n-rank-handle-disabled")))
                .andExpect(content().string(containsString("data-tooltip=\"조건이 걸린 동안에는 순서를 바꿀 수 없습니다.")));
        mvc.perform(get("/management/bios"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-rank-enabled=\"true\"")))
                .andExpect(content().string(not(containsString("n-rank-handle-disabled"))))
                .andExpect(content().string(containsString("data-tooltip=\"끌어서 순서 변경\"")));
    }

    @Test
    @DisplayName("POST 삭제 — 컨트롤러가 정한 묶음 선택(selectBoardId)은 유지되고 조건만 덧붙는다")
    void softDelete_keepsControllerBoardSelection() throws Exception {
        mvc.perform(post("/management/bios/10/bios/5/delete")
                        .param("returnQuery", "/management/bios?q=F1&selectBoardId=7&selectId=5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/management/bios?selectBoardId=10&q=F1"));
    }
}
