package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.provisioning.setting.dto.request.SettingListQuery;

import com.example.serverprovision.support.FixedClockTestConfig;
import org.springframework.context.annotation.Import;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentFormResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.DefinitionOptionResponse;
import com.example.serverprovision.provisioning.assignment.service.AssignmentCommandService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentStartService;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * U3-5-b — 정의서 선택 모달 조각({@code GET /provisioning/server/{id}/assignment/picker}) 통합 테스트.
 *
 * <p>이 조각이 지킬 계약은 넷이다. ① 좌측에 선택지가 전부 나온다(잠긴 것도 지우지 않는다) ② 잠긴 것은
 * 잠긴 모양이 되고 사유를 싣는다 ③ <b>잠긴 것에도 우측 상세가 붙는다</b>(DEC-C — 사유만 보여주면 다음
 * 판단을 못 한다) ④ 우측 카드는 정의서 상세 화면과 같은 조각이 그린다(DEC-A).</p>
 *
 * <p>Mocking 은 서비스 단까지다 — 조각 뷰 이름 해석과 Thymeleaf 렌더는 실제로 실행된다. 조각 참조가
 * 어긋나면 컴파일이 아니라 여기서 드러난다.</p>
 */
@Import(FixedClockTestConfig.class)
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerPickerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;

    @MockitoBean com.example.serverprovision.global.redfish.RedfishPowerService redfishPowerService;   // HF15-1 — [재시도 후 네트워크 부팅]
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean AssignmentCommandService assignmentCommandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean AssignmentStartService assignmentStartService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean GuestServerGroupQueryService groupQueryService;
    @MockitoBean com.example.serverprovision.execution.service.GuestServerLookupService lookupService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String BLOCK_REASON =
            "본 게스트 서버의 메인보드(ASUS-Z13PE)와 정의서의 메인보드(MS03-CE0)가 호환되지 않습니다.";

    private SettingSummaryResponse summary(long id, String name) {
        return new SettingSummaryResponse(id, name,
                List.of(SettingProcessType.BASIC_UPDATE), false, true, false, com.example.serverprovision.support.FixedClockTestConfig.TODAY.atTime(9, 0));   // R20 — 기본 기간(최근 1 년) 안
    }

    /** 단계 하나를 실은 상세 — 우측 패널이 카드 조각을 실제로 그리는지 보려면 빈 목록으로는 안 된다. */
    private SettingDetailResponse detail(long id, String name) {
        return new SettingDetailResponse(id, name, false, true, false, 0L,
                List.of(new BasicUpdateRequest(
                        new BoardModelSelectionRequest(BoardModelSelectionMode.SPECIFIED, 7L),
                        new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null),
                        new FirmwareSelectionRequest(FirmwareSelectionMode.LATEST, null))),
                List.of(), List.of(),
                new ReferenceNamesResponse(Map.of(7L, "MS03-CE0"),
                        Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()),
                LocalDateTime.now(), LocalDateTime.now());
    }

    /** 통과 1 종 + 차단 1 종. 차단 사유는 서버 가드가 만드는 문자열 그대로다. */
    private void stubTwoDefinitions(UUID id) {
        given(settingQueryService.findAssignable())
                .willReturn(List.of(summary(1L, "os-only-auto"), summary(2L, "bios-ms03")));
        given(assignmentQueryService.assignmentForm(any(UUID.class), anyList()))
                .willReturn(new AssignmentFormResponse(null, List.of(
                        new DefinitionOptionResponse(summary(1L, "os-only-auto"), null, null),
                        new DefinitionOptionResponse(summary(2L, "bios-ms03"), BLOCK_REASON, null))));
        given(settingQueryService.findDetailsOf(anyList()))
                .willReturn(List.of(detail(1L, "os-only-auto"), detail(2L, "bios-ms03")));
    }

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("GET /{id}/assignment/picker — 좌측에 선택지 전부 · 잠긴 것은 잠긴 모양으로 남는다")
    void picker_rendersAllOptionsIncludingBlocked() throws Exception {
        UUID id = UUID.randomUUID();
        stubTwoDefinitions(id);

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">os-only-auto<")))
                // 잠긴 정의서가 목록에서 사라지면 "없어진 것" 과 "안 맞는 것" 을 구분할 수 없다
                .andExpect(content().string(containsString(">bios-ms03<")))
                .andExpect(content().string(containsString("n-lookup-row-disabled")))
                .andExpect(content().string(containsString("data-definition-id=\"2\"")));
    }

    @Test
    @DisplayName("GET /{id}/assignment/picker — 잠긴 정의서도 우측에 사유와 상세를 함께 싣는다 (DEC-C)")
    void picker_blockedDefinitionStillCarriesDetail() throws Exception {
        UUID id = UUID.randomUUID();
        stubTwoDefinitions(id);

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id))
                .andExpect(status().isOk())
                // 사유는 옵션 라벨이 아니라 패널 머리에 놓여 잘리지 않는다
                .andExpect(content().string(containsString(BLOCK_REASON)))
                .andExpect(content().string(containsString("data-picker-panel=\"2\"")))
                // 패널 수 = 선택지 수 — 잠겼다고 패널을 빼면 사유만 보이고 내용을 못 본다
                .andExpect(content().string(containsString("data-picker-panel=\"1\"")));
    }

    @Test
    @DisplayName("R20 GET /{id}/assignment/picker — 대조 전 정의서는 '대조 전' 배지와 펼침에 미집행 가능성 안내를 싣는다")
    void picker_unverifiedOptionShowsReason() throws Exception {
        UUID id = UUID.randomUUID();
        String cardUnverified = "이 정의서는 RAID 카드 MegaRAID 9361-8i 전용인데 이 서버의 RAID 카드는 아직 확인되지 않았습니다.";
        given(settingQueryService.findAssignable())
                .willReturn(List.of(summary(1L, "os-only-auto"), summary(2L, "bios-ms03")));
        given(assignmentQueryService.assignmentForm(any(UUID.class), anyList()))
                .willReturn(new AssignmentFormResponse(null, List.of(
                        new DefinitionOptionResponse(summary(1L, "os-only-auto"), null, cardUnverified),
                        new DefinitionOptionResponse(summary(2L, "bios-ms03"), null, null))));
        given(settingQueryService.findDetailsOf(anyList()))
                .willReturn(List.of(detail(1L, "os-only-auto"), detail(2L, "bios-ms03")));

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("대조 전")))
                .andExpect(content().string(containsString("일부 하드웨어가 불일치할 수 있으며")))
                .andExpect(content().string(containsString("하드웨어를 대조하지 못했습니다.")));
    }

    @Test
    @DisplayName("GET /{id}/assignment/picker — 우측 카드는 정의서 상세 화면과 같은 조각이 그린다 (DEC-A)")
    void picker_rendersProcessCardsFragment() throws Exception {
        UUID id = UUID.randomUUID();
        stubTwoDefinitions(id);

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id))
                .andExpect(status().isOk())
                // 카드 조각이 실제로 실행됐다는 표식 — 단계 타입 배지와 참조 명칭 해석까지 도달했는가
                .andExpect(content().string(containsString("n-process-card")))
                .andExpect(content().string(containsString(SettingProcessType.BASIC_UPDATE.getDisplayName())))
                .andExpect(content().string(containsString("MS03-CE0")));
    }

    @Test
    @DisplayName("GET /{id}/assignment/picker — 상세가 사라진 선택지는 떨군다 (두 재료를 읽는 사이 삭제)")
    void picker_dropsOptionWithoutDetail() throws Exception {
        UUID id = UUID.randomUUID();
        given(settingQueryService.findAssignable())
                .willReturn(List.of(summary(1L, "os-only-auto"), summary(2L, "bios-ms03")));
        given(assignmentQueryService.assignmentForm(any(UUID.class), anyList()))
                .willReturn(new AssignmentFormResponse(null, List.of(
                        new DefinitionOptionResponse(summary(1L, "os-only-auto"), null, null),
                        new DefinitionOptionResponse(summary(2L, "bios-ms03"), null, null))));
        // 2 번은 그 사이에 삭제됐다 — 상세가 돌아오지 않는다
        given(settingQueryService.findDetailsOf(anyList()))
                .willReturn(List.of(detail(1L, "os-only-auto")));

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">os-only-auto<")))
                .andExpect(content().string(not(containsString(">bios-ms03<"))));
    }

    @Test
    @DisplayName("GET /{id}/assignment/picker — 선택지가 하나도 없으면 그 사실을 적는다")
    void picker_rendersEmptyState() throws Exception {
        UUID id = UUID.randomUUID();
        given(settingQueryService.findAssignable()).willReturn(List.of());
        given(assignmentQueryService.assignmentForm(any(UUID.class), anyList()))
                .willReturn(new AssignmentFormResponse(null, List.of()));
        given(settingQueryService.findDetailsOf(anyList())).willReturn(List.of());

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("할당할 수 있는 세팅 정의서가 없습니다")));
    }

    // ==== R20 — 검색칸 · 조회 조건 창 · 목록 조건 재사용 · 모달 전용 축 ===============

    private static final SettingSummaryResponse ON_MS04 = new SettingSummaryResponse(1L, "web-standard",
            List.of(SettingProcessType.BASIC_UPDATE), false, true, false, LocalDateTime.of(2026, 9, 1, 9, 0), 7L, "MS04-CE0");
    private static final SettingSummaryResponse BOARDLESS = new SettingSummaryResponse(2L, "db-raid10",
            List.of(SettingProcessType.BASIC_UPDATE), false, true, false, LocalDateTime.of(2026, 3, 1, 9, 0), null, null);
    private static final SettingSummaryResponse LOCKED = new SettingSummaryResponse(3L, "locked-one",
            List.of(SettingProcessType.BASIC_UPDATE), false, true, false, LocalDateTime.of(2026, 3, 2, 9, 0), 9L, "MS03-CE0");

    /** 전체(universe) 셋 · 목록 조건으로 거른 결과는 matched. 3 번은 이 서버에 잠긴다. */
    @SuppressWarnings("unchecked")
    private void stubSearch(List<SettingSummaryResponse> matched) {
        given(settingQueryService.findAssignable()).willReturn(List.of(ON_MS04, BOARDLESS, LOCKED));
        given(settingQueryService.findAssignable(any(SettingListQuery.class))).willReturn(matched);
        given(assignmentQueryService.assignmentForm(any(UUID.class), anyList())).willAnswer(inv ->
                new AssignmentFormResponse(null, ((List<SettingSummaryResponse>) inv.getArgument(1)).stream()
                        .map(d -> new DefinitionOptionResponse(d, d.id() == 3L ? BLOCK_REASON : null, null)).toList()));
        given(settingQueryService.findDetailsOf(anyList()))
                .willReturn(List.of(detail(1L, "web-standard"), detail(2L, "db-raid10"), detail(3L, "locked-one")));
    }

    @Test
    @DisplayName("R20 · 검색칸 + 조회 조건 창(대상 보드 · 단계 구성 · 할당 가능 · 등록 시각)을 그리고, 기본 기간을 목록 조건으로 넘긴다")
    void picker_rendersBandAndPanelWithDefaultRange() throws Exception {
        UUID id = UUID.randomUUID();
        stubSearch(List.of(ON_MS04, BOARDLESS, LOCKED));

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-picker-q")))
                .andExpect(content().string(containsString("popovertarget=\"definitionPickerFilter\"")))
                .andExpect(content().string(containsString(">대상 보드<")))
                .andExpect(content().string(containsString(">단계 구성<")))
                .andExpect(content().string(containsString("할당 가능한 것만")))
                .andExpect(content().string(containsString(">등록 시각<")))
                .andExpect(content().string(containsString("name=\"processType\"")))
                // 보드 칩 후보는 할당 가능한 정의서들이 요구하는 보드 + 보드 무관
                .andExpect(content().string(containsString(">MS04-CE0<")))
                .andExpect(content().string(containsString(">보드 무관<")));

        org.mockito.ArgumentCaptor<SettingListQuery> captor = org.mockito.ArgumentCaptor.forClass(SettingListQuery.class);
        org.mockito.Mockito.verify(settingQueryService).findAssignable(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().from()).isEqualTo(java.time.LocalDate.of(2025, 10, 11));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().to()).isEqualTo(java.time.LocalDate.of(2026, 10, 11));
    }

    @Test
    @DisplayName("R20 · 이름 · 단계 · 등록 시각은 세팅 정의서 목록과 같은 조건(SettingListQuery)으로 서비스에 넘긴다")
    void picker_passesSharedAxesAsListQuery() throws Exception {
        UUID id = UUID.randomUUID();
        stubSearch(List.of(BOARDLESS));

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id)
                        .param("q", "raid").param("processType", "RAID_CONFIGURATION")
                        .param("from", "2025-01-01").param("to", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(">db-raid10<")))
                .andExpect(content().string(not(containsString(">web-standard<"))));

        org.mockito.ArgumentCaptor<SettingListQuery> captor = org.mockito.ArgumentCaptor.forClass(SettingListQuery.class);
        org.mockito.Mockito.verify(settingQueryService).findAssignable(captor.capture());
        SettingListQuery passed = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(passed.q()).isEqualTo("raid");
        org.assertj.core.api.Assertions.assertThat(passed.processTypes()).containsExactly(SettingProcessType.RAID_CONFIGURATION);
        org.assertj.core.api.Assertions.assertThat(passed.from()).isEqualTo(java.time.LocalDate.of(2025, 1, 1));
        org.assertj.core.api.Assertions.assertThat(passed.to()).isEqualTo(java.time.LocalDate.of(2026, 6, 30));
    }

    @Test
    @DisplayName("R20 · 모달 전용 축 — 대상 보드 칩(OR · 보드 무관) · 할당 가능한 것만")
    void picker_filtersByBoardAndAssignable() throws Exception {
        UUID id = UUID.randomUUID();
        stubSearch(List.of(ON_MS04, BOARDLESS, LOCKED));

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id).param("board", "7"))
                .andExpect(content().string(containsString(">web-standard<")))
                .andExpect(content().string(not(containsString(">db-raid10<"))))
                .andExpect(content().string(not(containsString(">locked-one<"))));

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id).param("boardless", "true"))
                .andExpect(content().string(containsString(">db-raid10<")))
                .andExpect(content().string(not(containsString(">web-standard<"))));

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id).param("assignableOnly", "true"))
                .andExpect(content().string(containsString(">web-standard<")))
                .andExpect(content().string(not(containsString(">locked-one<"))));
    }

    @Test
    @DisplayName("R20 · 결과 0 건 — 정의서는 있는데 조건에 안 걸리면 '검색 결과가 없습니다.'")
    void picker_zeroResultsMessage() throws Exception {
        UUID id = UUID.randomUUID();
        stubSearch(List.of());

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id).param("q", "zzz"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("검색 결과가 없습니다.")))
                .andExpect(content().string(not(containsString("할당할 수 있는 세팅 정의서가 없습니다"))));
    }

    // ==== 404 =========================================================

    @Test
    @DisplayName("GET /{id}/assignment/picker — 없는 게스트 404 (advice 매핑)")
    void picker_unknownGuest_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        given(settingQueryService.findAssignable()).willReturn(List.of());
        willThrow(new GuestServerNotFoundException(id))
                .given(assignmentQueryService).assignmentForm(any(UUID.class), anyList());

        mvc.perform(get("/provisioning/server/{id}/assignment/picker", id))
                .andExpect(status().isNotFound());
    }
}
