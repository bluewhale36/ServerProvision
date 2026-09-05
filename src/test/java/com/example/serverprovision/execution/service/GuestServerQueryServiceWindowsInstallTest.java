package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.WorkerObservations;
import com.example.serverprovision.execution.engine.firmware.FirmwareResolutionProvider;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.phase.HoldTtlPolicy;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.engine.raid.RaidConfigurationResolutionProvider;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.engine.windows.WindowsInstallLedger;
import com.example.serverprovision.execution.engine.windows.WindowsInstallReadinessResolver;
import com.example.serverprovision.execution.engine.windows.WindowsInstallTarget;
import com.example.serverprovision.execution.engine.windows.WindowsInstallTimeoutPolicy;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.HostNicBindingRepository;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.repository.RaidVolumeRepository;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * E4-1-a-3 CP4 — 상세 응답의 Windows 설치 카드 조립. 준비도는 실행기와 같은 조립기(mock)에서, 진행은 실 원장(meta 왕복)에서,
 * 잔여 분은 실 시한 정책에서 온다 — 화면과 실행기가 같은 값을 보는지가 요점이다.
 */
@ExtendWith(MockitoExtension.class)
class GuestServerQueryServiceWindowsInstallTest {

    private static final WindowsImageName STANDARD = new WindowsImageName("Windows Server 2025 SERVERSTANDARD");

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
    @Mock WindowsInstallReadinessResolver resolver;

    private final LocalDateTime now = LocalDateTime.now();
    private final GuestServer server = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    private WindowsInstallLedger ledger;
    private GuestServerQueryService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(recorder.openRunning(any(), any(), any(), any())).thenAnswer(inv -> ProvisioningHistory.openRunning(
                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
        ledger = new WindowsInstallLedger(recorder, historyRepository, new ObjectMapper());
        service = new GuestServerQueryService(guestServerRepository, raidConfigurationResolutionProvider,
                raidVolumeRepository, detailRepository, nicRepository, progressRepository, historyRepository,
                firmwareResolutionProvider, holdTtlPolicy, retryPolicy, new FlashTimeoutPolicy(new MockEnvironment()),
                new SettingLedger(recorder, new ObjectMapper()), new WorkerObservations(), new ObjectMapper(),
                resolver, ledger, new WindowsInstallTimeoutPolicy(Duration.ofMinutes(60), 5, Duration.ofMinutes(30)),
                java.time.Clock.systemDefaultZone());
        given(guestServerRepository.findById(server.getId())).willReturn(Optional.of(server));
        given(detailRepository.findByServerIdWithBoardModel(server.getId())).willReturn(Optional.empty());
        given(nicRepository.findAllByServerIdOrderByPrimary(server.getId())).willReturn(List.of());
        given(firmwareResolutionProvider.resolveFor(any())).willReturn(Optional.empty());
    }

    private static WindowsImage image() {
        return new WindowsImage(2, STANDARD, "Windows Server 2025 Standard (데스크톱 환경)", "ServerStandard", "Server", "ko-KR", "10.0.26100.1742");
    }

    private static WindowsInstallReadinessResolver.Resolved resolved(PhaseReadiness readiness) {
        return new WindowsInstallReadinessResolver.Resolved(WindowsInstallTarget.windows(STANDARD, "P@ss"),
                InstallSourceSnapshot.present(List.of(image()), 1L, Instant.now()), Optional.of(image()), readiness);
    }

    private ProvisioningProgress progressAt(ProvisioningPhaseStep step) {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(server)
                .currentStep(step).lastTransitionAt(now).build();
        p.start(now);
        return p;
    }

    private GuestServerDetailResponse.WindowsInstall cardWith(ProvisioningProgress progress, List<ProvisioningHistory> rows) {
        given(progressRepository.findByGuestServer_Id(server.getId())).willReturn(Optional.of(progress));
        given(historyRepository.findAllByServerIdOrderByStartedAt(server.getId())).willReturn(rows);
        return service.findDetail(server.getId()).windowsInstall();
    }

    @Test
    @DisplayName("창 밖 + 원장 행 없음 → 카드 null(화면은 그리지 않는다)")
    void outsideWindow_noCard() {
        given(resolver.resolve(server.getId())).willReturn(Optional.empty());
        assertThat(cardWith(progressAt(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING), List.of())).isNull();
    }

    @Test
    @DisplayName("서빙 전 · READY — 이미지 · 표시명 · 등급 READY · served false · 잔여 null · 상한 5")
    void beforeServing_ready() {
        given(resolver.resolve(server.getId())).willReturn(Optional.of(resolved(PhaseReadiness.ready())));

        var card = cardWith(progressAt(ProvisioningPhaseStep.OS_INSTALLING), List.of());

        assertThat(card.imageName()).isEqualTo(STANDARD.value());
        assertThat(card.imageDisplayName()).isEqualTo("Windows Server 2025 Standard (데스크톱 환경)");
        assertThat(card.readinessGrade()).isEqualTo(ReadinessGrade.READY);
        assertThat(card.served()).isFalse();
        assertThat(card.remainingMinutes()).isNull();
        assertThat(card.maxReentries()).isEqualTo(5);
        assertThat(card.failedReason()).isNull();
        assertThat(card.holding()).isFalse();
    }

    @Test
    @DisplayName("설치 중 — RUNNING 행의 served · reentries 와 시한 정책의 잔여 분(60 - 경과)이 카드에 실린다")
    void running_projectsLedgerRow() {
        given(resolver.resolve(server.getId())).willReturn(Optional.of(resolved(PhaseReadiness.ready())));
        LocalDateTime served = now.minusMinutes(18).minusSeconds(10);
        ProvisioningHistory row = ledger.openServed(server, STANDARD, served);
        ledger.bumpReentry(row, now.minusMinutes(9));
        ledger.bumpReentry(row, now.minusMinutes(2));
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.OS_INSTALLING);
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, served);

        var card = cardWith(progress, List.of(row));

        assertThat(card.served()).isTrue();
        assertThat(card.servedAt()).isEqualTo(served);
        assertThat(card.reentries()).isEqualTo(2);
        assertThat(card.remainingMinutes()).isEqualTo(42L);
        assertThat(card.failedReason()).isNull();
    }

    @Test
    @DisplayName("실패 — 원장이 닫은 FAILED 행의 사유(REPXE_LOOP)가 실리고 served 는 false(열린 행 없음)")
    void failed_showsReason() {
        given(resolver.resolve(server.getId())).willReturn(Optional.of(resolved(PhaseReadiness.ready())));
        ProvisioningHistory row = ledger.openServed(server, STANDARD, now.minusMinutes(30));
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.OS_INSTALLING);
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, now.minusMinutes(30));
        ledger.failRunning(server, progress, row, WindowsInstallLedger.REPXE_LOOP, "재진입 6회", now);

        var card = cardWith(progress, List.of(row));

        assertThat(card.failedReason()).isEqualTo("REPXE_LOOP");
        assertThat(card.served()).isFalse();
        assertThat(card.reentries()).isZero();
    }

    @Test
    @DisplayName("결손 대기 — BLOCKED 사유 목록 + holding + 대기 잔여 분(HoldTtlPolicy)")
    void holding_showsNotesAndTtl() {
        given(resolver.resolve(server.getId())).willReturn(Optional.of(resolved(
                PhaseReadiness.of(ReadinessGrade.BLOCKED, List.of("install.wim 없음 — 대시보드 Windows 설치 소스 영역"), "install.wim missing"))));
        given(holdTtlPolicy.remainingMinutes(any(), any())).willReturn(100L);
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.OS_INSTALLING);
        progress.holdForShortage(now);

        var card = cardWith(progress, List.of());

        assertThat(card.readinessGrade()).isEqualTo(ReadinessGrade.BLOCKED);
        assertThat(card.readinessNotes()).containsExactly("install.wim 없음 — 대시보드 Windows 설치 소스 영역");
        assertThat(card.holding()).isTrue();
        assertThat(card.holdRemainingMinutes()).isEqualTo(100L);
    }

    @Test
    @DisplayName("할당이 사라졌어도 원장 행이 있으면 카드는 남는다 — 이미지는 행에서, 준비도는 없음")
    void ledgerOnly_keepsCard() {
        given(resolver.resolve(server.getId())).willReturn(Optional.empty());
        ProvisioningHistory row = ledger.openServed(server, STANDARD, now.minusMinutes(3));
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.OS_INSTALLING);
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, now.minusMinutes(3));

        var card = cardWith(progress, List.of(row));

        assertThat(card).isNotNull();
        assertThat(card.imageName()).isEqualTo(STANDARD.value());
        assertThat(card.readinessGrade()).isNull();
        assertThat(card.served()).isTrue();
    }

    @Test
    @DisplayName("CP5 F-1 — 운영자 수동 실패로 닫힌 행은 OPERATOR 사유 · 실패 뒤 남은 열린 행(구 데이터)은 진행으로 보지 않는다")
    void operatorFailed_showsReason_andIgnoresStaleRunningRow() {
        given(resolver.resolve(server.getId())).willReturn(Optional.of(resolved(PhaseReadiness.ready())));
        ProvisioningHistory row = ledger.openServed(server, STANDARD, now.minusMinutes(10));
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.OS_INSTALLING);
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, now.minusMinutes(10));
        progress.markFailedManually(now);
        ledger.abortRunning(row, WindowsInstallLedger.OPERATOR, "운영자 수동 실패 전환", now);

        var card = cardWith(progress, List.of(row));
        assertThat(card.failedReason()).isEqualTo("OPERATOR");
        assertThat(card.served()).isFalse();

        ProvisioningHistory stale = ledger.openServed(server, STANDARD, now.minusMinutes(5));   // 훅 이전에 남은 열린 행
        ProvisioningProgress failedProgress = progressAt(ProvisioningPhaseStep.OS_INSTALLING);
        failedProgress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, now.minusMinutes(5));
        failedProgress.markFailedManually(now);
        var staleCard = cardWith(failedProgress, List.of(stale));
        assertThat(staleCard.served()).isFalse();
        assertThat(staleCard.failedReason()).isNull();
    }

    @Test
    @DisplayName("완료 행(E4-1-a-4) — served false · completed true · 완료 시각 · ComputerName · OS · 드라이버 · 문제 장치 목록 · 종단이면 provisioningCompleted · 잔여 null")
    void completedRow_cardShowsCompletion() {
        given(resolver.resolve(server.getId())).willReturn(Optional.of(resolved(PhaseReadiness.ready())));
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.OS_INSTALLING);
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, now);
        ProvisioningHistory row = ledger.openServed(server, STANDARD, now.minusMinutes(30));
        ledger.closeSucceeded(row, new WindowsInstallLedger.Completion("SPV-14174000", "Windows Server 2025 10.0.26100",
                47, 2, List.of("Unknown device (ACPI\\INT34C6)", "PCI Simple Communications Controller"), null), now.minusMinutes(2));
        progress.markCompleted(now.minusMinutes(2));

        var card = cardWith(progress, List.of(row));

        assertThat(card.served()).isFalse();
        assertThat(card.completed()).isTrue();
        assertThat(card.servedAt()).isEqualTo(now.minusMinutes(30));
        assertThat(card.completedAt()).isEqualTo(now.minusMinutes(2));
        assertThat(card.computerName()).isEqualTo("SPV-14174000");
        assertThat(card.osVersion()).isEqualTo("Windows Server 2025 10.0.26100");
        assertThat(card.driversAdded()).isEqualTo(47);
        assertThat(card.problemDeviceCount()).isEqualTo(2);
        assertThat(card.problemDevices()).hasSize(2);
        assertThat(card.remainingMinutes()).isNull();
        assertThat(card.failedReason()).isNull();
        assertThat(card.provisioningCompleted()).isTrue();
        assertThat(card.nextPhase()).isNull();
    }

    @Test
    @DisplayName("완료 뒤 다음 소유 phase 로 전진한 게스트 — provisioningCompleted false · nextPhase = 커서 phase")
    void completedRow_nextPhase() {
        given(resolver.resolve(server.getId())).willReturn(Optional.of(resolved(PhaseReadiness.ready())));
        ProvisioningProgress progress = progressAt(ProvisioningPhaseStep.OS_INSTALLING);
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, now);
        ProvisioningHistory row = ledger.openServed(server, STANDARD, now.minusMinutes(30));
        ledger.closeSucceeded(row, new WindowsInstallLedger.Completion("SPV-1", null, 0, 0, List.of(), null), now.minusMinutes(2));
        progress.advanceToEntry(ProvisioningPhaseStep.entryOf(ProvisioningPhase.TESTING), now.minusMinutes(2));

        var card = cardWith(progress, List.of(row));

        assertThat(card.completed()).isTrue();
        assertThat(card.provisioningCompleted()).isFalse();
        assertThat(card.nextPhase()).isEqualTo(ProvisioningPhase.TESTING);
    }
}
