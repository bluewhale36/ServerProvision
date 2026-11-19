package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.support.FixedClockTestConfig;
import org.springframework.context.annotation.Import;
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
 * E4-1-b-4-2 — Windows 밖 계열의 OS 설치 카드 렌더. 응답의 계열 내용이 고른 조각(rhelCard · unsupportedCard)이 그려지는지,
 * 대기 배너와 실패 경보가 계열과 무관하게 서는지(F-1).
 */
@Import(FixedClockTestConfig.class)
@WebMvcTest(controllers = GuestServerController.class)
class GuestServerControllerOsInstallViewTest {


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

    @BeforeEach
    void stubAssignment() {
        given(assignmentQueryService.plannedPhasesOf(any(UUID.class))).willReturn(AssignmentPlanResponse.unassigned());
        given(assignmentQueryService.assignmentForm(any(UUID.class), anyList()))
                .willAnswer(invocation -> new AssignmentFormResponse(null,
                        invocation.<List<SettingSummaryResponse>>getArgument(1).stream()
                                .map(summary -> new DefinitionOptionResponse(summary, null, null))
                                .toList()));
    }

    private UUID detailWith(GuestServerDetailResponse.OsInstall card) {
        UUID id = UUID.randomUUID();
        given(queryService.findDetail(id)).willReturn(new GuestServerDetailResponse(
                id, "web-01", "RE2108", "RE2108X", UUID.randomUUID(), "464331aabbcc", null, "memo",
                GuestServerStatus.REGISTERED, null, LocalDateTime.now(), LocalDateTime.now(),
                null,
                new GuestServerDetailResponse.Inventory(Vendor.GIGABYTE, 3L, "MS73-HB1-000", "GB-001",
                        DiscoveryStage.DIAGNOSTIC_ENRICHED, null, null, null, null, null, List.of()),
                List.of(),
                new GuestServerDetailResponse.Progress(ProvisioningPhase.OS_INSTALLING, LocalDateTime.now(),
                        LocalDateTime.now(), null, null, null, false, true, false, false, false),
                null, null, null,
                card,
                null, List.of(), List.of()));
        return id;
    }

    private static GuestServerDetailResponse.OsInstall card(String family, ReadinessGrade grade, List<String> notes,
                                                           LocalDateTime servedAt, String failedReason, String failedMessage,
                                                           boolean holding, GuestServerDetailResponse.OsInstallCardDetail detail) {
        return new GuestServerDetailResponse.OsInstall(family, grade, notes, servedAt, 1, 5, servedAt == null ? null : 42L,
                failedReason, failedMessage, holding, holding ? 120L : 0L, null, false, null, detail);
    }

    @Test
    @DisplayName("RHEL 서빙 전 — 제목 'RHEL 계열 설치' · 대상 OS · 준비됨 · 정해진 디스크도 고정폭(CP5 F-3) · 예정")
    void rhel_beforeServing() throws Exception {
        UUID id = detailWith(card("RHEL 계열", ReadinessGrade.READY, List.of(), null, null, null, false,
                new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", "wwn-0x600508e000000000", null)));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("RHEL 계열 설치")))
                .andExpect(content().string(containsString("Rocky Linux 9.5")))
                .andExpect(content().string(containsString("정상: 다음 부팅에서 설치를 시작할 수 있습니다")))
                .andExpect(content().string(containsString("<code class=\"n-table-mono\">wwn-0x600508e000000000</code>")))
                .andExpect(content().string(containsString("예정")))
                .andExpect(content().string(not(containsString("대상 이미지"))));
    }

    @Test
    @DisplayName("RHEL 설치 중 — by-id 는 고정폭(code.n-table-mono) · 설치 중 · 재부팅 n/max · 남은 시간")
    void rhel_running() throws Exception {
        UUID id = detailWith(card("RHEL 계열", ReadinessGrade.READY, List.of(), LocalDateTime.of(2026, 11, 19, 10, 0), null, null, false,
                new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", "wwn-0x600508e000000000", null)));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<code class=\"n-table-mono\">wwn-0x600508e000000000</code>")))
                .andExpect(content().string(containsString("설치 중")))
                .andExpect(content().string(containsString("재부팅 1/5회")))
                .andExpect(content().string(containsString("남은 시간 42분")));
    }

    @Test
    @DisplayName("RHEL 실패 — 계열이 남긴 화면 문구(버전 불일치 + 다음 행동)를 경보에 그대로")
    void rhel_failedWithMessage() throws Exception {
        String message = "설치 트리 버전(Rocky Linux 10.0)과 정의서 버전(Rocky Linux 9.5)이 다릅니다. ISO 를 실제 OS 버전 아래에 다시 등록하거나 정의서에서 다른 ISO 를 골라야 합니다.";
        UUID id = detailWith(card("RHEL 계열", ReadinessGrade.READY, List.of(), null, "TREE_RELEASE_MISMATCH", message, false,
                new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", null, null)));
        String html = mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html.split(java.util.regex.Pattern.quote(message), -1)).hasSize(2);   // CP5 F-1 — 한 번만
    }

    @Test
    @DisplayName("F-1 — RHEL 결손 대기도 배너 'OS 설치 자원 대기' 와 카드의 막힘 사유가 선다")
    void rhel_holdingShowsReason() throws Exception {
        UUID id = detailWith(card("RHEL 계열", ReadinessGrade.BLOCKED, List.of("설치 트리 디렉터리가 설정되지 않았습니다. 관리자에게 확인해야 합니다."),
                null, null, null, true, new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", null, null)));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OS 설치 자원 대기")))
                .andExpect(content().string(containsString("설치 트리 디렉터리가 설정되지 않았습니다.")))
                .andExpect(content().string(not(containsString("Windows 설치 자원 대기"))));
    }

    @Test
    @DisplayName("지원하지 않는 계열 — 제목과 막힘 사유만 · 계열 이름이 비면 제목 'OS 설치'")
    void unsupported() throws Exception {
        String reason = "Debian 계열 설치는 지원하지 않습니다. 정의서의 OS 설치 단계를 지원하는 OS 로 바꿔야 합니다.";
        UUID id = detailWith(card("Debian 계열", ReadinessGrade.BLOCKED, List.of(reason), null, null, null, false,
                new GuestServerDetailResponse.UnsupportedOsInstall()));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Debian 계열 설치")))
                .andExpect(content().string(containsString(reason)))
                .andExpect(content().string(not(containsString("대상 OS"))));

        UUID unknown = detailWith(card(null, ReadinessGrade.BLOCKED, List.of("사유"), null, null, null, false,
                new GuestServerDetailResponse.UnsupportedOsInstall()));
        mvc.perform(get("/provisioning/server/{id}", unknown))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("OS 설치")));
    }
}
