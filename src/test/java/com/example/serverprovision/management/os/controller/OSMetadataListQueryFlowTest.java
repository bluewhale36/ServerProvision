package com.example.serverprovision.management.os.controller;

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
import static org.assertj.core.api.Assertions.assertThat;
import com.example.serverprovision.global.web.list.ListFilterMarkup;

/**
 * S8-3 — OS 버전 목록의 조회 띠 흐름(HTTP 계층).
 *
 * <p>조건 바인딩(서비스 인자) · 조회 띠 렌더(선택 보존 속성 · 조건만 푼 초기화 주소) · 조건 중 빈 결과 안내 ·
 * 오류값 400 · 상태 변경 뒤 리다이렉트의 조건 보존({@code ReturnQueryInterceptor}). 서비스는 mock 이고
 * 컨트롤러 · 바인딩 · 인터셉터 · 템플릿은 실제로 실행된다.</p>
 */
@WebMvcTest(controllers = {OSMetadataController.class})
class OSMetadataListQueryFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataService osMetadataService;
    @MockitoBean com.example.serverprovision.management.os.service.metadata.OSMetadataLifecycleService osMetadataLifecycleService;

    @MockitoBean com.example.serverprovision.global.trash.service.TypedNameVerifier typedNameVerifier;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("GET /management/os — 조건이 ResourceListQuery 로 묶여 서비스에 가고, 조회 띠가 값 · 선택 보존 · 조건만 푼 초기화 주소를 그린다")
    void list_bindsQuery_rendersBar() throws Exception {
        given(osMetadataService.findAllGrouped(any())).willReturn(List.of());
        mvc.perform(get("/management/os?q=ab&enabled=false&deprecated=true&includeDeleted=true&selectKey=ROCKY_LINUX&selectId=5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-list-query")))
                .andExpect(content().string(containsString("data-list-keep=\"selectKey selectId\"")))
                .andExpect(content().string(containsString("value=\"ab\"")))
                .andExpect(content().string(containsString("href=\"/management/os?includeDeleted=true&amp;selectKey=ROCKY_LINUX&amp;selectId=5\"")));

        verify(osMetadataService).findAllGrouped(new ResourceListQuery("ab", false, true, true));
    }

    @Test
    @DisplayName("GET /management/os — 조건 중 결과 0 건이면 '조건에 맞는 항목이 없습니다' · 조건이 없으면 그 안내가 없다")
    void list_filteredEmpty_showsGuideOnlyWhenFiltered() throws Exception {
        given(osMetadataService.findAllGrouped(any())).willReturn(List.of());
        mvc.perform(get("/management/os?q=zz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 항목이 없습니다.")));
        mvc.perform(get("/management/os"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("조건에 맞는 항목이 없습니다."))));
    }

    @Test
    @DisplayName("GET /management/os?enabled=maybe — 알 수 없는 값은 바인딩 400 · 서비스는 불리지 않는다")
    void list_invalidFlag_400() throws Exception {
        mvc.perform(get("/management/os?enabled=maybe"))
                .andExpect(status().isBadRequest());

        verify(osMetadataService, never()).findAllGrouped(any());
    }

    @Test
    @DisplayName("POST /management/os/3/toggle — returnQuery 의 조건 · 묶음 선택이 리다이렉트에 붙고 옛 selectId 는 버려진다(S8-3 Q1)")
    void stateChange_redirectKeepsListQuery() throws Exception {
        mvc.perform(post("/management/os/3/toggle")
                        .param("selectKey", "ROCKY_LINUX")
                        .param("returnQuery", "/management/os?q=9&deprecated=false&selectKey=ROCKY_LINUX&selectId=9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/management/os?selectId=3&selectKey=ROCKY_LINUX&q=9&deprecated=false"));
    }

    @Test
    @DisplayName("POST /management/os/3/toggle — returnQuery 가 다른 화면 경로면 리다이렉트를 건드리지 않는다")
    void stateChange_foreignReturnPath_ignored() throws Exception {
        mvc.perform(post("/management/os/3/toggle")
                        .param("selectKey", "ROCKY_LINUX")
                        .param("returnQuery", "/management/setting?q=ab"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/management/os?selectId=3&selectKey=ROCKY_LINUX"));
    }

    @Test
    @DisplayName("GET /management/os — 조건은 Search 버튼이 여는 조회 조건 창에 한 줄씩 서고, 걸린 값이 창 안에 checked 로 그려진다(S8-4)")
    void list_conditionsLiveInFilterPanel() throws Exception {
        given(osMetadataService.findAllGrouped(any())).willReturn(List.of());
        String html = mvc.perform(get("/management/os?q=ab&enabled=false&includeDeleted=true"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("popovertarget=\"listFilterPanel\"", ">Search</button>", "popover=\"auto\"", "data-filter-tokens");
        assertThat(ListFilterMarkup.rowLabels(html)).containsExactly("활성", "사용 중단", "삭제된 항목");
        String panel = ListFilterMarkup.panel(html);
        assertThat(panel).contains("name=\"enabled\" value=\"false\" checked", "name=\"includeDeleted\" value=\"true\" checked");
        // 검색칸은 창 밖(띠)에 남는다 — Enter 로 바로 제출되는 길
        assertThat(panel).doesNotContain("name=\"q\"");
        assertThat(ListFilterMarkup.outsidePanel(html)).contains("name=\"q\"", "value=\"ab\"");
    }

}
