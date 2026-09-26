package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.request.ServerListQuery;
import com.example.serverprovision.execution.dto.response.BoardOptionGroupResponse;
import com.example.serverprovision.execution.dto.response.BoardOptionResponse;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.execution.dto.response.GuestServerListResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ServerSortField;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.execution.vo.RegistrationAge;
import com.example.serverprovision.execution.vo.ServerScope;
import com.example.serverprovision.execution.vo.SpecGroupKey;
import com.example.serverprovision.provisioning.assignment.service.AssignmentCommandService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentStartService;
import com.example.serverprovision.provisioning.group.dto.response.GroupBadgeResponse;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
import com.example.serverprovision.provisioning.group.vo.GroupFilter;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 게스트 서버 목록(U3-3 · S8-2) — 두 보기의 렌더 · 조회 조건 바인딩 · 그룹 조건 번역 · 400 을 HTTP 계층에서 본다.
 * Mocking 은 조회 서비스까지 — 바인딩 · 변환기(GroupFilterConverter) · Pageable · ListLinks 리졸버 · 템플릿은 실제로 돈다.
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerListTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean com.example.serverprovision.global.redfish.RedfishPowerService redfishPowerService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean AssignmentCommandService assignmentCommandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean AssignmentStartService assignmentStartService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean GuestServerGroupQueryService groupQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static GuestServerSummaryResponse row(String name) {
        return new GuestServerSummaryResponse(
                UUID.randomUUID(), name, UUID.randomUUID(), null, "MS03-CE0",
                GuestServerStatus.PROVISIONING, ProvisioningPhase.DIAGNOSE_LINUX,
                null, LocalDateTime.now(), null, false, null, false,
                new SpecGroupKey("k-fixture"), "MS03-CE0 · 6338 ×2");
    }

    private static GuestServerListResponse bucketOf(String label, GuestServerSummaryResponse... rows) {
        return new GuestServerListResponse(null, List.of(new GuestServerListResponse.TimeGroup(
                new RegistrationAge(RegistrationAge.Unit.SECOND, 30L),
                List.of(new GuestServerListResponse.SpecGroup(new SpecGroupKey("k-" + label), label, List.of(rows))))));
    }

    @BeforeEach
    void stub() {
        given(groupQueryService.scopeOf(any())).willReturn(ServerScope.ALL);
        given(queryService.findGrouped(any(), any())).willReturn(bucketOf("MS03-CE0 · 6338 ×2", row("srv-01"), row("srv-02")));
        given(queryService.findPage(any(), any(), any())).willAnswer(inv ->
                new PageImpl<>(List.of(row("srv-t1")), (Pageable) inv.getArgument(2), 41));
        given(queryService.findBoardOptions()).willReturn(List.of(
                new BoardOptionGroupResponse("Gigabyte", List.of(new BoardOptionResponse(3L, "MS04-CE0", Vendor.GIGABYTE))),
                new BoardOptionGroupResponse("Asus", List.of(new BoardOptionResponse(9L, "Z13PP-D32", Vendor.ASUS)))));
    }

    private ServerListQuery groupedQuery() {
        ArgumentCaptor<ServerListQuery> c = ArgumentCaptor.forClass(ServerListQuery.class);
        then(queryService).should().findGrouped(c.capture(), any());
        return c.getValue();
    }

    private ServerListQuery tableQuery() {
        ArgumentCaptor<ServerListQuery> c = ArgumentCaptor.forClass(ServerListQuery.class);
        then(queryService).should().findPage(c.capture(), any(), any());
        return c.getValue();
    }

    private GroupFilter groupFilter() {
        ArgumentCaptor<GroupFilter> c = ArgumentCaptor.forClass(GroupFilter.class);
        then(groupQueryService).should().scopeOf(c.capture());
        return c.getValue();
    }

    // ==== 묶음 보기(기본 · 현행 유지) =================================

    @Test
    @DisplayName("200 — 기본은 묶음 보기: 스펙 요약 · 대수 · 결과 건수 · 조회 띠, 정렬 셀렉트와 쪽 이동은 없다")
    void groupedByDefault() throws Exception {
        mvc.perform(get("/provisioning/server"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-list"))
                .andExpect(model().attributeExists("list", "query", "links", "phases", "statuses", "boardOptions"))
                // 보드는 제조사별 optgroup · 소속 그룹 셀렉트는 화면에서 뺐다(S8-2 CP6 · S8-4 재설계)
                .andExpect(content().string(containsString("<optgroup label=\"Gigabyte\">")))
                .andExpect(content().string(containsString("<optgroup label=\"Asus\">")))
                .andExpect(content().string(not(containsString("name=\"group\""))))
                .andExpect(content().string(containsString("MS03-CE0 · 6338 ×2")))
                .andExpect(content().string(containsString("30초 전")))
                .andExpect(content().string(containsString("결과 <b>2</b>건")))
                .andExpect(content().string(containsString("id=\"listQuery\"")))
                .andExpect(content().string(not(containsString("name=\"sort\""))))
                .andExpect(content().string(not(containsString("n-pagination"))))
                .andExpect(content().string(not(containsString("include-deleted-toggle.js"))));

        assertThat(groupedQuery().isFiltered()).isFalse();
        then(queryService).should(never()).findPage(any(), any(), any());
    }

    @Test
    @DisplayName("그룹 배지는 전체 이름을 툴팁으로 남긴다 — 열 폭에 잘려도 hover 로 구분된다")
    void groupBadgeCarriesFullNameInTooltip() throws Exception {
        GuestServerSummaryResponse server = row("srv-01");
        willReturn(bucketOf("MS03-CE0", server)).given(queryService).findGrouped(any(), any());
        given(groupQueryService.findBadges(any()))
                .willReturn(Map.of(server.id(), new GroupBadgeResponse(3L, "8월 A동 1차(수정)", null)));

        mvc.perform(get("/provisioning/server"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-tooltip=\"8월 A동 1차(수정)\"")));
    }

    @Test
    @DisplayName("'등록 진행 중' 은 접힌 채 — 펼침 링크는 다른 조건을 지닌 채 pending 만 켠다(ListLinks.toggle)")
    void pendingCollapsedAndToggleKeepsConditions() throws Exception {
        willReturn(new GuestServerListResponse(new GuestServerListResponse.PendingRegistrations(
                List.of(row("srv-a")), List.of(row("srv-b"))), List.of())).given(queryService).findGrouped(any(), any());

        mvc.perform(get("/provisioning/server").param("includeDecommissioned", "true"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pendingOpen", false))
                .andExpect(content().string(containsString("부팅 · 네트워크 점검 대상")))
                .andExpect(content().string(not(containsString("srv-a"))))
                .andExpect(content().string(containsString("href=\"/provisioning/server?includeDecommissioned=true&amp;pending=open\"")));
    }

    @Test
    @DisplayName("?pending=open 이면 펼쳐지고, 조회 띠 폼이 숨은 필드로 펼침을 지닌다")
    void pendingOpenIsCarriedByForm() throws Exception {
        willReturn(new GuestServerListResponse(new GuestServerListResponse.PendingRegistrations(
                List.of(row("srv-a")), List.of(row("srv-b"))), List.of())).given(queryService).findGrouped(any(), any());

        mvc.perform(get("/provisioning/server").param("pending", "open"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pendingOpen", true))
                .andExpect(content().string(containsString("srv-a")))
                .andExpect(content().string(containsString("type=\"hidden\" name=\"pending\" value=\"open\"")));
    }

    // ==== 조회 조건 바인딩 ===========================================

    @Test
    @DisplayName("200 — 모든 축이 바인딩된다: 검색 · 반복 단계 · 반복 상태 · 회수 포함 · 보드 · 기간, 선택 상태가 조회 띠에 되돌아 그려진다")
    void bindsAllAxes() throws Exception {
        mvc.perform(get("/provisioning/server")
                        .param("q", "10.1.1.5")
                        .param("phase", "FIRMWARE_UPDATING").param("phase", "FIRMWARE_SETTING")
                        .param("status", "FAILED").param("status", "PROVISIONED")
                        .param("includeDecommissioned", "true").param("board", "3")
                        .param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"FIRMWARE_SETTING\" checked=\"checked\"")))
                .andExpect(content().string(containsString("value=\"FAILED\" checked=\"checked\"")))
                .andExpect(content().string(containsString("value=\"2026-09-01\"")));

        ServerListQuery q = groupedQuery();
        assertThat(q.q()).isEqualTo("10.1.1.5");
        assertThat(q.phases()).containsExactly(ProvisioningPhase.FIRMWARE_UPDATING, ProvisioningPhase.FIRMWARE_SETTING);
        assertThat(q.statuses()).containsExactly(GuestServerStatus.PROVISIONED, GuestServerStatus.FAILED);
        assertThat(q.includeDecommissioned()).isTrue();
        assertThat(q.boardModelId()).isEqualTo(3L);
        assertThat(q.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(q.to()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("200 — 상태 칩에는 회수됨이 없고, 주소창으로 status=DECOMMISSIONED 를 넣어도 조용히 빠진다")
    void decommissionedStatusIsIgnored() throws Exception {
        mvc.perform(get("/provisioning/server").param("status", "DECOMMISSIONED"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("value=\"DECOMMISSIONED\""))));

        assertThat(groupedQuery().statuses()).isEmpty();
    }

    @Test
    @DisplayName("200 — 그룹 조건: 없으면 ANY · none 은 무소속 · 숫자는 그 그룹, 각각 범위 번역에 넘어간다")
    void groupFilterIsConvertedAndTranslated() throws Exception {
        mvc.perform(get("/provisioning/server")).andExpect(status().isOk());
        assertThat(groupFilter()).isEqualTo(GroupFilter.ANY);
    }

    @Test
    @DisplayName("200 — 화면에는 그룹 셀렉트가 없지만 group=none 파라미터는 무소속으로 번역되고 초기화 링크가 나온다(S8-4 재설계 전까지)")
    void ungroupedFilter() throws Exception {
        mvc.perform(get("/provisioning/server").param("group", "none"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">초기화<")));
        assertThat(groupFilter()).isEqualTo(GroupFilter.UNGROUPED);
    }

    @Test
    @DisplayName("200 — group=7 은 그 그룹으로 번역된다")
    void specificGroupFilter() throws Exception {
        mvc.perform(get("/provisioning/server").param("group", "7")).andExpect(status().isOk());
        assertThat(groupFilter()).isEqualTo(GroupFilter.of(7L));
    }

    // ==== 표 보기 ===================================================

    @Test
    @DisplayName("200 — 표 보기: 쪽 이동 · 정렬 셀렉트 · 같은 행 조각, 정렬 · 쪽이 조회에 넘어간다")
    void tableView() throws Exception {
        mvc.perform(get("/provisioning/server").param("view", "TABLE").param("sort", "LAST_SEEN_AT").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("page"))
                .andExpect(content().string(containsString("srv-t1")))
                .andExpect(content().string(containsString("n-pagination")))
                .andExpect(content().string(containsString("name=\"sort\"")))
                .andExpect(content().string(containsString("결과 <b>41</b>건")));

        ServerListQuery q = tableQuery();
        assertThat(q.sort()).isEqualTo(ServerSortField.LAST_SEEN_AT);
        assertThat(q.dir()).isEqualTo(Sort.Direction.DESC);
        then(queryService).should(never()).findGrouped(any(), any());
    }

    @Test
    @DisplayName("200 — 표 보기 표시 개수 상한 100")
    void tablePageSizeClamped() throws Exception {
        mvc.perform(get("/provisioning/server").param("view", "TABLE").param("size", "9999")).andExpect(status().isOk());
        ArgumentCaptor<Pageable> c = ArgumentCaptor.forClass(Pageable.class);
        then(queryService).should().findPage(any(), any(), c.capture());
        assertThat(c.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("200 — 표 보기 범위 밖 쪽은 첫 페이지 링크로 유도")
    void tableOutOfRangePage() throws Exception {
        willReturn(new PageImpl<>(List.of(), PageRequest.of(9, 20), 41)).given(queryService).findPage(any(), any(), any());
        mvc.perform(get("/provisioning/server").param("view", "TABLE").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이 페이지에는 항목이 없습니다.")))
                .andExpect(content().string(containsString("href=\"/provisioning/server?view=TABLE\"")));
    }

    // ==== 빈 결과 · 기간 ============================================

    @Test
    @DisplayName("200 — 조건으로 비었으면 '조건에 맞는 서버가 없습니다' 와 초기화 유도")
    void emptyFilteredResult() throws Exception {
        willReturn(new GuestServerListResponse(null, List.of())).given(queryService).findGrouped(any(), any());
        mvc.perform(get("/provisioning/server").param("phase", "OS_INSTALLING"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("조건에 맞는 서버가 없습니다.")));
    }

    @Test
    @DisplayName("200 — 기간이 거꾸로면 결과가 비고 '시작일이 종료일보다 늦습니다' 안내")
    void reversedRangeIsExplained() throws Exception {
        willReturn(new GuestServerListResponse(null, List.of())).given(queryService).findGrouped(any(), any());
        mvc.perform(get("/provisioning/server").param("from", "2026-09-30").param("to", "2026-09-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("시작일이 종료일보다 늦습니다.")))
                // CP5 D-2 — 거꾸로 상태에서 min/max 를 그리면 두 칸이 서로를 가둬 어떤 제출도 못 한다
                .andExpect(content().string(not(containsString("max=\"2026-09-01\""))))
                .andExpect(content().string(not(containsString("min=\"2026-09-30\""))));
    }

    @Test
    @DisplayName("200 — 바른 기간이면 두 날짜 입력이 서로 min/max 를 건다")
    void rangeInputsCarryMinMax() throws Exception {
        mvc.perform(get("/provisioning/server").param("from", "2026-09-01").param("to", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("max=\"2026-09-30\"")))
                .andExpect(content().string(containsString("min=\"2026-09-01\"")));
    }

    @Test
    @DisplayName("200 — SSE 재조회 경로(X-Requested-With)로 들어와도 같은 조건 · 같은 교체 영역")
    void sseRefetchKeepsFilteredView() throws Exception {
        mvc.perform(get("/provisioning/server").param("phase", "DIAGNOSE_LINUX").header("X-Requested-With", "server-stream"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-live=\"server-list\"")));
        assertThat(groupedQuery().phases()).containsExactly(ProvisioningPhase.DIAGNOSE_LINUX);
    }

    // ==== 400 — 알 수 없는 값은 바인딩 실패 ============================

    @Test
    @DisplayName("400 — view · phase · status · board · group · from · sort · dir 의 잘못된 값은 조회하지 않는다")
    void invalidValuesAreBadRequest() throws Exception {
        String[][] cases = {
                {"view", "XX"}, {"phase", "NOT_A_PHASE"}, {"status", "XX"}, {"board", "abc"},
                {"group", "abc"}, {"from", "2026-13-01"}, {"sort", "ZZZ"}, {"dir", "UP"}};
        for (String[] c : cases) {
            mvc.perform(get("/provisioning/server").param(c[0], c[1])).andExpect(status().isBadRequest());
        }
        then(queryService).should(never()).findGrouped(any(), any());
        then(queryService).should(never()).findPage(any(), any(), any());
    }
}
