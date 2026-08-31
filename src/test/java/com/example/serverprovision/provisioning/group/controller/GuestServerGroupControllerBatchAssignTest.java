package com.example.serverprovision.provisioning.group.controller;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.BatchAssignResult;
import com.example.serverprovision.provisioning.assignment.dto.response.GroupApplyPreviewResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.MemberOutcomeResponse;
import com.example.serverprovision.provisioning.assignment.enums.MemberApplyOutcome;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.GroupAssignmentService;
import com.example.serverprovision.provisioning.group.dto.response.GroupDetailResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupMemberResponse;
import com.example.serverprovision.provisioning.group.exception.GuestServerGroupNotFoundException;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupCommandService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
import com.example.serverprovision.provisioning.setting.dto.request.BasicUpdateRequest;
import com.example.serverprovision.provisioning.setting.dto.request.BoardModelSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.request.FirmwareSelectionRequest;
import com.example.serverprovision.provisioning.setting.dto.response.ReferenceNamesResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.BoardModelSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.FirmwareSelectionMode;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U3-5-c CP4 — 그룹 정의서 일괄 할당의 조각 렌더와 실행 경로 (HTTP 계층).
 *
 * <p>Mocking 은 서비스 단까지다 — 조각 뷰 이름 해석 · Thymeleaf 렌더 · advice 예외 매핑은 실제로
 * 실행된다. 조각 참조가 어긋나거나 미리보기 표가 비면 컴파일이 아니라 여기서 드러난다.</p>
 *
 * <p>실행 경로에서 함께 못 박는 것은 <b>대상 선별을 서버가 다시 한다</b>는 것이다 — 화면이 보낸 목록을
 * 그대로 믿지 않고 같은 미리보기를 다시 만들어 붙는 멤버를 고른다. 그래야 화면이 알린 것과 실제로 하는
 * 것이 같은 판정에서 나온다.</p>
 */
@WebMvcTest(controllers = GuestServerGroupController.class)
class GuestServerGroupControllerBatchAssignTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerGroupQueryService queryService;
    @MockitoBean GuestServerGroupCommandService commandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean GroupAssignmentService groupAssignmentService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final long GROUP = 7L;
    private static final UUID SRV_OK = UUID.randomUUID();
    private static final UUID SRV_BLOCKED = UUID.randomUUID();
    private static final String MISMATCH =
            "이 정의서는 메인보드 MS03-CE0 전용입니다 — 이 서버는 ASUS-Z13PE 입니다.";

    @BeforeEach
    void stubAssignedDefinitions() {
        given(assignmentQueryService.activeDefinitionNamesOf(anyList())).willReturn(Map.of());
    }

    private static GuestServerSummaryResponse server(UUID id, String name, String board) {
        return new GuestServerSummaryResponse(id, name, UUID.randomUUID(), null, board,
                null, null, null, LocalDateTime.now(), null, false, null,false,  null, null);
    }

    private static GroupDetailResponse group(List<GroupMemberResponse> members) {
        // 표준 정의서 없음(U3-5-d) — 이 파일이 보는 것은 일괄 할당이고 표준은 그 경로에 관여하지 않는다.
        return new GroupDetailResponse(GROUP, "8월 A동 1차", LocalDateTime.now(), null, members, false, 0);
    }

    private void givenTwoMemberGroup() {
        given(queryService.findDetail(GROUP)).willReturn(group(List.of(
                new GroupMemberResponse(server(SRV_OK, "srv-ms03", "MS03-CE0"), false),
                new GroupMemberResponse(server(SRV_BLOCKED, "srv-asus", "ASUS-Z13PE"), false))));
    }

    private static SettingSummaryResponse summary(long id, String name) {
        return new SettingSummaryResponse(id, name, List.of(SettingProcessType.BASIC_UPDATE),
                false, true, false, LocalDateTime.now(), null, null);
    }

    /** 단계 하나를 실은 상세 — 우측 패널이 카드 조각을 실제로 그리는지 보려면 빈 목록으로는 안 된다. */
    private static SettingDetailResponse detail(long id, String name) {
        return new SettingDetailResponse(id, name, false, true, false, 0L,
                List.of(new BasicUpdateRequest(
                        new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, 7L),
                        new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                        new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null))),
                List.of(), List.of(),
                new ReferenceNamesResponse(Map.of(7L, "MS03-CE0"),
                        Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()),
                LocalDateTime.now(), LocalDateTime.now());
    }

    /** 정의서 2 종 — 하나는 한 대에 붙고, 하나는 아무에게도 안 붙는다. */
    private void givenTwoDefinitions() {
        given(settingQueryService.findAssignable())
                .willReturn(List.of(summary(1L, "os-only-auto"), summary(2L, "bios-ms03")));
        given(assignmentQueryService.groupPreview(anyList(), anyList())).willReturn(List.of(
                new GroupApplyPreviewResponse(summary(1L, "os-only-auto"), List.of(
                        new MemberOutcomeResponse(server(SRV_OK, "srv-ms03", "MS03-CE0"),
                                MemberApplyOutcome.WILL_ASSIGN, null),
                        new MemberOutcomeResponse(server(SRV_BLOCKED, "srv-asus", "ASUS-Z13PE"),
                                MemberApplyOutcome.ALREADY_ASSIGNED, "이미 세팅 정의서가 할당되어 있습니다."))),
                new GroupApplyPreviewResponse(summary(2L, "bios-ms03"), List.of(
                        new MemberOutcomeResponse(server(SRV_OK, "srv-ms03", "MS03-CE0"),
                                MemberApplyOutcome.BLOCKED, MISMATCH),
                        new MemberOutcomeResponse(server(SRV_BLOCKED, "srv-asus", "ASUS-Z13PE"),
                                MemberApplyOutcome.BLOCKED, MISMATCH)))));
        given(settingQueryService.findDetailsOf(anyList()))
                .willReturn(List.of(detail(1L, "os-only-auto"), detail(2L, "bios-ms03")));
    }

    // ==== 성공 2xx — 조각 =============================================

    @Test
    @DisplayName("GET /{id}/assignment/picker — 좌측에 붙는 수, 우측에 멤버별 분류가 나온다")
    void picker_rendersCountsAndMemberBreakdown() throws Exception {
        givenTwoMemberGroup();
        givenTwoDefinitions();

        mvc.perform(get("/provisioning/server-group/{id}/assignment/picker", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">os-only-auto<")))
                // 분모(멤버 수)는 표 밖에서 한 번만 — 좌측 행은 붙는 수만 적는다(OQ-2)
                .andExpect(content().string(containsString(">1 대<")))
                .andExpect(content().string(containsString("2 대 중 1 대에 할당됩니다")))
                // 멤버별 분류 표가 실제로 그려지는가 — 분류 배지와 사유가 함께
                .andExpect(content().string(containsString("할당됨")))
                .andExpect(content().string(containsString("이미 있음")))
                .andExpect(content().string(containsString("srv-asus")));
    }

    @Test
    @DisplayName("GET /{id}/assignment/picker — 아무에게도 안 붙는 정의서는 잠기고 사유가 모인다")
    void picker_marksDefinitionThatAppliesToNobody() throws Exception {
        givenTwoMemberGroup();
        givenTwoDefinitions();

        mvc.perform(get("/provisioning/server-group/{id}/assignment/picker", GROUP))
                .andExpect(status().isOk())
                // 목록에서 지우지 않는다 — 사라지면 왜 안 되는지 알 수 없다
                .andExpect(content().string(containsString(">bios-ms03<")))
                .andExpect(content().string(containsString("n-miller-item-disabled")))
                .andExpect(content().string(containsString("이 그룹의 어떤 서버에도 붙일 수 없습니다")))
                .andExpect(content().string(containsString(MISMATCH)));
    }

    @Test
    @DisplayName("GET /{id}/assignment/picker — 우측 카드는 정의서 상세와 같은 조각이 그린다")
    void picker_rendersProcessCardsFragment() throws Exception {
        givenTwoMemberGroup();
        givenTwoDefinitions();

        mvc.perform(get("/provisioning/server-group/{id}/assignment/picker", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("n-process-card")))
                .andExpect(content().string(containsString(SettingProcessType.BASIC_UPDATE.getDisplayName())))
                .andExpect(content().string(containsString("MS03-CE0")));
    }

    // ==== 성공 3xx — 실행 =============================================

    @Test
    @DisplayName("POST /{id}/assignment — 붙는 멤버에만 붙이고 결과를 flash 로 알린다")
    void assignBatch_appliesToTargetsAndFlashesResult() throws Exception {
        givenTwoMemberGroup();
        givenTwoDefinitions();
        given(groupAssignmentService.assignToMembers(any(GroupApplyPreviewResponse.class), eq(1L)))
                .willReturn(new BatchAssignResult("os-only-auto", 1, 1, "이미 있음 1"));

        mvc.perform(post("/provisioning/server-group/{id}/assignment", GROUP)
                        .param("definitionId", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server-group/" + GROUP))
                .andExpect(flash().attribute("flashMessage",
                        containsString("'os-only-auto' 를 1 대에 할당했습니다")));

        // 대상 선별은 서버가 다시 한다 — 화면이 보낸 목록이 아니라 미리보기가 고른 것이 넘어간다
        org.mockito.ArgumentCaptor<GroupApplyPreviewResponse> captor =
                org.mockito.ArgumentCaptor.forClass(GroupApplyPreviewResponse.class);
        verify(groupAssignmentService).assignToMembers(captor.capture(), eq(1L));
        assertThat(captor.getValue().targetServerIds()).containsExactly(SRV_OK);
    }

    @Test
    @DisplayName("POST /{id}/assignment — 아무에게도 안 붙는 정의서면 빈 대상으로 부른다(붙는 것이 없다)")
    void assignBatch_withNoTargetsAssignsNothing() throws Exception {
        givenTwoMemberGroup();
        givenTwoDefinitions();
        given(groupAssignmentService.assignToMembers(any(GroupApplyPreviewResponse.class), eq(2L)))
                .willReturn(new BatchAssignResult("bios-ms03", 0, 0, ""));

        mvc.perform(post("/provisioning/server-group/{id}/assignment", GROUP)
                        .param("definitionId", "2"))
                .andExpect(status().is3xxRedirection());

        org.mockito.ArgumentCaptor<GroupApplyPreviewResponse> captor =
                org.mockito.ArgumentCaptor.forClass(GroupApplyPreviewResponse.class);
        verify(groupAssignmentService).assignToMembers(captor.capture(), eq(2L));
        assertThat(captor.getValue().targetServerIds()).isEmpty();
    }

    // ==== 404 / 400 ==================================================

    @Test
    @DisplayName("GET /{id}/assignment/picker — 없는 그룹 404")
    void picker_unknownGroup_returns404() throws Exception {
        willThrow(new GuestServerGroupNotFoundException(99L)).given(queryService).findDetail(99L);

        mvc.perform(get("/provisioning/server-group/{id}/assignment/picker", 99L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{id}/assignment — 선택지에 없는 정의서 404 (삭제 · 비활성 포함)")
    void assignBatch_unknownDefinition_returns404() throws Exception {
        givenTwoMemberGroup();
        givenTwoDefinitions();

        mvc.perform(post("/provisioning/server-group/{id}/assignment", GROUP)
                        .param("definitionId", "999"))
                .andExpect(status().isNotFound());

        verify(groupAssignmentService, never()).assignToMembers(any(), any());
    }

    @Test
    @DisplayName("POST /{id}/assignment — definitionId 없이 제출하면 400 (HTML 오류 페이지)")
    void assignBatch_missingDefinitionId_returns400() throws Exception {
        mvc.perform(post("/provisioning/server-group/{id}/assignment", GROUP))
                .andExpect(status().isBadRequest());

        verify(groupAssignmentService, never()).assignToMembers(any(), any());
    }

    // ==== 멤버 0 인 그룹 ==============================================

    /**
     * U3-5-d 가 이 계약의 <b>절반을 뒤집었다</b>. U3-5-c 는 "멤버가 없으면 버튼도 모달도 내지 않는다"
     * 였는데, U3-5-d 의 표준 지정이 <b>빈 그룹에서 하는 일</b>이라 모달까지 감추면 핵심 유스케이스가
     * 닿지 않는다(DEC-B — 그룹을 미리 만들어 두고 정책부터 정하기).
     *
     * <p>그래서 <b>모달은 남기고 일괄 할당 버튼만 감춘다</b>. 감추는 이유는 그대로다 — 붙일 서버가
     * 없는데 여는 화면은 조작할 것이 없다. 표준 지정 버튼은 그 사정이 아니므로 남는다.</p>
     */
    @Test
    @DisplayName("멤버가 없는 그룹 — 일괄 할당 버튼만 감추고 표준 지정 경로는 남는다 (U3-5-d DEC-B)")
    void detail_hidesBatchButtonButKeepsStandardPickerWhenGroupIsEmpty() throws Exception {
        given(queryService.findDetail(GROUP)).willReturn(group(List.of()));

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("openGroupDefinitionPicker"))))
                // 표준 지정은 빈 그룹에서 하는 일이다 — 버튼도 모달도 렌더된다
                .andExpect(content().string(containsString("openGroupStandardPicker")))
                .andExpect(content().string(containsString("id=\"groupDefinitionPicker\"")));
    }

    @Test
    @DisplayName("멤버가 있는 그룹 — 버튼과 모달이 함께 렌더된다")
    void detail_showsBatchButtonWhenGroupHasMembers() throws Exception {
        givenTwoMemberGroup();

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openGroupDefinitionPicker")))
                .andExpect(content().string(containsString("/assignment/picker")))
                // 결과를 flash 로 알려야 하므로 네이티브 제출이다(DEC-E)
                .andExpect(content().string(containsString("data-native-submit")));
    }
}
