package com.example.serverprovision.provisioning.group.controller;

import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.GroupAssignmentService;
import com.example.serverprovision.provisioning.group.dto.request.GroupListQuery;
import com.example.serverprovision.provisioning.group.dto.response.GroupSummaryResponse;
import com.example.serverprovision.provisioning.group.enums.GroupSortField;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupCommandService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** S8-1 — 서버 그룹 목록의 조회 조건 바인딩 · 쪽 정정 · 400 을 HTTP 계층에서 본다. */
@WebMvcTest(controllers = GuestServerGroupController.class)
class GuestServerGroupControllerListTest {

    @Autowired MockMvc mvc;
    @MockitoBean GuestServerGroupQueryService queryService;
    @MockitoBean GuestServerGroupCommandService commandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean GroupAssignmentService groupAssignmentService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void stub() {
        given(queryService.search(any(), any())).willAnswer(inv -> new PageImpl<>(
                List.of(new GroupSummaryResponse(7L, "8월 2차", 3L, false, LocalDateTime.now())),
                (Pageable) inv.getArgument(1), 1));
    }

    private GroupListQuery capturedQuery() {
        ArgumentCaptor<GroupListQuery> c = ArgumentCaptor.forClass(GroupListQuery.class);
        then(queryService).should().search(c.capture(), any(Pageable.class));
        return c.getValue();
    }

    private Pageable capturedPageable() {
        ArgumentCaptor<Pageable> c = ArgumentCaptor.forClass(Pageable.class);
        then(queryService).should().search(any(), c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("200 — 기본은 만든 시각 최신순 · 필터 없음, 조회 띠와 쪽 이동 줄을 그린다")
    void defaultList() throws Exception {
        mvc.perform(get("/provisioning/server-group"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"listQuery\"")))
                .andExpect(content().string(containsString("n-pagination")))
                .andExpect(content().string(containsString("3대")));

        GroupListQuery q = capturedQuery();
        assertThat(q.sort()).isEqualTo(GroupSortField.CREATED_AT);
        assertThat(q.dir()).isEqualTo(Sort.Direction.DESC);
        assertThat(q.isFiltered()).isFalse();
    }

    @Test
    @DisplayName("200 — 검색어 · 표준 정의서 지정 · 멤버 수 정렬 · 방향이 바인딩된다")
    void bindsAllAxes() throws Exception {
        mvc.perform(get("/provisioning/server-group").param("q", "8월").param("hasStandardDefinition", "false")
                        .param("sort", "MEMBER_COUNT").param("dir", "ASC"))
                .andExpect(status().isOk());

        GroupListQuery q = capturedQuery();
        assertThat(q.q()).isEqualTo("8월");
        assertThat(q.hasStandardDefinition()).isFalse();
        assertThat(q.sort()).isEqualTo(GroupSortField.MEMBER_COUNT);
        assertThat(q.dir()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("200 — 표시 개수 상한 100 으로 정정된다")
    void pageableIsClamped() throws Exception {
        mvc.perform(get("/provisioning/server-group").param("size", "500")).andExpect(status().isOk());
        assertThat(capturedPageable().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("200 — 조건으로 비었으면 '조건에 맞는 그룹이 없습니다' 로 안내하고 그룹 생성 안내는 내지 않는다")
    void emptyFilteredResult() throws Exception {
        willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0)).given(queryService).search(any(), any());

        mvc.perform(get("/provisioning/server-group").param("q", "없음"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 그룹이 없습니다.")));
    }

    @Test
    @DisplayName("400 — 정렬 항목이 화이트리스트 밖이면 조회하지 않는다")
    void unknownSortField_400() throws Exception {
        mvc.perform(get("/provisioning/server-group").param("sort", "members")).andExpect(status().isBadRequest());
        then(queryService).should(never()).search(any(), any());
    }

    @Test
    @DisplayName("400 — 방향 · 표준 정의서 값이 잘못되면 조회하지 않는다")
    void invalidValues_400() throws Exception {
        mvc.perform(get("/provisioning/server-group").param("dir", "UP")).andExpect(status().isBadRequest());
        mvc.perform(get("/provisioning/server-group").param("hasStandardDefinition", "maybe")).andExpect(status().isBadRequest());
        then(queryService).should(never()).search(any(), any());
    }
}
