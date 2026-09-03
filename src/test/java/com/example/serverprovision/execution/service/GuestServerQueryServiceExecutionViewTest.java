package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.dto.response.GuestServerSummaryResponse;
import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.WorkerObservations;
import com.example.serverprovision.execution.engine.firmware.FirmwareResolutionProvider;
import com.example.serverprovision.execution.engine.raid.RaidConfigurationResolutionProvider;
import com.example.serverprovision.execution.repository.RaidVolumeRepository;
import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.phase.HoldTtlPolicy;
import com.example.serverprovision.execution.engine.setting.BiosSettingTarget;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * E2-4 — 집행 현황의 화면 파생(구간 진리표 · 설정 축 국면 · 사유 · 대기 사유 · 목록 배지 재료).
 * 판정 재료는 원장 · progress 뿐이고 기점 · 시한은 엔진과 같은 식이어야 한다(D-3).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GuestServerQueryServiceExecutionViewTest {

    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository detailRepository;
    @Mock HostNicBindingRepository nicRepository;
    @Mock ProvisioningProgressRepository progressRepository;
    @Mock ProvisioningHistoryRepository historyRepository;
    @Mock FirmwareResolutionProvider firmwareResolutionProvider;
    @Mock RaidConfigurationResolutionProvider raidConfigurationResolutionProvider;
    @Mock RaidVolumeRepository raidVolumeRepository;
    @Mock HoldTtlPolicy holdTtlPolicy;
    @Mock RetryPolicy retryPolicy;
    @Mock ProvisioningHistoryRecorder recorder;

    private GuestServerQueryService service;
    private SettingLedger settingLedger;
    private final WorkerObservations observations = new WorkerObservations();

    private final LocalDateTime now = LocalDateTime.now();
    private final GuestServer server = GuestServer.builder()
            .id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        settingLedger = new SettingLedger(recorder, new ObjectMapper());
        given(recorder.openRunning(any(), any(), any(), any())).willAnswer(inv -> ProvisioningHistory.openRunning(
                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
        service = new GuestServerQueryService(guestServerRepository, raidConfigurationResolutionProvider,
                raidVolumeRepository, detailRepository, nicRepository,
                progressRepository, historyRepository, firmwareResolutionProvider, holdTtlPolicy, retryPolicy,
                new FlashTimeoutPolicy(new MockEnvironment()), settingLedger, observations, new ObjectMapper(),
                org.mockito.Mockito.mock(com.example.serverprovision.execution.engine.windows.WindowsInstallReadinessResolver.class),   // E4-1-a-3 — 창 밖(empty)
                new com.example.serverprovision.execution.engine.windows.WindowsInstallLedger(recorder, historyRepository, new ObjectMapper()),
                new com.example.serverprovision.execution.engine.windows.WindowsInstallTimeoutPolicy(java.time.Duration.ofMinutes(60), 5));
        given(guestServerRepository.findById(server.getId())).willReturn(Optional.of(server));
        given(detailRepository.findByServerIdWithBoardModel(server.getId())).willReturn(Optional.empty());
        given(nicRepository.findAllByServerIdOrderByPrimary(server.getId())).willReturn(List.of());
        given(firmwareResolutionProvider.resolveFor(any())).willReturn(Optional.empty());
    }

    private GuestServerDetailResponse detailWith(ProvisioningProgress progress, List<ProvisioningHistory> rows) {
        given(progressRepository.findByGuestServer_Id(server.getId())).willReturn(Optional.of(progress));
        given(historyRepository.findAllByServerIdOrderByStartedAt(server.getId())).willReturn(rows);
        return service.findDetail(server.getId());
    }

    /** 커서를 빌더에서 직접 놓는다 — {@code positionAt} 은 phase 이탈을 막는 가드가 있다(교차는 advanceToEntry 전용). */
    private ProvisioningProgress progressAt(ProvisioningPhaseStep step, LocalDateTime start) {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID()).guestServer(server)
                .currentStep(step)
                .lastTransitionAt(start).build();
        p.start(start);
        return p;
    }

    private ProvisioningHistory closedAxis(ProvisioningPhaseStep step, String name, String version, LocalDateTime at) {
        ProvisioningHistory row = ProvisioningHistory.openRunning(server, step, at.minusMinutes(1),
                ProvisioningHistory.flashTargetMeta(name, version, 1L, "/task/1"));
        row.closeFlash(ProvisioningStatus.SUCCEEDED, FlashLedger.FLASH_COMPLETED, "전송 완료", at);
        return row;
    }

    private ProvisioningHistory powerEvent(ProvisioningPhaseStep step, String reason, String detail, LocalDateTime at) {
        return ProvisioningHistory.instant(server, step, ProvisioningStatus.SUCCEEDED,
                ProvisioningHistory.flashOutcomeMeta(reason, detail), at);
    }

    @Test
    @DisplayName("진리표 1행 — 열린 굽기가 있으면 '{축} 굽는 중' + 기점은 그 행 startedAt(엔진과 같은 시계)")
    void stageRow1_running() {
        ProvisioningHistory running = ProvisioningHistory.openRunning(server, ProvisioningPhaseStep.BIOS_UPDATING,
                now.minusMinutes(5), ProvisioningHistory.flashTargetMeta("BIOS 표준", "F29", 1L, "/task/1"));

        GuestServerDetailResponse detail = detailWith(
                progressAt(ProvisioningPhaseStep.BIOS_UPDATING, now.minusMinutes(6)), List.of(running));

        assertThat(detail.firmwareFlash().stageText()).isEqualTo("BIOS 굽는 중");
        // BIOS 기본 시한 15분 · 5분 경과 — 기점이 행 startedAt 이라야 이 범위다(lastTransitionAt 이면 달라진다).
        assertThat(detail.firmwareFlash().stageRemainingMinutes()).isBetween(9L, 10L);
        assertThat(detail.firmwareFlash().axes())
                .anySatisfy(axis -> assertThat(axis.targetVersion()).isEqualTo("BIOS 표준 (F29)"));   // R7
    }

    @Test
    @DisplayName("진리표 1b행 — 한 축만 닫히고 다음 축이 아직이면 '다음 축 착수 대기'")
    void stageRow1b_gapBetweenAxes() {
        GuestServerDetailResponse detail = detailWith(
                progressAt(ProvisioningPhaseStep.BIOS_UPDATING, now.minusMinutes(10)),
                List.of(closedAxis(ProvisioningPhaseStep.BIOS_UPDATING, "BIOS 표준", "F29", now.minusMinutes(2))));

        assertThat(detail.firmwareFlash().stageText()).isEqualTo("다음 축 착수 대기(다음 워커 주기)");
        assertThat(detail.firmwareFlash().stageRemainingMinutes()).isNull();
    }

    @Test
    @DisplayName("진리표 2 · 3행 — 축 전부 닫힘 뒤 전원 사건 행 유무가 '투입 대기' 와 '복귀 대기(기점 = 사건 행)' 를 가른다")
    void stageRow2And3_powerEventSwitches() {
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.BMC_UPDATING, now.minusMinutes(30));
        List<ProvisioningHistory> closed = List.of(
                closedAxis(ProvisioningPhaseStep.BIOS_UPDATING, "BIOS 표준", "F29", now.minusMinutes(18)),
                closedAxis(ProvisioningPhaseStep.BMC_UPDATING, "BMC 표준", "13.06.27", now.minusMinutes(18)));

        assertThat(detailWith(progress, closed).firmwareFlash().stageText())
                .isEqualTo("전원 투입 대기(다음 워커 주기)");   // 2행

        List<ProvisioningHistory> withPowerOn = List.of(closed.get(0), closed.get(1),
                powerEvent(ProvisioningPhaseStep.BMC_UPDATING, FlashLedger.POWER_ON,
                        "다음 부팅 PXE 강제 : 반영 확인 · 전원이 켜졌습니다", now.minusMinutes(5)));
        GuestServerDetailResponse detail = detailWith(progress, withPowerOn);
        assertThat(detail.firmwareFlash().stageText()).isEqualTo("전원 투입 — 게스트 복귀 대기");   // 3행
        // 복귀 시한 20분 · 기점 = 전원 사건 행(5분 전) — 축 종료(18분 전)가 기점이면 2분 이하로 떨어진다.
        assertThat(detail.firmwareFlash().stageRemainingMinutes()).isBetween(14L, 15L);
    }

    @Test
    @DisplayName("진리표 4행 — 전원 투입 뒤 접촉이 살아나면 '반영 확인 대기'")
    void stageRow4_guestReturned() {
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.BMC_UPDATING, now.minusMinutes(30));
        server.touchSeen(now.minusMinutes(1));

        GuestServerDetailResponse detail = detailWith(progress, List.of(
                closedAxis(ProvisioningPhaseStep.BIOS_UPDATING, "BIOS 표준", "F29", now.minusMinutes(18)),
                closedAxis(ProvisioningPhaseStep.BMC_UPDATING, "BMC 표준", "13.06.27", now.minusMinutes(18)),
                powerEvent(ProvisioningPhaseStep.BMC_UPDATING, FlashLedger.POWER_ON, "전원 투입", now.minusMinutes(5))));

        assertThat(detail.firmwareFlash().stageText()).isEqualTo("게스트 복귀 — 반영 확인 대기(다음 워커 주기)");
    }

    @Test
    @DisplayName("진리표 5행 — 커서가 phase 를 떠났으면 '반영 확인 완료 · 전진'(카드는 완료 요약 유지)")
    void stageRow5_advanced() {
        GuestServerDetailResponse detail = detailWith(
                progressAt(ProvisioningPhaseStep.BIOS_SETTING, now.minusMinutes(30)),
                List.of(closedAxis(ProvisioningPhaseStep.BIOS_UPDATING, "BIOS 표준", "F29", now.minusMinutes(18))));

        assertThat(detail.firmwareFlash().stageText()).startsWith("반영 확인 완료 — ");
    }

    @Test
    @DisplayName("사유 열(R5) — 실패 행의 detail 이 steps.note 로 나온다 · 하트비트는 최신 관측을 싣는다")
    void noteAndHeartbeat() {
        observations.note(server.getId(), "BMC Task 확인 — 굽는 중", now.minusSeconds(10));
        ProvisioningHistory failed = ProvisioningHistory.instant(server, ProvisioningPhaseStep.BIOS_UPDATING,
                ProvisioningStatus.FAILED,
                ProvisioningHistory.flashOutcomeMeta(FlashLedger.VERIFY_MISMATCH, "목표 F29 · 확인 F27"), now);

        GuestServerDetailResponse detail = detailWith(
                progressAt(ProvisioningPhaseStep.BIOS_UPDATING, now.minusMinutes(5)), List.of(failed));

        assertThat(detail.steps()).anySatisfy(step -> assertThat(step.note()).isEqualTo("목표 F29 · 확인 F27"));
        assertThat(detail.firmwareFlash()).isNotNull();
        assertThat(detail.firmwareFlash().lastObservation()).endsWith("확인 — BMC Task 확인 — 굽는 중");
    }

    @Test
    @DisplayName("설정 축 국면 — 재부팅 뒤 접촉이 살아나면 'readback 대기', 원장 meta 가 유일한 재료다")
    void settingAxisReadbackStage() {
        ProvisioningHistory row = settingLedger.open(server, new BiosSettingTarget(Map.of("A", "B")), now.minusMinutes(3));
        settingLedger.markRebooted(row, now.minusMinutes(2), "다음 부팅 PXE 강제 : 반영 확인 · 재시작");
        server.touchSeen(now.minusMinutes(1));

        GuestServerDetailResponse detail = detailWith(
                progressAt(ProvisioningPhaseStep.BIOS_SETTING, now.minusMinutes(4)), List.of(row));

        assertThat(detail.firmwareSetting()).isNotNull();
        assertThat(detail.firmwareSetting().axes())
                .anySatisfy(axis -> {
                    assertThat(axis.label()).isEqualTo("BIOS");
                    assertThat(axis.stageText()).isEqualTo("게스트 복귀 — 값 확인(readback) 대기(다음 워커 주기)");
                });
        assertThat(detail.firmwareSetting().waitingReason()).isNull();   // 열린 행이 있으니 침묵 대기가 아니다
    }

    @Test
    @DisplayName("대기 사유(R6) — 커서가 설정 축인데 열린 행이 없으면 침묵 사유 + 시리얼 부재 힌트")
    void settingWaitingReasonWithSerialHint() {
        GuestServerDetailResponse detail = detailWith(
                progressAt(ProvisioningPhaseStep.BMC_SETTING, now.minusMinutes(2)), List.of());

        assertThat(detail.firmwareSetting()).isNotNull();
        assertThat(detail.firmwareSetting().waitingReason())
                .contains("축 착수 대기")
                .contains("보드 시리얼이 없어");   // detail 미적재 게스트 — E3-3 O-1 의 재연
    }

    @Test
    @DisplayName("목록 배지 재료(Q6) — 요약의 disruptionBlocked 는 progress 판정을 그대로 나른다(SSOT 전달)")
    void summaryCarriesDisruptionBlocked() {
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.BIOS_UPDATING, now.minusMinutes(5));
        given(guestServerRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(server));
        given(detailRepository.findAllByServerIdInWithBoardModel(any())).willReturn(List.of());
        given(nicRepository.findPrimaryByServerIdIn(any())).willReturn(List.of());
        given(progressRepository.findAllByGuestServer_IdIn(any())).willReturn(List.of(progress));

        List<GuestServerSummaryResponse> rows = service.findAll();

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().disruptionBlocked()).isEqualTo(progress.isDisruptionBlocked());
    }

    @Test
    @DisplayName("침묵 대기 카드(CP5 F-2) — 원장 행 0 이어도 커서가 펌웨어 축이면 카드 + 사유가 그려진다")
    void flashWaitingCardWhenNoRows() {
        GuestServerDetailResponse detail = detailWith(
                progressAt(ProvisioningPhaseStep.BIOS_UPDATING, now.minusMinutes(2)), List.of());

        assertThat(detail.firmwareFlash()).isNotNull();
        assertThat(detail.firmwareFlash().axes()).hasSize(2);
        assertThat(detail.firmwareFlash().stageText())
                .contains("축 착수 대기")
                .contains("보드 시리얼이 없어");   // detail 미적재 — F-2 의 재연 상태
    }
}
