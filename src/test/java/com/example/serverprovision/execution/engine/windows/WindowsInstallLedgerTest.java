package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E4-1-a-3 CP4 — Windows 설치 원장: RUNNING 행 하나 = 서빙 한 사이클. 열 때 meta, 재진입은 meta 덧쓰기, 실패는 그 행을
 * 사유와 함께 닫되 서빙 meta 를 보존한다([[원장 close 가 메타를 덮는다]] 교훈의 이 슬라이스 판).
 */
@ExtendWith(MockitoExtension.class)
class WindowsInstallLedgerTest {

    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 11, 0);
    private static final WindowsImageName IMAGE = new WindowsImageName("Windows Server 2025 SERVERSTANDARD");

    @Mock ProvisioningHistoryRecorder recorder;
    @Mock ProvisioningHistoryRepository historyRepository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks WindowsInstallLedger ledger;

    private final GuestServer guest = GuestServer.builder().id(GUEST_ID).systemUUID(UUID.randomUUID()).build();

    @BeforeEach
    void openRunningReturnsRealRow() {
        lenient().when(recorder.openRunning(any(), any(), any(), any())).thenAnswer(inv -> ProvisioningHistory.openRunning(
                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
    }

    private ProvisioningProgress startedProgress() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(NOW).build();
        p.start(NOW);
        return p;
    }

    @Test
    @DisplayName("openServed — origin · image · served · reentries 0 을 열림 시점에 적는다(판독기 3종과 왕복)")
    void openServed_writesMeta() {
        ProvisioningHistory row = ledger.openServed(guest, IMAGE, NOW);

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(row.getStepCode()).isEqualTo(ProvisioningPhaseStep.OS_INSTALLING);
        assertThat(row.getStatusMeta()).contains("\"origin\":\"windows-install\"").doesNotContain("token").doesNotContain("password");
        assertThat(ledger.servedAtOf(row)).isEqualTo(NOW);
        assertThat(ledger.reentriesOf(row)).isZero();
        assertThat(ledger.imageOf(row)).isEqualTo(IMAGE.value());
        assertThat(ledger.isWindowsInstallRow(row)).isTrue();
    }

    @Test
    @DisplayName("bumpReentry — 같은 행의 reentries 만 오르고 served 는 그대로(행 교체 아님)")
    void bumpReentry_updatesMetaInPlace() {
        ProvisioningHistory row = ledger.openServed(guest, IMAGE, NOW);

        assertThat(ledger.bumpReentry(row, NOW.plusMinutes(8))).isEqualTo(1);
        assertThat(ledger.bumpReentry(row, NOW.plusMinutes(25))).isEqualTo(2);
        assertThat(ledger.reentriesOf(row)).isEqualTo(2);
        assertThat(ledger.servedAtOf(row)).isEqualTo(NOW);
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(row.getStatusMeta()).contains("\"lastReentryAt\":\"" + NOW.plusMinutes(25) + "\"");
    }

    @Test
    @DisplayName("failRunning — 열린 행을 FAILED 로 닫고 사유 · detail 을 얹되 image · served · reentries 는 보존 · 진행도 실패")
    void failRunning_closesRowPreservingMeta() {
        ProvisioningHistory row = ledger.openServed(guest, IMAGE, NOW);
        ledger.bumpReentry(row, NOW.plusMinutes(5));
        ProvisioningProgress progress = startedProgress();

        ledger.failRunning(guest, progress, row, WindowsInstallLedger.REPXE_LOOP, "재진입 6회 — 상한 5회 초과", NOW.plusMinutes(40));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getFinishedAt()).isEqualTo(NOW.plusMinutes(40));
        assertThat(ledger.reasonOf(row)).isEqualTo("REPXE_LOOP");
        assertThat(ledger.servedAtOf(row)).isEqualTo(NOW);
        assertThat(ledger.reentriesOf(row)).isEqualTo(1);
        assertThat(ledger.imageOf(row)).isEqualTo(IMAGE.value());
        assertThat(row.displayNote()).isEqualTo("재진입 6회 — 상한 5회 초과");   // 화면 사유 판독(E2-4 R5)과 호환
        assertThat(progress.isFailed()).isTrue();
        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("failRunning — 열린 행이 없으면(비정상) 단발 FAILED 기록으로 대신하고 진행은 실패")
    void failRunning_withoutRow_recordsInstant() {
        ProvisioningProgress progress = startedProgress();

        ledger.failRunning(guest, progress, null, WindowsInstallLedger.INSTALL_TIMEOUT, "서빙 후 61분", NOW);

        verify(recorder).recordInstant(eq(guest), eq(ProvisioningPhaseStep.OS_INSTALLING), eq(ProvisioningStatus.FAILED),
                contains("\"reason\":\"INSTALL_TIMEOUT\""), eq(NOW));
        assertThat(progress.isFailed()).isTrue();
    }

    @Test
    @DisplayName("latestRunning — 상태 조건으로 직접 묻는다(CP5 F-1 재발 원인): 뒤에 운영자 FAILED 행이 쌓여도 열린 행을 찾고, 없으면 empty")
    void latestRunning_asksByStatus() {
        ProvisioningHistory open = ledger.openServed(guest, IMAGE, NOW);
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                GUEST_ID, ProvisioningPhaseStep.OS_INSTALLING, ProvisioningStatus.RUNNING)).willReturn(Optional.of(open));
        assertThat(ledger.latestRunning(GUEST_ID)).contains(open);

        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                GUEST_ID, ProvisioningPhaseStep.OS_INSTALLING, ProvisioningStatus.RUNNING)).willReturn(Optional.empty());
        ProvisioningHistory operator = ProvisioningHistory.instant(guest, ProvisioningPhaseStep.OS_INSTALLING,
                ProvisioningStatus.FAILED, ProvisioningHistory.OPERATOR_ORIGIN_META, NOW.plusMinutes(1));
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(GUEST_ID, ProvisioningPhaseStep.OS_INSTALLING))
                .willReturn(Optional.of(operator));
        assertThat(ledger.latestRunning(GUEST_ID)).isEmpty();
        assertThat(ledger.latestOf(GUEST_ID)).contains(operator);
    }

    @Test
    @DisplayName("isWindowsInstallRow — 다른 origin(운영자 전환 · 게스트 보고) · 빈 meta 는 이 원장의 행이 아니다")
    void isWindowsInstallRow_otherRows() {
        ProvisioningHistory operator = ProvisioningHistory.instant(guest, ProvisioningPhaseStep.OS_INSTALLING,
                ProvisioningStatus.FAILED, ProvisioningHistory.OPERATOR_ORIGIN_META, NOW);
        ProvisioningHistory empty = ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, NOW);

        assertThat(ledger.isWindowsInstallRow(operator)).isFalse();
        assertThat(ledger.isWindowsInstallRow(empty)).isFalse();
        assertThat(ledger.reasonOf(operator)).isNull();
    }

    @Test
    @DisplayName("abortRunning — 진행 신호는 건드리지 않고 열린 행만 OPERATOR 사유로 닫는다(CP5 F-1) · 이미 닫힌 행은 false")
    void abortRunning_closesWithoutTouchingProgress() {
        ProvisioningHistory row = ledger.openServed(guest, IMAGE, NOW);

        assertThat(ledger.abortRunning(row, WindowsInstallLedger.OPERATOR, "운영자 수동 실패 전환", NOW.plusMinutes(3))).isTrue();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(ledger.reasonOf(row)).isEqualTo("OPERATOR");
        assertThat(ledger.servedAtOf(row)).isEqualTo(NOW);
        assertThat(ledger.isWindowsInstallRow(row)).isTrue();
        assertThat(ledger.abortRunning(row, WindowsInstallLedger.OPERATOR, "다시", NOW.plusMinutes(4))).isFalse();
    }

    @Test
    @DisplayName("closeSucceeded(E4-1-a-4) — SUCCEEDED 로 닫되 서빙 meta 보존 + 완료 meta · 판독기 6종 왕복 · 로그 꼬리는 있을 때만")
    void closeSucceeded_keepsServingMeta() {
        ProvisioningHistory row = ledger.openServed(guest, IMAGE, NOW);
        ledger.bumpReentry(row, NOW.plusMinutes(5));

        boolean closed = ledger.closeSucceeded(row, new WindowsInstallLedger.Completion("SPV-14174000", "Windows Server 2025 10.0.26100",
                47, 2, java.util.List.of("A", "B"), "tail"), NOW.plusMinutes(12));

        assertThat(closed).isTrue();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(row.getFinishedAt()).isEqualTo(NOW.plusMinutes(12));
        assertThat(ledger.servedAtOf(row)).isEqualTo(NOW);
        assertThat(ledger.reentriesOf(row)).isEqualTo(1);
        assertThat(ledger.imageOf(row)).isEqualTo(IMAGE.value());
        assertThat(ledger.isCompletedRow(row)).isTrue();
        assertThat(ledger.reasonOf(row)).isEqualTo(WindowsInstallLedger.COMPLETED);
        assertThat(ledger.completedAtOf(row)).isEqualTo(NOW.plusMinutes(12));
        assertThat(ledger.computerNameOf(row)).isEqualTo("SPV-14174000");
        assertThat(ledger.osVersionOf(row)).isEqualTo("Windows Server 2025 10.0.26100");
        assertThat(ledger.driversAddedOf(row)).isEqualTo(47);
        assertThat(ledger.problemDeviceCountOf(row)).isEqualTo(2);
        assertThat(ledger.problemDevicesOf(row)).containsExactly("A", "B");
        assertThat(row.getStatusMeta()).contains("\"setupCompleteLogTail\":\"tail\"");
        assertThat(row.displayNote()).isEqualTo("설치 완료 · 드라이버 47 · 문제 장치 2");
    }

    @Test
    @DisplayName("closeSucceeded — 이미 닫힌 행은 false · 실패로 닫힌 행은 완료 행이 아니다 · 로그 꼬리 없으면 키 생략")
    void closeSucceeded_alreadyClosedAndNotCompleted() {
        ProvisioningHistory failed = ledger.openServed(guest, IMAGE, NOW);
        ledger.abortRunning(failed, WindowsInstallLedger.OPERATOR, "운영자", NOW.plusMinutes(1));
        WindowsInstallLedger.Completion c = new WindowsInstallLedger.Completion("SPV-1", null, 0, 0, null, " ");

        assertThat(ledger.closeSucceeded(failed, c, NOW.plusMinutes(2))).isFalse();
        assertThat(ledger.isCompletedRow(failed)).isFalse();

        ProvisioningHistory row = ledger.openServed(guest, IMAGE, NOW);
        assertThat(ledger.closeSucceeded(row, c, NOW.plusMinutes(3))).isTrue();
        assertThat(row.getStatusMeta()).doesNotContain("setupCompleteLogTail");
        assertThat(ledger.problemDevicesOf(row)).isEmpty();
        assertThat(ledger.osVersionOf(row)).isNull();
    }
}
