package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.support.FixedClockTestConfig;
import org.springframework.context.annotation.Import;
import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.osinstall.InstallConfirmation;
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
                new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", "wwn-0x600508e000000000", null, null)));
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
                new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", "wwn-0x600508e000000000", null, null)));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<code class=\"n-table-mono\">wwn-0x600508e000000000</code>")))
                .andExpect(content().string(containsString("설치 중")))
                .andExpect(content().string(containsString("재부팅 1/5회")))
                .andExpect(content().string(containsString("남은 시간 42분")));
    }

    private static GuestServerDetailResponse.OsInstall completedCard(String family, boolean terminal,
                                                                    GuestServerDetailResponse.OsInstallCardDetail detail) {
        return new GuestServerDetailResponse.OsInstall(family, ReadinessGrade.READY, List.of(), LocalDateTime.of(2026, 11, 20, 15, 0),
                1, 5, null, null, null, false, 0L, LocalDateTime.of(2026, 11, 20, 15, 40, 12), terminal,
                terminal ? null : ProvisioningPhase.OS_SETTING, detail);
    }

    private static final String REPORTED_UUID = "11111111-2222-3333-4444-555555555555";

    private static GuestServerDetailResponse.RhelInstall rhelCompleted(InstallConfirmation disk, String reportedUuid,
                                                                       InstallConfirmation identity) {
        return new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", "wwn-0x600508e000000000", null,
                new GuestServerDetailResponse.RhelInstall.Result("Rocky Linux 9.5 (Blue Onyx)", "5.14.0-503.14.1.el9_5.x86_64",
                        "localhost", "wwn-0x600508e000000000", disk, reportedUuid, identity));
    }

    @Test
    @DisplayName("RHEL 완료 — 완료 배지 · 완료 시각과 호스트 이름 · 설치 결과 절(다음 · 설치 OS와 커널 · 설치 디스크 · 대상 서버)")
    void rhel_completed() throws Exception {
        UUID id = detailWith(completedCard("RHEL 계열", false, rhelCompleted(InstallConfirmation.CONFIRMED, REPORTED_UUID, InstallConfirmation.CONFIRMED)));
        String html = mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("완료 2026-11-20 15:40:12, 호스트 이름 localhost")))
                .andExpect(content().string(containsString("설치 결과")))
                .andExpect(content().string(containsString("다음 단계로 넘어갔습니다: OS 설정")))
                .andExpect(content().string(containsString("Rocky Linux 9.5 (Blue Onyx)")))
                .andExpect(content().string(containsString("<div class=\"n-note\">5.14.0-503.14.1.el9_5.x86_64</div>")))
                .andExpect(content().string(containsString("대상 서버")))
                .andExpect(content().string(containsString("<code class=\"n-table-mono\">" + REPORTED_UUID + "</code>")))
                .andExpect(content().string(not(containsString("설치 중"))))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html.split("<span class=\"n-badge n-badge-teal\">일치</span>", -1)).hasSize(3);
    }

    @Test
    @DisplayName("RHEL 완료 · 불일치와 미보고 — 디스크 불일치는 경보, 대상 서버 미보고는 안내 · 종단이면 마지막 단계 문구")
    void rhel_completedMismatchAndUnreported() throws Exception {
        UUID id = detailWith(completedCard("RHEL 계열", true, rhelCompleted(InstallConfirmation.MISMATCH, null, InstallConfirmation.UNREPORTED)));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("불일치")))
                .andExpect(content().string(containsString("설치된 디스크가 계획한 OS 볼륨과 다릅니다.")))
                .andExpect(content().string(containsString("미보고")))
                .andExpect(content().string(containsString("대상 서버 확인 정보가 없습니다.")))
                .andExpect(content().string(containsString("마지막 단계였습니다.")));
    }

    @Test
    @DisplayName("RHEL 완료 · 대상 서버 불일치 — 경보와 함께 보고한 기계의 시스템 UUID 를 보인다")
    void rhel_identityMismatchShowsReportedUuid() throws Exception {
        UUID id = detailWith(completedCard("RHEL 계열", true,
                rhelCompleted(InstallConfirmation.CONFIRMED, REPORTED_UUID, InstallConfirmation.MISMATCH)));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("보고한 서버의 시스템 UUID 가 이 게스트 서버와 다릅니다.")))
                .andExpect(content().string(containsString("<code class=\"n-table-mono\">" + REPORTED_UUID + "</code>")));
    }

    @Test
    @DisplayName("Windows 완료 — 공통 조각으로 옮긴 다음 행 · 설치 디스크 확증이 전과 같은 문구로 그려진다")
    void windows_completedUsesSharedRows() throws Exception {
        UUID id = detailWith(completedCard("Windows", true, new GuestServerDetailResponse.WindowsInstall(
                "Windows Server 2025 SERVERSTANDARD", null, "SPV-14174000", null, 47, 0, List.of(), 0, null,
                InstallConfirmation.MISMATCH, null, List.of(), List.of(), List.of(), List.of())));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("설치 결과")))
                .andExpect(content().string(containsString("마지막 단계였습니다.")))
                .andExpect(content().string(containsString("설치 디스크")))
                .andExpect(content().string(containsString("<span class=\"n-badge n-badge-red\">불일치</span>")))
                .andExpect(content().string(containsString("설치된 디스크가 계획한 OS 볼륨과 다릅니다.")))
                .andExpect(content().string(not(containsString("대상 서버"))));
    }

    @Test
    @DisplayName("RHEL 실패 — 계열이 남긴 화면 문구(버전 불일치 + 다음 행동)를 경보에 그대로")
    void rhel_failedWithMessage() throws Exception {
        String message = "설치 트리 버전(Rocky Linux 10.0)과 정의서 버전(Rocky Linux 9.5)이 다릅니다. ISO 를 실제 OS 버전 아래에 다시 등록하거나 정의서에서 다른 ISO 를 골라야 합니다.";
        UUID id = detailWith(card("RHEL 계열", ReadinessGrade.READY, List.of(), null, "TREE_RELEASE_MISMATCH", message, false,
                new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", null, null, null)));
        String html = mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html.split(java.util.regex.Pattern.quote(message), -1)).hasSize(2);   // CP5 F-1 — 한 번만
    }

    @Test
    @DisplayName("F-1 — RHEL 결손 대기도 배너 'OS 설치 자원 대기' 와 카드의 막힘 사유가 선다")
    void rhel_holdingShowsReason() throws Exception {
        UUID id = detailWith(card("RHEL 계열", ReadinessGrade.BLOCKED, List.of("설치 트리 디렉터리가 설정되지 않았습니다. 관리자에게 확인해야 합니다."),
                null, null, null, true, new GuestServerDetailResponse.RhelInstall("Rocky Linux 9.5", null, null, null)));
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

    @Test
    @DisplayName("E4-1-b-7 OS 설정 패널 — 실패면 경보를 다시 그리지 않고 기본 설정 표 · 스크립트 표(종료 코드 · 실행 기록) · 판정 배지")
    void osSettingPanel_failed() throws Exception {
        UUID id = detailWith(null);
        given(assignmentQueryService.plannedPhasesOf(id)).willReturn(new AssignmentPlanResponse(true, "rhel-standard",
                com.example.serverprovision.provisioning.assignment.enums.AssignmentState.ACTIVE_CONSUMED, null, null,
                List.of(ProvisioningPhase.OS_INSTALLING, ProvisioningPhase.OS_SETTING)));
        given(queryService.findOsSettingOutcome(id)).willReturn(java.util.Optional.of(new com.example.serverprovision.execution.dto.response.OsSettingOutcomeResponse(
                false,
                List.of(new com.example.serverprovision.execution.dto.response.OsSettingOutcomeResponse.Row("SELinux", "enforcing", "enforcing", true, null),
                        new com.example.serverprovision.execution.dto.response.OsSettingOutcomeResponse.Row("vim", "installed", "missing", false, null)),
                List.of(new com.example.serverprovision.execution.dto.response.OsSettingOutcomeResponse.Row("1. init-ntp.sh", null, "3", false, "/root/spv-script-1.log")),
                "패키지 vim 의 설치를 확인하지 못했습니다. 스크립트 1번(init-ntp.sh)의 종료 코드가 3 입니다.")));

        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(status().isOk())
                // 실패 사유는 phase 패널 공통 경보(원장 사유)가 맡는다 — 조각은 같은 문장을 다시 그리지 않는다(CP5 F-2)
                .andExpect(content().string(not(containsString("n-alert-danger\">패키지 vim 의 설치를 확인하지 못했습니다."))))
                .andExpect(content().string(containsString("<div class=\"n-section\">기본 설정</div>")))
                .andExpect(content().string(containsString("<code class=\"n-table-mono\">missing</code>")))
                .andExpect(content().string(containsString("<td>1. init-ntp.sh</td>")))
                .andExpect(content().string(containsString("<code class=\"n-table-mono\">/root/spv-script-1.log</code>")))
                .andExpect(content().string(containsString("<span class=\"n-badge n-badge-red\">실패</span>")));
    }

    @Test
    @DisplayName("E4-1-b-7 OS 설정 패널 — 성공이면 직접 확인 안내(정보 띠), 판정 전이면 패널 본문 없음")
    void osSettingPanel_passedAndPending() throws Exception {
        UUID id = detailWith(null);
        given(assignmentQueryService.plannedPhasesOf(id)).willReturn(new AssignmentPlanResponse(true, "rhel-standard",
                com.example.serverprovision.provisioning.assignment.enums.AssignmentState.ACTIVE_CONSUMED, null, null,
                List.of(ProvisioningPhase.OS_INSTALLING, ProvisioningPhase.OS_SETTING)));
        given(queryService.findOsSettingOutcome(id)).willReturn(java.util.Optional.of(new com.example.serverprovision.execution.dto.response.OsSettingOutcomeResponse(
                true, List.of(), List.of(), "스크립트 실행은 끝났지만 적용 여부는 서버에서 직접 확인해야 합니다.")));
        mvc.perform(get("/provisioning/server/{id}", id))
                .andExpect(content().string(containsString("class=\"n-alert n-alert-info\">스크립트 실행은 끝났지만 적용 여부는 서버에서 직접 확인해야 합니다.</div>")));

        UUID pending = detailWith(null);
        given(assignmentQueryService.plannedPhasesOf(pending)).willReturn(new AssignmentPlanResponse(true, "rhel-standard",
                com.example.serverprovision.provisioning.assignment.enums.AssignmentState.ACTIVE_CONSUMED, null, null,
                List.of(ProvisioningPhase.OS_INSTALLING, ProvisioningPhase.OS_SETTING)));
        mvc.perform(get("/provisioning/server/{id}", pending))
                .andExpect(content().string(not(containsString("<div class=\"n-section\">기본 설정</div>"))));
    }
}
