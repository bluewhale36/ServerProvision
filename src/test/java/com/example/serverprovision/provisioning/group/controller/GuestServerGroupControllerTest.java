package com.example.serverprovision.provisioning.group.controller;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.dto.response.GuestServerListResponse;
import com.example.serverprovision.execution.vo.RegistrationAge;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.vo.SpecGroupKey;
import com.example.serverprovision.provisioning.group.dto.response.GroupBadgeResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupDetailResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupMemberResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupSummaryResponse;
import com.example.serverprovision.provisioning.group.dto.response.SeedCandidateResponse;
import com.example.serverprovision.provisioning.group.exception.GroupNameConflictException;
import com.example.serverprovision.provisioning.group.exception.GuestServerGroupNotFoundException;
import com.example.serverprovision.provisioning.group.exception.ServerAlreadyGroupedException;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupCommandService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.GroupAssignmentService;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * U3-4 CP4 — 그룹 화면의 모든 액션을 HTTP 계층에서 검증한다.
 *
 * <p>Mocking 은 서비스 단까지만이라 컨트롤러의 뷰 선택 · 리다이렉트 · {@code BindingResult} 인라인과
 * advice 의 예외 매핑이 실제로 실행된다. 신규 예외 3 종은 전부 여기서 트리거된다 —
 * 예외 클래스만 늘리고 발생 시나리오가 빠진 묶음은 승인 대상이 아니라는 규율 때문이다.</p>
 */
@WebMvcTest(controllers = GuestServerGroupController.class)
class GuestServerGroupControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerGroupQueryService queryService;
    @MockitoBean GuestServerGroupCommandService commandService;
    // U3-5-c — 컨트롤러가 그룹과 할당을 잇게 되면서 늘어난 협력자들(DEC-F).
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean GroupAssignmentService groupAssignmentService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void stubAssignedDefinitions() {
        // 상세가 멤버 표의 '할당된 정의서' 열을 그리려면 이 조회가 답해야 한다.
        given(assignmentQueryService.activeDefinitionNamesOf(anyList())).willReturn(java.util.Map.of());
    }

    private static GuestServerSummaryResponse row(UUID id, String name) {
        return new GuestServerSummaryResponse(
                id, name, UUID.randomUUID(), null, "MS03-CE0", null, null, null,
                LocalDateTime.now(), null, false, null,
                new SpecGroupKey("spec-A"), "MS03-CE0 · 6338 ×2");
    }

    private static GroupDetailResponse detail(List<GroupMemberResponse> members,
                                              boolean diverged,
                                              int candidateCount) {
        return new GroupDetailResponse(7L, "8월 2차", LocalDateTime.now(), members, diverged, candidateCount);
    }

    // ==== 성공 2xx / 3xx ==============================================

    @Test
    @DisplayName("GET /server-group — 목록 200 + 멤버 수가 화면에 나온다")
    void list_returns200() throws Exception {
        given(queryService.findAll()).willReturn(
                List.of(new GroupSummaryResponse(7L, "8월 2차", 3L, true, LocalDateTime.now())));

        mvc.perform(get("/provisioning/server-group"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-group-list"))
                .andExpect(model().attributeExists("groups"))
                .andExpect(content().string(containsString("8월 2차")))
                .andExpect(content().string(containsString("3대")))
                // 구성 혼재는 문장이 아니라 표식으로 알린다 — 자원 화면의 사용 중단과 같은 어휘(개정)
                .andExpect(content().string(containsString("n-deprecated-dot")))
                .andExpect(content().string(containsString("구성 혼재")));
    }

    @Test
    @DisplayName("그룹이 하나도 없으면 만드는 두 경로를 안내한다")
    void list_emptyStateGuidesBothEntryPoints() throws Exception {
        given(queryService.findAll()).willReturn(List.of());

        mvc.perform(get("/provisioning/server-group"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("만들어진 그룹이 없습니다")))
                .andExpect(content().string(containsString("스펙 묶음")))
                .andExpect(content().string(containsString("빈 그룹")));
    }

    @Test
    @DisplayName("GET /new — 씨앗 후보가 사유와 함께 보이고, 막힌 서버는 체크가 잠긴다(DEC-K)")
    void newForm_showsBlockedCandidatesWithReason() throws Exception {
        UUID free = UUID.randomUUID();
        UUID taken = UUID.randomUUID();
        given(queryService.findSeedCandidates(any())).willReturn(List.of(
                new SeedCandidateResponse(row(free, "srv-01"), null, null),
                new SeedCandidateResponse(row(taken, "srv-02"),
                        new GroupBadgeResponse(1L, "8월 1차"), "이미 다른 그룹(8월 1차)에 속해 있습니다.")));

        mvc.perform(get("/provisioning/server-group/new")
                        .param("serverIds", free.toString(), taken.toString())
                        .param("suggested", "MS03-CE0 · 8월 11일 입고"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-group-form"))
                .andExpect(model().attribute("blockedCount", 1L))
                .andExpect(content().string(containsString("MS03-CE0 · 8월 11일 입고")))
                .andExpect(content().string(containsString("이미 다른 그룹에 속해 있어 선택할 수 없습니다")))
                .andExpect(content().string(containsString("disabled")));
    }

    @Test
    @DisplayName("후보표는 운영 상태를 함께 보여준다 — 회수된 서버를 고르기 전에 알 수 있어야 한다(CP5 발견)")
    void candidateTablesShowOperatingStatus() throws Exception {
        UUID decommissioned = UUID.randomUUID();
        GuestServerSummaryResponse retired = new GuestServerSummaryResponse(
                decommissioned, "srv-07", UUID.randomUUID(), null, "MS03-CE0",
                GuestServerStatus.DECOMMISSIONED, null, null,
                LocalDateTime.now(), null, false, null, new SpecGroupKey("spec-A"), "MS03-CE0 · 6338 ×2");

        // ① 생성 폼의 씨앗 후보
        given(queryService.findSeedCandidates(any()))
                .willReturn(List.of(new SeedCandidateResponse(retired, null, null)));
        mvc.perform(get("/provisioning/server-group/new").param("serverIds", decommissioned.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("운영 상태")))
                .andExpect(content().string(containsString(GuestServerStatus.DECOMMISSIONED.getDescription())));

        // ② 서버 넣기 모달의 후보 조각
        given(queryService.findDetail(7L)).willReturn(detail(List.of(), false, 1));
        given(queryService.findCandidateGroups()).willReturn(new GuestServerListResponse(
                null, List.of(new GuestServerListResponse.TimeGroup(
                        new RegistrationAge(RegistrationAge.Unit.DAY, 2L),
                        List.of(new GuestServerListResponse.SpecGroup(
                                new SpecGroupKey("spec-A"), "MS03-CE0 · 6338 ×2", List.of(retired)))))));

        mvc.perform(get("/provisioning/server-group/7/candidates"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("운영 상태")))
                .andExpect(content().string(containsString(GuestServerStatus.DECOMMISSIONED.getDescription())));
    }

    @Test
    @DisplayName("후보 조각은 서버 목록과 같은 방식으로 묶인다 — 시간 구간과 '스펙 N' 이 그대로 나온다(개정)")
    void candidateFragmentGroupsLikeServerList() throws Exception {
        given(queryService.findDetail(7L)).willReturn(detail(List.of(), false, 2));
        given(queryService.findCandidateGroups()).willReturn(new GuestServerListResponse(
                null, List.of(new GuestServerListResponse.TimeGroup(
                        new RegistrationAge(RegistrationAge.Unit.HOUR, 3L),
                        List.of(new GuestServerListResponse.SpecGroup(
                                new SpecGroupKey("spec-B"), "MS03-CE0 · 4310 ×1",
                                List.of(row(UUID.randomUUID(), "srv-05"),
                                        row(UUID.randomUUID(), "srv-06"))))))));

        mvc.perform(get("/provisioning/server-group/7/candidates"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("3시간 전")))
                .andExpect(content().string(containsString("스펙 1")))
                .andExpect(content().string(containsString("MS03-CE0 · 4310 ×1")))
                .andExpect(content().string(containsString("2대")))
                .andExpect(content().string(containsString("srv-05")));
    }

    @Test
    @DisplayName("없는 그룹의 후보 조각은 404 — 모달이 빈 목록을 보여주지 않는다")
    void candidateFragmentOfUnknownGroupIs404() throws Exception {
        willThrow(new GuestServerGroupNotFoundException(99L)).given(queryService).findDetail(99L);

        mvc.perform(get("/provisioning/server-group/99/candidates")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /new — 씨앗이 없으면 빈 그룹 만들기가 되고 그렇게 안내한다(DEC-J)")
    void newForm_withoutSeedIsEmptyGroup() throws Exception {
        given(queryService.findSeedCandidates(any())).willReturn(List.of());

        mvc.perform(get("/provisioning/server-group/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("멤버 없이 만듭니다")));
    }

    @Test
    @DisplayName("POST /server-group — 생성 성공 302 → 상세")
    void create_redirectsToDetail() throws Exception {
        given(queryService.nameConflictReason(eq("8월 2차"), eq(null))).willReturn(null);
        given(commandService.create(eq("8월 2차"), anyCollection())).willReturn(7L);

        mvc.perform(post("/provisioning/server-group").param("name", "8월 2차"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server-group/7"));
    }

    @Test
    @DisplayName("GET /{id} — 상세 200 + 멤버와 후보가 나온다")
    void detail_returns200() throws Exception {
        UUID member = UUID.randomUUID();
        given(queryService.findDetail(7L)).willReturn(detail(
                List.of(new GroupMemberResponse(row(member, "srv-01"), false)), false, 0));

        mvc.perform(get("/provisioning/server-group/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-group-detail"))
                .andExpect(model().attributeExists("group", "renameForm"))
                .andExpect(content().string(containsString("srv-01")));
    }

    /**
     * 목록으로 돌아갈 때 방금 본 행을 짚을 수 있어야 한다.
     *
     * <p>행은 {@code data-return-key} 로 자기를 밝히고 돌아갈 링크가 {@code returned} 를 싣는다 —
     * 서버 목록과 같은 규약이라 목록이 늘어도 스크립트를 고칠 일이 없다(개정).</p>
     */
    /**
     * 되돌아가기는 URL 파라미터가 아니라 화면 이력 스택이 맡는다(재구성).
     * 화면이 지킬 것은 마크업 규약 둘 — 상세의 {@code data-nav-back}, 목록 행의 {@code data-nav-key}.
     */
    @Test
    @DisplayName("상세의 '목록으로' 와 목록 행이 이력 스택 규약을 지킨다")
    void navStackConvention() throws Exception {
        given(queryService.findDetail(7L)).willReturn(detail(List.of(), false, 0));
        mvc.perform(get("/provisioning/server-group/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-nav-back")))
                .andExpect(content().string(containsString("href=\"/provisioning/server-group\"")));

        given(queryService.findAll()).willReturn(
                List.of(new GroupSummaryResponse(7L, "8월 2차", 3L, false, LocalDateTime.now())));
        mvc.perform(get("/provisioning/server-group"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-nav-key=\"7\"")));
    }

    @Test
    @DisplayName("GET /{id} — 구성이 갈리면 배너로 알리되 조작을 막지 않는다(DEC-I)")
    void detail_showsDivergenceAsNotice() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        given(queryService.findDetail(7L)).willReturn(detail(
                List.of(new GroupMemberResponse(row(a, "srv-01"), false),
                        new GroupMemberResponse(row(b, "srv-02"), true)),
                true, 0));

        mvc.perform(get("/provisioning/server-group/7"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("구성이 다른 서버")))
                .andExpect(content().string(containsString("그대로 두셔도 됩니다")))   // 차단이 아님을 문구가 말한다
                .andExpect(content().string(containsString("빼기")))                  // 조작은 그대로 열려 있다
                // 개정 — 문장만으로 알리지 않는다. 제목 표식과 겉도는 행의 표식이 함께 나온다
                .andExpect(content().string(containsString("n-title-badge")))
                .andExpect(content().string(containsString("n-row-diverged")))
                .andExpect(content().string(containsString("다수와 구성이 다릅니다")));
    }

    @Test
    @DisplayName("POST /{id}/members — 멤버 추가 302 → 상세")
    void addMembers_redirects() throws Exception {
        mvc.perform(post("/provisioning/server-group/7/members")
                        .param("serverIds", UUID.randomUUID().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server-group/7"));
    }

    @Test
    @DisplayName("POST /{id}/members/{serverId}/remove — 제외 302 → 상세")
    void removeMember_redirects() throws Exception {
        mvc.perform(post("/provisioning/server-group/7/members/{sid}/remove", UUID.randomUUID()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server-group/7"));
    }

    @Test
    @DisplayName("POST /{id}/rename — 이름 변경 302 → 상세")
    void rename_redirects() throws Exception {
        given(queryService.nameConflictReason(eq("새 이름"), eq(7L))).willReturn(null);

        mvc.perform(post("/provisioning/server-group/7/rename").param("name", "새 이름"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server-group/7"));
    }

    @Test
    @DisplayName("POST /{id}/delete — 삭제 302 → 목록")
    void delete_redirectsToList() throws Exception {
        mvc.perform(post("/provisioning/server-group/7/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server-group"));
    }

    // ==== 400 — 폼 재렌더 + 필드 오류 ==================================

    @Test
    @DisplayName("생성 — 이름이 비면 400 없이 폼을 다시 그리고 입력칸 옆에 사유를 붙인다")
    void create_blankNameReRendersForm() throws Exception {
        given(queryService.findSeedCandidates(any())).willReturn(List.of());

        mvc.perform(post("/provisioning/server-group").param("name", " "))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-group-form"))
                .andExpect(model().attributeHasFieldErrors("form", "name"))
                .andExpect(content().string(containsString("그룹 이름을 입력하세요")));
    }

    @Test
    @DisplayName("생성 — 128자를 넘으면 같은 방식으로 되돌린다")
    void create_tooLongNameReRendersForm() throws Exception {
        given(queryService.findSeedCandidates(any())).willReturn(List.of());

        mvc.perform(post("/provisioning/server-group").param("name", "가".repeat(129)))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name"))
                .andExpect(content().string(containsString("128자 이하")));
    }

    @Test
    @DisplayName("이름 변경 — 비면 상세를 다시 그려 사유를 붙인다")
    void rename_blankNameReRendersDetail() throws Exception {
        given(queryService.findDetail(7L)).willReturn(detail(List.of(), false, 0));

        mvc.perform(post("/provisioning/server-group/7/rename").param("name", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-group-detail"))
                .andExpect(model().attributeHasFieldErrors("renameForm", "name"));
    }

    // ==== 409 — 신규 ConflictException 전부 트리거 =====================

    @Test
    @DisplayName("생성 — 이름 중복은 예외가 아니라 필드 오류로 되돌아온다(화면 1차 차단)")
    void create_duplicateNameBecomesFieldError() throws Exception {
        given(queryService.nameConflictReason(eq("8월 2차"), eq(null)))
                .willReturn("같은 이름의 그룹이 이미 있습니다: 8월 2차");
        given(queryService.findSeedCandidates(any())).willReturn(List.of());

        mvc.perform(post("/provisioning/server-group").param("name", "8월 2차"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "name"))
                .andExpect(content().string(containsString("같은 이름의 그룹이 이미 있습니다")));
    }

    @Test
    @DisplayName("생성 — 화면을 우회한 동시 생성은 서비스 가드가 409 로 막는다")
    void create_directPostDuplicateIs409() throws Exception {
        given(queryService.nameConflictReason(any(), any())).willReturn(null);   // 사전 검사는 통과
        willThrow(new GroupNameConflictException("8월 2차"))
                .given(commandService).create(eq("8월 2차"), anyCollection());

        mvc.perform(post("/provisioning/server-group").param("name", "8월 2차"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("멤버 추가 — 이미 다른 그룹 소속이면 409 이고, 사유에 그 그룹 이름이 담긴다")
    void addMembers_alreadyGroupedIs409() throws Exception {
        willThrow(new ServerAlreadyGroupedException("이미 다른 그룹(8월 1차)에 속해 있습니다. 옮기려면 그 그룹에서 먼저 빼주세요."))
                .given(commandService).addMembers(eq(7L), anyCollection());

        mvc.perform(post("/provisioning/server-group/7/members")
                        .param("serverIds", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("8월 1차")));
    }

    // ==== 404 — 없는 그룹 · 없는 서버 ==================================

    @Test
    @DisplayName("없는 그룹 상세는 404")
    void detail_unknownGroupIs404() throws Exception {
        willThrow(new GuestServerGroupNotFoundException(99L)).given(queryService).findDetail(99L);

        mvc.perform(get("/provisioning/server-group/99")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("없는 그룹에 멤버 추가 · 제외 · 이름 변경 · 삭제는 모두 404")
    void unknownGroupActionsAre404() throws Exception {
        willThrow(new GuestServerGroupNotFoundException(99L))
                .given(commandService).addMembers(eq(99L), anyCollection());
        willThrow(new GuestServerGroupNotFoundException(99L))
                .given(commandService).removeMember(eq(99L), any());
        willThrow(new GuestServerGroupNotFoundException(99L))
                .given(commandService).rename(eq(99L), any());
        willThrow(new GuestServerGroupNotFoundException(99L))
                .given(commandService).delete(99L);
        given(queryService.nameConflictReason(any(), any())).willReturn(null);

        mvc.perform(post("/provisioning/server-group/99/members")
                .param("serverIds", UUID.randomUUID().toString())).andExpect(status().isNotFound());
        mvc.perform(post("/provisioning/server-group/99/members/{sid}/remove", UUID.randomUUID()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/provisioning/server-group/99/rename").param("name", "이름"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/provisioning/server-group/99/delete")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("없는 서버를 멤버로 넣으면 404 — 게스트 도메인의 기존 예외를 재사용한다")
    void addMembers_unknownServerIs404() throws Exception {
        UUID missing = UUID.randomUUID();
        willThrow(new GuestServerNotFoundException(missing))
                .given(commandService).addMembers(eq(7L), anyCollection());

        mvc.perform(post("/provisioning/server-group/7/members").param("serverIds", missing.toString()))
                .andExpect(status().isNotFound());
    }
}
