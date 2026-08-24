package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2-2 D-1 — 축마다 달라지는 것을 상수가 자기 값으로 든다. 이 시험이 지키는 것은 <b>축이 늘어날 때
 * 손대는 자리가 상수 하나</b>라는 성질이다. 소비처가 축 이름으로 분기하기 시작하면 이 값들이
 * 흩어지고, 그때부터 새 축은 여러 곳을 고쳐야 지원된다.
 */
class FirmwareAxisTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 23, 12, 0);

    @Test
    @DisplayName("축마다 step · 컴포넌트 · 인벤토리 멤버가 짝지어 있다")
    void axisHoldsItsOwnValues() {
        assertThat(FirmwareAxis.BIOS.getStep()).isEqualTo(ProvisioningPhaseStep.BIOS_UPDATING);
        assertThat(FirmwareAxis.BIOS.getUpdateComponent()).isEqualTo("BIOS");
        assertThat(FirmwareAxis.BMC.getStep()).isEqualTo(ProvisioningPhaseStep.BMC_UPDATING);
        assertThat(FirmwareAxis.BMC.getInventoryMember()).isEqualTo("BMC");
    }

    @Test
    @DisplayName("판정 접근자 — 소비처가 bios · bmc 로 갈라지지 않게 축이 자기 몫을 꺼낸다")
    void axisPicksItsOwnResolution() {
        FirmwareResolution resolution = new FirmwareResolution(
                AxisResolution.selected(1L, "F29", "/opt/fw/bios.RBU"),
                AxisResolution.of(FirmwareAxisReason.NO_CANDIDATE));

        assertThat(FirmwareAxis.BIOS.resolutionOf(resolution).display()).isEqualTo("F29");
        assertThat(FirmwareAxis.BMC.resolutionOf(resolution).isSelected()).isFalse();
    }

    @Test
    @DisplayName("커서에서 축을 되찾는다 — 실패 지점 판독이 이 매핑을 쓴다")
    void axisFoundFromCursor() {
        assertThat(FirmwareAxis.of(ProvisioningPhaseStep.BMC_UPDATING)).isEqualTo(FirmwareAxis.BMC);
        assertThat(FirmwareAxis.of(ProvisioningPhaseStep.OS_INSTALLING)).isNull();
    }

    @Test
    @DisplayName("시한은 축마다 다르다 — 실측 소요가 네 배 가까이 벌어지기 때문이다")
    void timeoutDiffersByAxis() {
        FlashTimeoutPolicy policy = new FlashTimeoutPolicy(new MockEnvironment());

        assertThat(policy.limitFor(FirmwareAxis.BIOS)).isEqualTo(Duration.ofMinutes(15));
        assertThat(policy.limitFor(FirmwareAxis.BMC)).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    @DisplayName("설정이 있으면 축별로 덮는다 — 키를 축 이름으로 조립하므로 축이 늘어도 정책은 그대로다")
    void settingOverridesPerAxis() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("provision.execution.flash-timeout.bios", "5m");
        FlashTimeoutPolicy policy = new FlashTimeoutPolicy(env);

        assertThat(policy.limitFor(FirmwareAxis.BIOS)).isEqualTo(Duration.ofMinutes(5));
        assertThat(policy.limitFor(FirmwareAxis.BMC)).isEqualTo(Duration.ofMinutes(30));   // 덮지 않은 축은 기본값
    }

    @Test
    @DisplayName("Task 상태가 종결 여부 · 사유 · 문구를 든다 — 소비처가 다시 고르지 않는다")
    void taskStateHoldsItsOwnOutcome() {
        assertThat(FlashTaskState.COMPLETED.isTerminal()).isTrue();
        assertThat(FlashTaskState.COMPLETED.getTerminalStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(FlashTaskState.FAILED.getReasonCode()).isEqualTo(FlashLedger.FLASH_EXCEPTION);
        assertThat(FlashTaskState.RUNNING.isTerminal()).isFalse();
        // 응답 없음과 굽다 난 시한 초과는 사유 어휘가 다르다 — 운영자가 볼 곳이 다르기 때문이다.
        assertThat(FlashTaskState.UNREACHABLE.getReasonCode()).isEqualTo(FlashLedger.BMC_UNREACHABLE);
        assertThat(FlashTaskState.RUNNING.getReasonCode()).isEqualTo(FlashLedger.FLASH_TIMEOUT);
    }

    @Test
    @DisplayName("원장 메타 — 적은 것을 그대로 되읽는다(워커가 이 기록으로 상태를 복원한다)")
    void ledgerMetaRoundTrips() {
        String meta = com.example.serverprovision.execution.entity.ProvisioningHistory
                .flashTargetMeta("F29", 7L, "/redfish/v1/TaskService/Tasks/2");
        var row = com.example.serverprovision.execution.entity.ProvisioningHistory.openRunning(
                com.example.serverprovision.execution.entity.GuestServer.builder().build(),
                ProvisioningPhaseStep.BIOS_UPDATING, T, meta);

        assertThat(row.flashTargetVersion()).isEqualTo("F29");
        assertThat(row.flashTaskPath()).isEqualTo("/redfish/v1/TaskService/Tasks/2");
    }

    @Test
    @DisplayName("굽기 행을 닫아도 목표는 지워지지 않는다 — 반영 확인이 대조할 기준이다(CP5 F-1)")
    void closeFlashPreservesTarget() {
        var row = com.example.serverprovision.execution.entity.ProvisioningHistory.openRunning(
                com.example.serverprovision.execution.entity.GuestServer.builder().build(),
                ProvisioningPhaseStep.BIOS_UPDATING, T,
                com.example.serverprovision.execution.entity.ProvisioningHistory
                        .flashTargetMeta("R22", 13L, "/redfish/v1/TaskService/Tasks/2"));

        row.closeFlash(ProvisioningStatus.SUCCEEDED, FlashLedger.FLASH_COMPLETED, "전송 완료", T.plusMinutes(1));

        // 종결 사유와 목표가 함께 남는다 — 둘 다 사건 사실이라 한쪽이 다른 쪽을 덮으면 안 된다.
        assertThat(row.flashFailureReason()).isEqualTo(FlashLedger.FLASH_COMPLETED);
        assertThat(row.flashTargetVersion()).isEqualTo("R22");
        assertThat(row.flashTaskPath()).isEqualTo("/redfish/v1/TaskService/Tasks/2");
    }

    @Test
    @DisplayName("굽기 기록이 아닌 행은 종결 메타만 남는다 — 없는 목표를 지어내지 않는다")
    void closeFlashWithoutTargetKeepsOutcomeOnly() {
        var row = com.example.serverprovision.execution.entity.ProvisioningHistory.openRunning(
                com.example.serverprovision.execution.entity.GuestServer.builder().build(),
                ProvisioningPhaseStep.BIOS_UPDATING, T);

        row.closeFlash(ProvisioningStatus.FAILED, FlashLedger.FLASH_EXCEPTION, "실패", T.plusMinutes(1));

        assertThat(row.flashFailureReason()).isEqualTo(FlashLedger.FLASH_EXCEPTION);
        assertThat(row.flashTargetVersion()).isNull();
    }

    @Test
    @DisplayName("phase 수준 사유는 축의 결과가 아니다 — 화면이 성공한 축을 뒤집지 않게(CP5 F-2)")
    void phaseLevelReasonsAreNotAxisOutcomes() {
        assertThat(FlashLedger.isPhaseLevel(FlashLedger.RETURN_TIMEOUT)).isTrue();
        assertThat(FlashLedger.isPhaseLevel(FlashLedger.IDENTITY_MISMATCH)).isTrue();
        assertThat(FlashLedger.isPhaseLevel(FlashLedger.BMC_UNREACHABLE)).isTrue();
        // 굽다 난 일은 그 축의 결과가 맞다.
        assertThat(FlashLedger.isPhaseLevel(FlashLedger.FLASH_EXCEPTION)).isFalse();
        assertThat(FlashLedger.isPhaseLevel(FlashLedger.VERIFY_MISMATCH)).isFalse();
        assertThat(FlashLedger.isPhaseLevel(null)).isFalse();
    }

    @Test
    @DisplayName("화면 배지 — 매핑 지식은 화면 enum 자신이 든다")
    void axisFlashStateMapsFromStatus() {
        assertThat(AxisFlashState.of(ProvisioningStatus.RUNNING)).isEqualTo(AxisFlashState.RUNNING);
        assertThat(AxisFlashState.of(ProvisioningStatus.SKIPPED)).isEqualTo(AxisFlashState.SKIPPED);
        assertThat(AxisFlashState.of(null)).isEqualTo(AxisFlashState.PENDING);
    }
}
