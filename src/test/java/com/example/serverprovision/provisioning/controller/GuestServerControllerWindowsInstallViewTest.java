package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.enums.DiscoveryStage;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.service.GuestServerCommandService;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.management.board.enums.Vendor;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentFormResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.AssignmentPlanResponse;
import com.example.serverprovision.provisioning.assignment.dto.response.DefinitionOptionResponse;
import com.example.serverprovision.provisioning.assignment.service.AssignmentCommandService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentQueryService;
import com.example.serverprovision.provisioning.assignment.service.AssignmentStartService;
import com.example.serverprovision.provisioning.group.service.GuestServerGroupQueryService;
import com.example.serverprovision.provisioning.setting.dto.response.SettingSummaryResponse;
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
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E4-1-a-3 CP4 — 게스트 상세의 "Windows 설치" 카드 렌더(R5). 응답 record 의 상태 다섯(서빙 전 · 설치 중 · 시한 만료 ·
 * 실패 · 결손 대기)이 화면 문구로 옮겨지는지, 창 밖이면 카드가 없는지.
 */
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerWindowsInstallViewTest {

    private static final String IMAGE = "Windows Server 2025 SERVERSTANDARD";
    private static final String DISPLAY = "Windows Server 2025 Standard (데스크톱 환경)";

    @Autowired MockMvc mvc;
    @MockitoBean GuestServerQueryService queryService;
    @MockitoBean GuestServerCommandService commandService;
    @MockitoBean AssignmentCommandService assignmentCommandService;
    @MockitoBean AssignmentQueryService assignmentQueryService;
    @MockitoBean AssignmentStartService assignmentStartService;
    @MockitoBean SettingQueryService settingQueryService;
    @MockitoBean GuestServerGroupQueryService groupQueryService;
    @MockitoBean JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @BeforeEach
    void stubAssignment() {
        given(assignmentQueryService.plannedPhasesOf(any(UUID.class))).willReturn(AssignmentPlanResponse.unassigned());
        given(assignmentQueryService.assignmentForm(any(UUID.class), anyList()))
                .willAnswer(invocation -> new AssignmentFormResponse(null,
                        invocation.<List<SettingSummaryResponse>>getArgument(1).stream()
                                .map(summary -> new DefinitionOptionResponse(summary, null, false))
                                .toList()));
    }

    private UUID detailWith(GuestServerDetailResponse.WindowsInstall card) {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(new GuestServerDetailResponse(
                id, "web-01", "RE2108", "RE2108X", UUID.randomUUID(), "464331aabbcc", null, "memo",
                GuestServerStatus.REGISTERED, null, LocalDateTime.now(), LocalDateTime.now(),
                null,
                new GuestServerDetailResponse.Inventory(Vendor.GIGABYTE, 3L, "MS73-HB1-000", "GB-001",
                        DiscoveryStage.DIAGNOSTIC_ENRICHED, null, null, null, null, null),
                List.of(),
                new GuestServerDetailResponse.Progress(ProvisioningPhase.OS_INSTALLING, LocalDateTime.now(),
                        LocalDateTime.now(), null, null, null, false, true, false, false, false),
                null, null, null,
                card,
                null, List.of(), List.of()));
        return id;
    }

    private static GuestServerDetailResponse.WindowsInstall card(ReadinessGrade grade, List<String> notes, LocalDateTime servedAt,
                                                                 int reentries, Long remaining, String failedReason,
                                                                 boolean holding, long holdRemaining) {
        return new GuestServerDetailResponse.WindowsInstall(IMAGE, DISPLAY, grade, notes, servedAt, reentries, 5,
                remaining, failedReason, holding, holdRemaining);
    }

    @Test
    @DisplayName("서빙 전 · READY — 제목 · 표시명(이미지) · '문제 없음' · '서빙 전' 안내")
    void beforeServing() throws Exception {
        UUID id = detailWith(card(ReadinessGrade.READY, List.of(), null, 0, null, null, false, 0));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Windows 설치")))
                .andExpect(content().string(containsString(DISPLAY + " (" + IMAGE + ")")))
                .andExpect(content().string(containsString("문제 없음")))
                .andExpect(content().string(containsString("서빙 전")));
    }

    @Test
    @DisplayName("설치 중 — 서빙 시각 · 재진입 2/5 · 잔여 42분 · 완료 보고 미수신 안내")
    void running() throws Exception {
        LocalDateTime served = LocalDateTime.of(2026, 9, 3, 13, 5, 9);
        UUID id = detailWith(card(ReadinessGrade.READY, List.of(), served, 2, 42L, null, false, 0));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("설치 중")))
                .andExpect(content().string(containsString("서빙 2026-09-03 13:05:09 · 재진입 2/5 · 잔여 42분")))
                .andExpect(content().string(containsString("E4-1-a-4 에서 종결")))
                .andExpect(content().string(not(containsString("설치 시한이 지났습니다"))))
                .andExpect(content().string(not(containsString("다음 부팅에서 설치를 시작할 수 있습니다"))));   // CP5 O-2 — 서빙 뒤 시제
    }

    @Test
    @DisplayName("시한 만료(잔여 0) — 재진입이 없으면 운영자 실패 전환을 안내한다(D-8 공백)")
    void expiredWithoutReentry() throws Exception {
        UUID id = detailWith(card(ReadinessGrade.READY, List.of(), LocalDateTime.now().minusHours(2), 0, 0L, null, false, 0));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("설치 시한이 지났습니다")))
                .andExpect(content().string(containsString("운영자가 실패 전환하십시오")));
    }

    @Test
    @DisplayName("실패 — 사유 코드와 사유별 안내(REPXE_LOOP → 부팅 순서 · 로그 확인)")
    void failed() throws Exception {
        UUID id = detailWith(card(ReadinessGrade.READY, List.of(), null, 0, null, "REPXE_LOOP", false, 0));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("실패 · REPXE_LOOP")))
                .andExpect(content().string(containsString("재부팅이 상한을 넘겨 반복됐습니다")));
    }

    @Test
    @DisplayName("결손 대기 — BLOCKED 사유 목록(어디를 고칠지) + 대기 잔여 분")
    void holding() throws Exception {
        UUID id = detailWith(card(ReadinessGrade.BLOCKED,
                List.of("install.wim 없음 — 대시보드 Windows 설치 소스 영역", "제품 키 ServerStandard 미설정 — 환경변수"),
                null, 0, null, null, true, 100));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("install.wim 없음 — 대시보드 Windows 설치 소스 영역 · 제품 키 ServerStandard 미설정 — 환경변수")))
                .andExpect(content().string(containsString("자원이 갖춰지기를 기다리는 중입니다")))
                .andExpect(content().string(containsString("대기 잔여 100분")));
    }

    @Test
    @DisplayName("창 밖 — 카드 자체가 없다")
    void noCard() throws Exception {
        UUID id = detailWith(null);
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("정의서가 고른 설치 이미지를"))));
    }

    @Test
    @DisplayName("CP5 F-1 — 운영자 수동 실패(OPERATOR) 사유는 재시도 안내 문구, 준비도는 '문제 없음' 만")
    void failedByOperator() throws Exception {
        UUID id = detailWith(card(ReadinessGrade.READY, List.of(), null, 0, null, "OPERATOR", false, 0));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("실패 · OPERATOR")))
                .andExpect(content().string(containsString("운영자가 수동으로 실패 전환했습니다")))
                .andExpect(content().string(not(containsString("다음 부팅에서 설치를 시작할 수 있습니다"))));
    }
}
