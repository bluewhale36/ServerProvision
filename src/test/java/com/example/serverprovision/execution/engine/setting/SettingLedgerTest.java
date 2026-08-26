package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * E3-1 D-5 — 원장 한 행의 생애. 열 때 적은 목표는 관찰 덧쓰기와 종결을 지나도 <b>지워지지 않는다</b>
 * (E2-2 F-1: 닫힘이 meta 를 통째로 덮어 대조 기준이 사라졌던 결함의 재발 방지).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingLedgerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 25, 12, 0);
    private static final Map<String, Object> TARGET = Map.of("BootMode", "UEFI");

    @Mock ProvisioningHistoryRecorder recorder;

    private SettingLedger ledger;
    private final GuestServer server = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        ledger = new SettingLedger(recorder, new ObjectMapper());
        given(recorder.openRunning(any(), any(), any(), any())).willAnswer(inv -> ProvisioningHistory.openRunning(
                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
    }

    @Test
    @DisplayName("열기 — origin=setting 과 목표를 싣고, 재부팅 시각은 아직 없다")
    void open_carriesTargetOnly() {
        ProvisioningHistory row = ledger.open(server, new BiosSettingTarget(TARGET), T);

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(row.getStatusMeta()).contains("\"origin\":\"setting\"");
        assertThat(ledger.targetOf(row)).isEqualTo(TARGET);
        assertThat(ledger.rebootAtOf(row)).isNull();
    }

    @Test
    @DisplayName("닫기 — 사유 · 상세를 덧쓰되 목표 · rebootAt · pendingSeen 은 남는다")
    void close_preservesWhatWasKnownAtOpen() {
        ProvisioningHistory row = ledger.open(server, new BiosSettingTarget(TARGET), T);
        ledger.markPending(row, true);
        ledger.markRebooted(row, T.plusSeconds(30));

        ledger.close(row, ProvisioningStatus.FAILED, SettingLedger.READBACK_MISMATCH, "반영되지 않은 속성: BootMode",
                T.plusMinutes(10));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getFinishedAt()).isEqualTo(T.plusMinutes(10));
        assertThat(row.getStatusMeta())
                .contains("\"origin\":\"READBACK_MISMATCH\"")
                .contains("\"detail\":\"반영되지 않은 속성: BootMode\"")
                .contains("\"pendingSeen\":true");
        assertThat(ledger.targetOf(row)).isEqualTo(TARGET);
        assertThat(ledger.rebootAtOf(row)).isEqualTo(T.plusSeconds(30));
    }

    @Test
    @DisplayName("닫힌 행에는 관찰을 덧쓰지 못한다(사건 기록은 불변) — 두 번째 닫기도 무시된다")
    void closedRowIsImmutable() {
        ProvisioningHistory row = ledger.open(server, new BiosSettingTarget(TARGET), T);
        ledger.close(row, ProvisioningStatus.SUCCEEDED, SettingLedger.APPLIED, "1개 속성 반영 확인", T.plusMinutes(5));

        ledger.markRebooted(row, T.plusMinutes(6));
        ledger.markPending(row, false);
        ledger.close(row, ProvisioningStatus.FAILED, SettingLedger.RETURN_TIMEOUT, null, T.plusMinutes(7));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(row.getFinishedAt()).isEqualTo(T.plusMinutes(5));
        assertThat(ledger.rebootAtOf(row)).isNull();
        assertThat(row.getStatusMeta()).doesNotContain("pendingSeen");
    }

    @Test
    @DisplayName("meta 가 비어 있는 행 — 목표는 빈 맵, 재부팅 시각은 null(예외를 내지 않는다)")
    void blankMetaReadsAsEmpty() {
        ProvisioningHistory bare = ProvisioningHistory.openRunning(server, ProvisioningPhaseStep.BIOS_SETTING, T);

        assertThat(ledger.targetOf(bare)).isEmpty();
        assertThat(ledger.rebootAtOf(bare)).isNull();
    }

    @Test
    @DisplayName("failAtCursor — 단발 실패 기록 + 진행 실패 전환을 한 호출로")
    void failAtCursor_recordsAndMarksFailed() {
        ProvisioningProgress progress = ProvisioningProgress.builder()
                .id(UUID.randomUUID()).currentStep(ProvisioningPhaseStep.BIOS_SETTING).lastTransitionAt(T).build();
        progress.start(T);

        ledger.failAtCursor(server, progress, SettingLedger.BMC_REQUIRED, "BMC 없음", T.plusMinutes(1));

        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordInstant(eq(server), eq(ProvisioningPhaseStep.BIOS_SETTING), eq(ProvisioningStatus.FAILED),
                meta.capture(), eq(T.plusMinutes(1)));
        assertThat(meta.getValue()).contains("\"origin\":\"BMC_REQUIRED\"").contains("\"detail\":\"BMC 없음\"");
        assertThat(progress.isFailed()).isTrue();
    }

    // ---- E3-2 — BMC 축 행 --------------------------------------------------------

    @Test
    @DisplayName("BMC 열기 — axis=BMC · 빈 items 로 열리고, 항목 결과가 누적되며, 닫혀도 items 는 남는다")
    void bmcRowAccumulatesItemsAndKeepsThemOnClose() {
        ProvisioningHistory row = ledger.openBmc(server, T);
        assertThat(row.getStepCode()).isEqualTo(ProvisioningPhaseStep.BMC_SETTING);
        assertThat(row.getStatusMeta()).contains("\"axis\":\"BMC\"").contains("\"items\":{}");

        ledger.markItem(row, BmcSettingItem.DATE_TIME, BmcItemOutcome.applied());
        ledger.markItem(row, BmcSettingItem.FAN_PROFILE, BmcItemOutcome.skipped("NO_FAN_PROFILE"));
        ledger.markItem(row, BmcSettingItem.NETWORK_BOND, BmcItemOutcome.reconnectPending());
        ledger.markBondAt(row, T.plusSeconds(5));

        assertThat(ledger.itemsOf(row)).containsExactly(
                Map.entry("DATE_TIME", "APPLIED"), Map.entry("FAN_PROFILE", "SKIPPED:NO_FAN_PROFILE"),
                Map.entry("NETWORK_BOND", "RECONNECT_PENDING:Bond 적용 뒤 재접속 대기"));
        assertThat(ledger.bondAtOf(row)).isEqualTo(T.plusSeconds(5));
        assertThat(ledger.summaryOf(row)).isEqualTo("1개 적용 · FAN_PROFILE 건너뜀(NO_FAN_PROFILE)");

        ledger.close(row, ProvisioningStatus.SUCCEEDED, SettingLedger.APPLIED, ledger.summaryOf(row), T.plusMinutes(1));
        assertThat(ledger.itemsOf(row)).hasSize(3);
        assertThat(ledger.bondAtOf(row)).isEqualTo(T.plusSeconds(5));
        assertThat(row.getStatusMeta()).contains("\"origin\":\"APPLIED\"");
    }
}
