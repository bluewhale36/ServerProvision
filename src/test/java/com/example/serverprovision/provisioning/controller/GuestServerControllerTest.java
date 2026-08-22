package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.exception.GuestServerNotFoundException;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentFormResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentPlanResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.DefinitionOptionResponse;
import com.example.serverprovision.provisioning.assignment.service.AssignmentCommandService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentStartService;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.service.SettingQueryService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
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
 * U1 CP4 — {@link GuestServerController} 통합 테스트 (목록 / 상세 / 인라인수정 / 회수).
 * Mocking 은 execution application service 단까지만 — controller 의 redirect/view 선택 + BindingResult 인라인 +
 * {@code @ControllerAdvice} 예외 매핑은 실제로 실행된다.
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    // U3-1 — 컨트롤러 신규 협력자(할당 스냅샷). 상세 렌더가 계획 rail 을 조립하므로 plannedPhasesOf 를 스텁한다.
    @MockitoBean AssignmentCommandService assignmentCommandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean AssignmentStartService assignmentStartService;
    @MockitoBean SettingQueryService settingQueryService;
    // U3-4 — 목록이 소속 그룹 배지를 합성하므로 컨트롤러가 이 빈을 요구한다.
    @MockitoBean GuestServerGroupQueryService groupQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void stubAssignmentPlan() {
        given(assignmentQueryService.plannedPhasesOf(any(UUID.class)))
                .willReturn(AssignmentPlanResponse.unassigned());
        // U3-5-a — 할당 폼 재료. 판정은 별도 테스트가 다루므로 여기서는 차단 없이 그대로 통과시킨다
        // (기존 시나리오의 관심사는 선택지 렌더 · 개시 버튼 노출이지 할당 가능성이 아니다).
        given(assignmentQueryService.assignmentForm(any(UUID.class), anyList()))
                .willAnswer(invocation -> new AssignmentFormResponse(null,
                        invocation.<java.util.List<SettingSummaryResponse>>getArgument(1).stream()
                                .map(summary -> new DefinitionOptionResponse(summary, null, false))
                                .toList()));
    }

    private GuestServerSummaryResponse summary(UUID id) {
        return new GuestServerSummaryResponse(
                id, "web-01", UUID.randomUUID(), Vendor.GIGABYTE, "MS73-HB1-000",
                GuestServerStatus.REGISTERED, ProvisioningPhase.BOOTSTRAPPING,
                IpAddressVO.of("10.20.3.11"), LocalDateTime.now(),
                null, false, null,   // E1-2·S7 — 접촉 관찰(lastSeenAt·contactActive·remaining) 기본 fixture
                null, null);         // U3-4 — 스펙 미수집 서버는 그룹 키·라벨이 없다
    }

    private GuestServerDetailResponse detail(UUID id) {
        return new GuestServerDetailResponse(
                id, "web-01", "RE2108", "RE2108X", UUID.randomUUID(), "memo",
                GuestServerStatus.REGISTERED, null, LocalDateTime.now(), LocalDateTime.now(),
                null,   // E1-2 — 접촉 관찰 없음 fixture
                new GuestServerDetailResponse.Inventory(Vendor.GIGABYTE, 3L, "MS73-HB1-000", "GB-001",
                        DiscoveryStage.IPXE_REGISTERED, null, null, null, null),
                List.of(),
                new GuestServerDetailResponse.Progress(
                        ProvisioningPhase.DIAGNOSE_LINUX, LocalDateTime.now(),
                        null, null, null, null, true, false, false, false),   // E1-0a 미개시 + E1-2 액션 플래그 (ES-2: phaseMeta 소멸 · seed phase 파생)
                List.of());
    }

    // ==== 성공 2xx ====================================================

    @Test
    @DisplayName("GET /provisioning/server — 목록 200 + list 뷰")
    void list_returns200() throws Exception {
        // U3-3 — 목록은 평면 리스트가 아니라 그룹 응답을 받는다. 그룹 렌더 자체는 전용 테스트가 덮는다.
        given(queryService.findGrouped(null)).willReturn(
                new com.example.serverprovision.execution.dto.response.GuestServerListResponse(null, List.of()));

        mvc.perform(get("/provisioning/server"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-list"))
                .andExpect(model().attributeExists("list"));
    }

    /**
     * 되돌아가기는 URL 파라미터가 아니라 화면 이력 스택이 맡는다(재구성).
     *
     * <p>화면이 지킬 것은 마크업 규약 둘뿐이다 — 상세의 '목록으로' 에 {@code data-nav-back} 과
     * 기본 목록 href, 목록 행에 {@code data-nav-key}. 실제 목적지는 스크립트가 스택에서 정하고,
     * 스택이 비었거나 남의 것이면 이 href 가 그대로 열린다(주소창 직접 진입 · 새로고침).</p>
     */
    @Test
    @DisplayName("상세의 '목록으로' 는 이력 스택 표기와 기본 목록 href 를 함께 갖는다")
    void detail_backLinkFollowsNavStackConvention() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(detail(id));

        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-nav-back")))
                .andExpect(content().string(containsString("href=\"/provisioning/server\"")));
    }

    @Test
    @DisplayName("GET /provisioning/server/{id} — 상세 200 + detail 뷰 + updateForm")
    void detail_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(detail(id));

        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-detail"))
                .andExpect(model().attributeExists("server", "updateForm"));
    }

    /**
     * 정의서를 고르는 자리가 상세 본문에서 모달로 옮겨갔다(U3-5-b).
     *
     * <p>상세가 지킬 것은 둘이다 — 모달을 여는 버튼이 있을 것, 그리고 <b>정의서 이름을 본문에 그리지
     * 않을 것</b>. 후자를 함께 보는 이유는 {@code <select>} 잔재가 남아도 화면은 멀쩡해 보이기 때문이다.
     * 선택지 자체의 렌더(할당 가능 정의서만 · 잠금 · 사유)는 조각을 직접 받는
     * {@code GuestServerControllerPickerTest} 가 덮는다.</p>
     */
    @Test
    @DisplayName("GET /provisioning/server/{id} — 고르는 자리는 모달이므로 상세에는 여는 버튼만 (U3-5-b)")
    void detail_rendersPickerOpenerInsteadOfSelect() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(detail(id));
        given(settingQueryService.findAssignable()).willReturn(List.of(
                new SettingSummaryResponse(1L, "web-standard",
                        List.of(SettingProcessType.BASIC_UPDATE), false, true, false, LocalDateTime.now())));

        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"openAssignPicker\"")))
                .andExpect(content().string(containsString("/assignment/picker")))
                .andExpect(content().string(not(containsString("name=\"definitionId\" class=\"n-select\""))))
                .andExpect(content().string(not(containsString(">web-standard<"))));
    }

    @Test
    @DisplayName("POST /{id}/edit — 수정 성공 302 redirect")
    void edit_success_redirects() throws Exception {
        UUID id = UUID.randomUUID();
        given(commandService.isNameTakenByOther(eq(id), any())).willReturn(false);
        given(commandService.isSerialTakenByOther(eq(id), any())).willReturn(false);

        mvc.perform(post("/provisioning/server/{id}/edit", id)
                        .param("name", "web-01")
                        .param("modelName", "RE2108")
                        .param("serialNumber", "RE2108X")
                        .param("memo", "메모"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server/" + id));
    }

    @Test
    @DisplayName("POST /{id}/decommission — 회수 302 redirect")
    void decommission_redirects() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(post("/provisioning/server/{id}/decommission", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/provisioning/server/" + id));
    }

    // ==== 400 / 검증 실패 (재렌더) ====================================

    @Test
    @DisplayName("POST /{id}/edit — name 128자 초과(@Size) → 폼 재렌더(200) + name 필드 에러")
    void edit_sizeViolation_rerenders() throws Exception {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(detail(id));

        mvc.perform(post("/provisioning/server/{id}/edit", id)
                        .param("name", "a".repeat(129)))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-detail"))
                .andExpect(model().attributeHasFieldErrors("updateForm", "name"));
    }

    @Test
    @DisplayName("POST /{id}/edit — 이름 중복 → 폼 재렌더(200) + name 필드 에러 (예외 아님)")
    void edit_duplicateName_rerenders() throws Exception {
        UUID id = UUID.randomUUID();
        given(commandService.isNameTakenByOther(eq(id), eq("dup"))).willReturn(true);
        given(queryService.findDetail(id)).willReturn(detail(id));

        mvc.perform(post("/provisioning/server/{id}/edit", id)
                        .param("name", "dup"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-detail"))
                .andExpect(model().attributeHasFieldErrors("updateForm", "name"));
    }

    @Test
    @DisplayName("POST /{id}/edit — 사내 시리얼 중복 → 폼 재렌더(200) + serialNumber 필드 에러")
    void edit_duplicateSerial_rerenders() throws Exception {
        UUID id = UUID.randomUUID();
        given(commandService.isSerialTakenByOther(eq(id), eq("S1"))).willReturn(true);
        given(queryService.findDetail(id)).willReturn(detail(id));

        mvc.perform(post("/provisioning/server/{id}/edit", id)
                        .param("serialNumber", "S1"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-detail"))
                .andExpect(model().attributeHasFieldErrors("updateForm", "serialNumber"));
    }

    // ==== 404 ========================================================

    @Test
    @DisplayName("GET /provisioning/server/{id} — 없는 id → GuestServerNotFound 404 (advice)")
    void detail_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        willThrow(new GuestServerNotFoundException(id)).given(queryService).findDetail(id);

        mvc.perform(get("/provisioning/server/{id}", id).accept(org.springframework.http.MediaType.TEXT_HTML))
                .andExpect(status().isNotFound());
    }
}
