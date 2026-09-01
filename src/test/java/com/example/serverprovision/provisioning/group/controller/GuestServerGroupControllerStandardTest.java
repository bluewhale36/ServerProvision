package com.example.serverprovision.provisioning.group.controller;

import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.dto.response.GroupApplyPreviewResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.StandardApplyBannerResponse;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.GroupAssignmentService;
import com.example.serverprovision.provisioning.group.dto.response.GroupDetailResponse;
import com.example.serverprovision.provisioning.group.dto.response.GroupMemberResponse;
import com.example.serverprovision.provisioning.group.exception.GuestServerGroupNotFoundException;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupCommandService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
import com.example.serverprovision.provisioning.setting.dto.response.ReferenceNamesResponse;
import com.example.serverprovision.provisioning.setting.dto.response.ReferencedDefinitionResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingDetailResponse;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.DefinitionNotAssignableException;
import com.example.serverprovision.provisioning.setting.exception.SettingNotFoundException;
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
 * U3-5-d CP4 — 그룹 표준 정의서의 화면과 실행 경로 (HTTP 계층).
 *
 * <p>Mocking 은 서비스 단까지다 — 뷰 이름 해석 · Thymeleaf 렌더 · advice 예외 매핑은 실제로 실행된다.
 * 표준 절의 세 상태(없음 · 쓸 수 있음 · 쓸 수 없음)가 실제로 다른 화면을 그리는지, 그리고 안내 배너가
 * 할 일이 있을 때만 뜨는지를 렌더 결과로 확인한다.</p>
 *
 * <p><b>멤버가 없는 그룹</b>을 특히 본다. U3-5-c 는 조작할 것이 없는 화면을 열어 주지 않는다는 이유로
 * 멤버 0 이면 모달을 렌더하지 않았는데, U3-5-d 의 표준 지정은 <b>빈 그룹에서 하는 일</b>이라 그대로
 * 두면 핵심 유스케이스가 닿지 않는다(DEC-B).</p>
 */
@WebMvcTest(controllers = GuestServerGroupController.class)
class GuestServerGroupControllerStandardTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerGroupQueryService queryService;
    @MockitoBean GuestServerGroupCommandService commandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean GroupAssignmentService groupAssignmentService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final long GROUP = 7L;
    private static final long DEFINITION = 11L;
    private static final String DISABLED_REASON =
            "비활성화된 정의서는 신규 할당이 차단됩니다(활성화 후 재시도)";

    @BeforeEach
    void stubAssignedDefinitions() {
        given(assignmentQueryService.activeDefinitionNamesOf(anyList())).willReturn(Map.of());
    }

    private static GuestServerSummaryResponse server(String name) {
        return new GuestServerSummaryResponse(UUID.randomUUID(), name, UUID.randomUUID(), null, "MS03-CE0",
                null, null, null, LocalDateTime.now(), null, false, null,false,  null, null);
    }

    private static SettingSummaryResponse summary(String name) {
        return new SettingSummaryResponse(DEFINITION, name, List.of(SettingProcessType.OS_INSTALLATION),
                false, true, false, LocalDateTime.now(), null, null);
    }

    private static SettingDetailResponse detail(String name) {
        return new SettingDetailResponse(DEFINITION, name, false, true, false, 0L,
                List.of(), List.of(), List.of(), ReferenceNamesResponse.empty(),
                LocalDateTime.now(), LocalDateTime.now());
    }

    /** 멤버 둘 · 표준 id 는 인자대로. */
    private void givenGroup(Long standardDefinitionId) {
        given(queryService.findDetail(GROUP)).willReturn(new GroupDetailResponse(
                GROUP, "8월 A동 1차", LocalDateTime.now(), standardDefinitionId,
                List.of(new GroupMemberResponse(server("srv-01"), false),
                        new GroupMemberResponse(server("srv-02"), false)),
                false, 0));
    }

    private void givenEmptyGroup(Long standardDefinitionId) {
        given(queryService.findDetail(GROUP)).willReturn(new GroupDetailResponse(
                GROUP, "8월 A동 1차", LocalDateTime.now(), standardDefinitionId,
                List.of(), false, 0));
    }

    /** 표준이 지금 쓸 수 있는 상태 + 배너 대상 수. */
    private void givenUsableStandard(String name, int targetCount) {
        given(settingQueryService.resolveReference(DEFINITION))
                .willReturn(new ReferencedDefinitionResponse(DEFINITION, summary(name), null));
        given(assignmentQueryService.standardApplyBanner(anyList(), any()))
                // 멤버 2 대 그룹 기준 — 대상 외 수와 사유 내역도 배너가 함께 싣는다(개정)
                .willReturn(new StandardApplyBannerResponse(
                        DEFINITION, name, targetCount, 2, targetCount < 2 ? "이미 있음 " + (2 - targetCount) : ""));
    }

    // ==== 상세 렌더 : 표준의 세 상태 ==================================

    @Test
    @DisplayName("표준 없음 — 안내와 [표준 정하기] 가 뜨고 배너는 계산조차 하지 않는다")
    void detail_noStandard() throws Exception {
        givenGroup(null);

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("표준 세팅 정의서")))
                .andExpect(content().string(containsString("아직 정하지 않았습니다")))
                .andExpect(content().string(containsString("openGroupStandardPicker")))
                .andExpect(content().string(containsString("표준 정하기")));

        // 표준이 없으면 셀 대상도 없다 — 상세를 그릴 때마다 치르는 값이라 부르지 않아야 한다
        verify(assignmentQueryService, never()).standardApplyBanner(anyList(), any());
    }

    @Test
    @DisplayName("표준 있음 · 대상 2 대 — 배너가 이름과 수를 함께 알리고 [표준 적용] 을 낸다")
    void detail_usableStandardWithTargets() throws Exception {
        givenGroup(DEFINITION);
        givenUsableStandard("web-standard", 2);

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("web-standard")))
                // 붙는 수를 미리 읽고 누르므로 '부분 적용을 미리 알고 승인' 이 유지된다.
                // 문구는 "아직 적용받지 않은" 이 아니라 <b>"지금 붙일 수 있는"</b> 이어야 한다 —
                // 다른 정의서가 붙어 있는 서버는 표준을 안 따르는데도 이 수에서 빠지기 때문이다(개정).
                .andExpect(content().string(containsString("지금 붙일 수 있습니다")))
                .andExpect(content().string(containsString(">2</b>대에 표준 정의서")))
                .andExpect(content().string(containsString("표준 적용")))
                .andExpect(content().string(containsString("표준 바꾸기")));
    }

    @Test
    @DisplayName("표준 있음 · 대상 0 — 배너는 감추고 왜 없는지는 알린다 (OQ-2)")
    void detail_usableStandardWithoutTargets() throws Exception {
        givenGroup(DEFINITION);
        givenUsableStandard("web-standard", 0);

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("표준 적용"))))
                .andExpect(content().string(containsString("지금 표준을 적용할 서버가 없습니다")));
    }

    @Test
    @DisplayName("표준이 비활성 — 사용 불가 표시와 사유가 뜨고 적용 경로가 열리지 않는다 (R5)")
    void detail_unusableStandard() throws Exception {
        givenGroup(DEFINITION);
        given(settingQueryService.resolveReference(DEFINITION))
                .willReturn(new ReferencedDefinitionResponse(DEFINITION, summary("web-standard"), DISABLED_REASON));

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("사용 불가")))
                .andExpect(content().string(containsString("이 표준은 지금 쓸 수 없습니다")))
                .andExpect(content().string(containsString(DISABLED_REASON)))
                .andExpect(content().string(not(containsString("표준 적용"))));

        // 붙일 수 없는 정의서의 '대상 N 대' 는 누를 수 없는 수라 계산할 값이 아니다
        verify(assignmentQueryService, never()).standardApplyBanner(anyList(), any());
    }

    @Test
    @DisplayName("표준이 가리키던 정의서가 사라짐 — 절이 그대로 남아 [해제] 로 빠져나갈 수 있다")
    void detail_goneStandardStillOffersEscape() throws Exception {
        givenGroup(DEFINITION);
        given(settingQueryService.resolveReference(DEFINITION))
                .willReturn(ReferencedDefinitionResponse.gone(DEFINITION));

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("사용 불가")))
                .andExpect(content().string(containsString("해제")))
                .andExpect(content().string(containsString("/standard-definition/clear")));
    }

    @Test
    @DisplayName("배너가 대상 외 멤버 수와 사유 내역을 함께 말한다 — 수만 말하면 오해를 부른다 (개정)")
    void detail_bannerTellsWhatItCounts() throws Exception {
        givenGroup(DEFINITION);
        given(settingQueryService.resolveReference(DEFINITION))
                .willReturn(new ReferencedDefinitionResponse(DEFINITION, summary("web-standard"), null));
        // 멤버 2 대 중 1 대만 붙고 1 대는 이미 다른 정의서가 있다
        given(assignmentQueryService.standardApplyBanner(anyList(), any()))
                .willReturn(new StandardApplyBannerResponse(DEFINITION, "web-standard", 1, 2, "이미 있음 1"));

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                // 분모(멤버 수) · 붙는 수 · 빠지는 수 · 사유가 모두 화면에 나와야 한다.
                // 붙는 수만 말하면 "나머지는 표준을 따르고 있다" 로 읽힌다 — 실제로는 아니다.
                .andExpect(content().string(containsString(">2</b>대 중")))
                .andExpect(content().string(containsString(">1</b>대에 표준 정의서")))
                .andExpect(content().string(containsString("대상이 아닙니다")))
                .andExpect(content().string(containsString("이미 있음 1")));
    }

    @Test
    @DisplayName("표준 이름 옆에 밟게 될 단계가 순서대로 붙는다 — 이름만으로는 무엇을 하는지 알 수 없다")
    void detail_showsPlannedPhasesOfStandard() throws Exception {
        givenGroup(DEFINITION);
        givenUsableStandard("web-standard", 2);
        given(assignmentQueryService.phasesOfDefinition(any())).willReturn(List.of(
                ProvisioningPhase.DIAGNOSE_LINUX, ProvisioningPhase.OS_INSTALLING));

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("n-phase-chip")))
                .andExpect(content().string(containsString("진단 리눅스")))
                .andExpect(content().string(containsString("OS 설치")))
                // 이름은 꾸미지 않은 텍스트 링크다 — 누르면 그 정의서 상세로 간다(개정 2회차)
                .andExpect(content().string(containsString("/provisioning/setting/" + DEFINITION)));
    }

    @Test
    @DisplayName("가리키던 정의서가 사라졌으면 이름을 링크하지 않는다 — 열 곳이 없는 링크는 404 로 보낸다")
    void detail_goneStandardIsNotLinked() throws Exception {
        givenGroup(DEFINITION);
        given(settingQueryService.resolveReference(DEFINITION))
                .willReturn(ReferencedDefinitionResponse.gone(DEFINITION));

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                // 이름 자리에 id 는 남아 무엇을 해제하려는지 알 수 있다
                .andExpect(content().string(containsString("사라진 정의서")))
                // 그러나 그 정의서 상세로 가는 링크는 없다
                .andExpect(content().string(not(containsString("/provisioning/setting/" + DEFINITION))));
    }

    // ==== 멤버 0 인 그룹 — DEC-B 의 핵심 ==============================

    @Test
    @DisplayName("빈 그룹에서도 표준 지정 버튼과 모달이 렌더된다 (R3 · DEC-B)")
    void detail_emptyGroupStillOffersStandardPicker() throws Exception {
        givenEmptyGroup(null);

        mvc.perform(get("/provisioning/server-group/{id}", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("openGroupStandardPicker")))
                .andExpect(content().string(containsString("id=\"groupDefinitionPicker\"")))
                // 일괄 할당은 붙일 서버가 없으므로 여전히 감춘다
                .andExpect(content().string(not(containsString("openGroupDefinitionPicker"))));
    }

    @Test
    @DisplayName("빈 그룹의 모달 조각도 200 이다 — 목록은 오고 미리보기가 '서버가 없다' 고 말한다")
    void picker_emptyGroupRendersFragment() throws Exception {
        givenEmptyGroup(null);
        given(settingQueryService.findAssignable()).willReturn(List.of(summary("web-standard")));
        given(assignmentQueryService.groupPreview(anyList(), anyList()))
                .willReturn(List.of(new GroupApplyPreviewResponse(summary("web-standard"), List.of())));
        given(settingQueryService.findDetailsOf(anyList())).willReturn(List.of(detail("web-standard")));

        mvc.perform(get("/provisioning/server-group/{id}/assignment/picker", GROUP))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("web-standard")))
                // '붙일 수 없다' 가 아니라 '서버가 없다' — 원인을 정의서 탓으로 돌리지 않는다
                .andExpect(content().string(containsString("이 그룹에는 아직 서버가 없습니다")));
    }

    // ==== 지정 · 해제 실행 ============================================

    @Test
    @DisplayName("지정 302 — flash 가 이름과 '자동으로 붙지 않는다' 를 함께 알린다")
    void setStandard_redirectsWithFlash() throws Exception {
        given(commandService.setStandardDefinition(GROUP, DEFINITION)).willReturn("web-standard");

        mvc.perform(post("/provisioning/server-group/{id}/standard-definition", GROUP)
                        .param("definitionId", String.valueOf(DEFINITION)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server-group/" + GROUP))
                .andExpect(flash().attribute("flashMessage", containsString("web-standard")))
                .andExpect(flash().attribute("flashMessage", containsString("자동으로 붙지 않습니다")));

        // 표준은 기억일 뿐이다 — 지정만으로 아무 서버에도 붙지 않는다(DEC-C)
        verify(groupAssignmentService, never()).assignToMembers(any(), any());
    }

    @Test
    @DisplayName("해제 302 — 이미 할당된 서버는 그대로라는 사실을 함께 알린다")
    void clearStandard_redirectsWithFlash() throws Exception {
        mvc.perform(post("/provisioning/server-group/{id}/standard-definition/clear", GROUP))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashMessage", containsString("이미 할당된 서버는 그대로")));

        verify(commandService).clearStandardDefinition(GROUP);
        verify(groupAssignmentService, never()).assignToMembers(any(), any());
    }

    // ==== 거절 ========================================================

    @Test
    @DisplayName("definitionId 없이 지정 제출 — 400")
    void setStandard_missingDefinitionId_returns400() throws Exception {
        mvc.perform(post("/provisioning/server-group/{id}/standard-definition", GROUP))
                .andExpect(status().isBadRequest());

        verify(commandService, never()).setStandardDefinition(any(), any());
    }

    @Test
    @DisplayName("없는 그룹에 지정 — 404")
    void setStandard_missingGroup_returns404() throws Exception {
        willThrow(new GuestServerGroupNotFoundException(GROUP))
                .given(commandService).setStandardDefinition(GROUP, DEFINITION);

        mvc.perform(post("/provisioning/server-group/{id}/standard-definition", GROUP)
                        .param("definitionId", String.valueOf(DEFINITION)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("없는 정의서를 지정 — 404")
    void setStandard_missingDefinition_returns404() throws Exception {
        willThrow(new SettingNotFoundException(DEFINITION))
                .given(commandService).setStandardDefinition(GROUP, DEFINITION);

        mvc.perform(post("/provisioning/server-group/{id}/standard-definition", GROUP)
                        .param("definitionId", String.valueOf(DEFINITION)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("비활성 정의서를 direct POST 로 지정 — 409. 화면이 목록에서 뺀 것을 서버가 같은 말로 거절한다")
    void setStandard_disabledDefinition_returns409() throws Exception {
        willThrow(new DefinitionNotAssignableException(DEFINITION, DISABLED_REASON))
                .given(commandService).setStandardDefinition(GROUP, DEFINITION);

        mvc.perform(post("/provisioning/server-group/{id}/standard-definition", GROUP)
                        .param("definitionId", String.valueOf(DEFINITION)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("없는 그룹의 해제 — 404")
    void clearStandard_missingGroup_returns404() throws Exception {
        willThrow(new GuestServerGroupNotFoundException(GROUP))
                .given(commandService).clearStandardDefinition(GROUP);

        mvc.perform(post("/provisioning/server-group/{id}/standard-definition/clear", GROUP))
                .andExpect(status().isNotFound());
    }

    // ==== '고르는 김에 표준으로도 두기' (OQ-3) =========================

    @Test
    @DisplayName("일괄 할당에 체크박스를 켜면 표준 지정이 함께 일어나고 문구가 둘 다 알린다")
    void assignBatch_withAlsoSetStandard() throws Exception {
        givenGroup(null);
        given(settingQueryService.findAssignable()).willReturn(List.of(summary("web-standard")));
        given(assignmentQueryService.groupPreview(anyList(), anyList()))
                .willReturn(List.of(new GroupApplyPreviewResponse(summary("web-standard"), List.of())));
        given(commandService.setStandardDefinition(GROUP, DEFINITION)).willReturn("web-standard");
        given(groupAssignmentService.assignToMembers(any(), eq(DEFINITION)))
                .willReturn(new com.example.serverprovision.provisioning.assignment.dto.response
                        .BatchAssignResult("web-standard", 2, 0, ""));

        mvc.perform(post("/provisioning/server-group/{id}/assignment", GROUP)
                        .param("definitionId", String.valueOf(DEFINITION))
                        .param("alsoSetStandard", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("flashMessage", containsString("2 대에 할당했습니다")))
                .andExpect(flash().attribute("flashMessage", containsString("표준으로 두었습니다")));

        verify(commandService).setStandardDefinition(GROUP, DEFINITION);
    }

    @Test
    @DisplayName("체크박스를 켜지 않으면 표준은 건드리지 않는다 — 기본값은 '할당만'")
    void assignBatch_withoutAlsoSetStandard() throws Exception {
        givenGroup(null);
        given(settingQueryService.findAssignable()).willReturn(List.of(summary("web-standard")));
        given(assignmentQueryService.groupPreview(anyList(), anyList()))
                .willReturn(List.of(new GroupApplyPreviewResponse(summary("web-standard"), List.of())));
        given(groupAssignmentService.assignToMembers(any(), eq(DEFINITION)))
                .willReturn(new com.example.serverprovision.provisioning.assignment.dto.response
                        .BatchAssignResult("web-standard", 2, 0, ""));

        mvc.perform(post("/provisioning/server-group/{id}/assignment", GROUP)
                        .param("definitionId", String.valueOf(DEFINITION)))
                .andExpect(status().is3xxRedirection());

        verify(commandService, never()).setStandardDefinition(any(), any());
    }
}
