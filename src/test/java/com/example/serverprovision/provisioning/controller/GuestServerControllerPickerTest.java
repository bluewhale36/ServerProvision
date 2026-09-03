package com.example.serverprovision.provisioning.controller;

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
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerPickerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean AssignmentCommandService assignmentCommandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean AssignmentStartService assignmentStartService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean GuestServerGroupQueryService groupQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static final String BLOCK_REASON =
            "이 정의서는 메인보드 MS03-CE0 전용입니다 — 이 서버는 ASUS-Z13PE 입니다.";

    private SettingSummaryResponse summary(long id, String name) {
        return new SettingSummaryResponse(id, name,
                List.of(SettingProcessType.BASIC_UPDATE), false, true, false, LocalDateTime.now());
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
                        new DefinitionOptionResponse(summary(1L, "os-only-auto"), null, false),
                        new DefinitionOptionResponse(summary(2L, "bios-ms03"), BLOCK_REASON, false))));
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
                .andExpect(content().string(containsString("n-miller-item-disabled")))
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
                .andExpect(content().string(containsString("id=\"definition-panel-2\"")))
                // 패널 수 = 선택지 수 — 잠겼다고 패널을 빼면 사유만 보이고 내용을 못 본다
                .andExpect(content().string(containsString("id=\"definition-panel-1\"")));
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
                        new DefinitionOptionResponse(summary(1L, "os-only-auto"), null, false),
                        new DefinitionOptionResponse(summary(2L, "bios-ms03"), null, false))));
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
