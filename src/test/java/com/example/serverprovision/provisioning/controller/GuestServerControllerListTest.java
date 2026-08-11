package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.response.GuestServerListResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.vo.RegistrationAge;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.execution.vo.SpecGroupKey;
import com.example.serverprovision.provisioning.assignment.service.AssignmentCommandService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentStartService;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * U3-3 CP4 — 목록 화면의 그룹 렌더 · 필터 · 접힘 상태를 HTTP 계층에서 검증한다.
 * Mocking 은 조회 서비스까지만이라 뷰 렌더와 파라미터 바인딩은 실제로 실행된다.
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerListTest {

    @Autowired MockMvc mvc;

    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean AssignmentCommandService assignmentCommandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean AssignmentStartService assignmentStartService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private static GuestServerSummaryResponse row(String name) {
        return new GuestServerSummaryResponse(
                UUID.randomUUID(), name, UUID.randomUUID(), null, "MS03-CE0",
                GuestServerStatus.PROVISIONING, ProvisioningPhase.DIAGNOSE_LINUX,
                null, LocalDateTime.now(), null, false, null);
    }

    private static GuestServerListResponse listWith(GuestServerListResponse.PendingRegistrations pending,
                                                    List<GuestServerListResponse.TimeGroup> groups) {
        return new GuestServerListResponse(pending, groups);
    }

    private static GuestServerListResponse.TimeGroup bucket(RegistrationAge b, String label, int count) {
        List<GuestServerSummaryResponse> rows = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(row("srv-0" + (i + 1)));
        }
        return new GuestServerListResponse.TimeGroup(b, List.of(
                new GuestServerListResponse.SpecGroup(new SpecGroupKey("k-" + label), label, rows)));
    }

    @Test
    @DisplayName("그룹이 렌더되고 스펙 요약과 대수가 화면에 나온다")
    void rendersSpecGroups() throws Exception {
        given(queryService.findGrouped(any())).willReturn(
                listWith(null, List.of(bucket(new RegistrationAge(RegistrationAge.Unit.SECOND, 30L), "MS03-CE0 · 6338 ×2", 2))));

        mvc.perform(get("/provisioning/server"))
                .andExpect(status().isOk())
                .andExpect(view().name("provisioning/server-list"))
                // 필터가 없으면 phaseFilter 는 null 이라 모델에 등록되지 않는다 — 뷰는 null 을 "전체" 로 읽는다
                .andExpect(model().attributeExists("list", "phases", "pendingOpen"))
                .andExpect(content().string(containsString("MS03-CE0 · 6338 ×2")))
                .andExpect(content().string(containsString("30초 전")))
                .andExpect(content().string(containsString("2대")))
                // U3-3 요구사항 4 — 서버별 현재 단계가 열로 나온다(필터와 같은 값이라 결과의 근거가 행에서 읽힌다)
                .andExpect(content().string(containsString("현재 단계")))
                .andExpect(content().string(containsString(ProvisioningPhase.DIAGNOSE_LINUX.getDescription())));
    }

    @Test
    @DisplayName("'등록 진행 중' 은 접힌 채로 나오고 헤더만으로 대수와 내역이 읽힌다")
    void pendingIsCollapsedByDefault() throws Exception {
        given(queryService.findGrouped(any())).willReturn(
                listWith(new GuestServerListResponse.PendingRegistrations(
                        List.of(row("srv-a")), List.of(row("srv-b"))), List.of()));

        mvc.perform(get("/provisioning/server"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pendingOpen", false))
                .andExpect(content().string(containsString("등록 진행 중")))
                .andExpect(content().string(containsString("등록만 됨")))     // 헤더 요약에 노출
                .andExpect(content().string(containsString("수집 중")))
                // 접힌 상태이므로 본문(설명 문장)은 렌더되지 않는다
                .andExpect(content().string(not(containsString("부팅 · 네트워크 점검 대상"))));
    }

    @Test
    @DisplayName("?pending=open 이면 펼쳐져 두 묶음의 안내와 표가 나온다")
    void pendingOpensByQueryParameter() throws Exception {
        given(queryService.findGrouped(any())).willReturn(
                listWith(new GuestServerListResponse.PendingRegistrations(
                        List.of(row("srv-a")), List.of(row("srv-b"))), List.of()));

        mvc.perform(get("/provisioning/server").param("pending", "open"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pendingOpen", true))
                .andExpect(content().string(containsString("부팅 · 네트워크 점검 대상")))
                .andExpect(content().string(containsString("기다리면 되는 대상")));
    }

    @Test
    @DisplayName("?phase= 는 조회 서비스에 그대로 전달되고 선택 상태가 화면에 남는다")
    void phaseFilterIsBoundAndKept() throws Exception {
        given(queryService.findGrouped(eq(ProvisioningPhase.DIAGNOSE_LINUX))).willReturn(
                listWith(null, List.of(bucket(new RegistrationAge(RegistrationAge.Unit.SECOND, 30L), "MS03-CE0", 1))));

        mvc.perform(get("/provisioning/server").param("phase", "DIAGNOSE_LINUX"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("phaseFilter", ProvisioningPhase.DIAGNOSE_LINUX))
                .andExpect(content().string(containsString("is-active")));
    }

    @Test
    @DisplayName("필터 결과가 0건이면 되돌리는 길을 안내한다")
    void emptyFilterResultGuidesBack() throws Exception {
        given(queryService.findGrouped(any())).willReturn(listWith(null, List.of()));

        mvc.perform(get("/provisioning/server").param("phase", "OS_INSTALLING"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이 단계에 해당하는 서버가 없습니다")))
                .andExpect(content().string(containsString("전체")));
    }

    @Test
    @DisplayName("알 수 없는 phase 값은 400 — 새 분기를 만들지 않고 Spring 기본 처리에 맡긴다")
    void unknownPhaseValueIsBadRequest() throws Exception {
        mvc.perform(get("/provisioning/server").param("phase", "NOT_A_PHASE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("SSE 재조회 경로(X-Requested-With)로 들어와도 같은 화면을 돌려준다 — 필터가 URL 에 있어 보존된다")
    void sseRefetchKeepsFilteredView() throws Exception {
        given(queryService.findGrouped(eq(ProvisioningPhase.DIAGNOSE_LINUX))).willReturn(
                listWith(null, List.of(bucket(new RegistrationAge(RegistrationAge.Unit.SECOND, 30L), "MS03-CE0", 1))));

        mvc.perform(get("/provisioning/server")
                        .param("phase", "DIAGNOSE_LINUX")
                        .header("X-Requested-With", "server-stream"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("phaseFilter", ProvisioningPhase.DIAGNOSE_LINUX))
                .andExpect(content().string(containsString("data-live=\"server-list\"")));
    }
}
