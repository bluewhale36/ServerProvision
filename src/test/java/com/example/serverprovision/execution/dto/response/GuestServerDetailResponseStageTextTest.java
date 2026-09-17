package com.example.serverprovision.execution.dto.response;

import com.example.serverprovision.execution.engine.firmware.AxisFlashState;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GuestServerDetailResponse#currentStageText()} · {@link GuestServerDetailResponse#failedStepNote()} — 스테퍼의
 * 국면 한 줄과 실패 사유는 화면이 조립하지 않고 응답이 만든다(S21-1). 개요와 단계 패널이 같은 말을 쓰게 하는 자리다.
 */
class GuestServerDetailResponseStageTextTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 13, 10, 0, 0);

    private static GuestServerDetailResponse detail(GuestServerDetailResponse.FirmwareFlash flash,
                                                    GuestServerDetailResponse.FirmwareSetting setting,
                                                    GuestServerDetailResponse.WindowsInstall windows,
                                                    List<GuestServerDetailResponse.Step> steps) {
        return new GuestServerDetailResponse(
                UUID.randomUUID(), "web-01", null, null, UUID.randomUUID(), "aabbcc", null, null,
                GuestServerStatus.PROVISIONING, null, T0, T0,
                null, null, List.of(),
                new GuestServerDetailResponse.Progress(ProvisioningPhase.FIRMWARE_UPDATING, T0, T0,
                        null, null, null, false, false, false, false, true),
                null, flash, setting, windows, null, List.of(), steps);
    }

    private static GuestServerDetailResponse.WindowsInstall windows(LocalDateTime servedAt, Long remaining, LocalDateTime completedAt) {
        return new GuestServerDetailResponse.WindowsInstall("WS2025", "Windows Server 2025", ReadinessGrade.READY, List.of(),
                servedAt, 2, 5, remaining, null, false, 0, completedAt, null, null, 0, 0, List.of(), false, null,
                null, null, null,
                null, List.of(), List.of(), List.of());   // R15-2 — driverSummary · driverEntries · driversSkipped · driverInstalls
    }

    @Test
    @DisplayName("굽는 중 — 도는 축 + 구간 문구 + 잔여 분을 한 줄로")
    void flashing_runningAxisWithStage() {
        var flash = new GuestServerDetailResponse.FirmwareFlash(true, List.of(
                new GuestServerDetailResponse.AxisFlash("BIOS", AxisFlashState.SUCCEEDED, "F29", null),
                new GuestServerDetailResponse.AxisFlash("BMC", AxisFlashState.RUNNING, "13.06.26", null)),
                "전원 투입 — 게스트 복귀 대기", 12L, null, true);

        assertThat(detail(flash, null, null, List.of()).currentStageText())
                .isEqualTo("BMC 굽는 중 · 전원 투입 — 게스트 복귀 대기 (잔여 12분)");
    }

    @Test
    @DisplayName("굽기가 끝난 뒤(running=false)에는 굽기 문구를 내지 않는다 — 다음 단계의 것으로 넘어간다")
    void flashDone_fallsThroughToSetting() {
        var flash = new GuestServerDetailResponse.FirmwareFlash(false, List.of(
                new GuestServerDetailResponse.AxisFlash("BIOS", AxisFlashState.SUCCEEDED, "F29", null)), null, null, null, false);
        var setting = new GuestServerDetailResponse.FirmwareSetting(List.of(
                new GuestServerDetailResponse.AxisSetting("BIOS", AxisFlashState.SUCCEEDED, null, null, null, null),
                new GuestServerDetailResponse.AxisSetting("BMC", AxisFlashState.RUNNING, "재부팅 — 게스트 복귀 대기", 3L, "2/4 적용", null)),
                null);

        assertThat(detail(flash, setting, null, List.of()).currentStageText())
                .isEqualTo("BMC 적용 중 · 2/4 적용 · 재부팅 — 게스트 복귀 대기 (잔여 3분)");
    }

    @Test
    @DisplayName("Windows 설치 중 — 서빙된 행만 '설치 중', 완료 뒤에는 null")
    void windows_servedOnly() {
        assertThat(detail(null, null, windows(T0, 58L, null), List.of()).currentStageText())
                .isEqualTo("설치 중 · 재진입 2/5 · 잔여 58분");
        assertThat(detail(null, null, windows(T0, null, T0.plusHours(1)), List.of()).currentStageText()).isNull();
    }

    @Test
    @DisplayName("도는 것이 없으면 null — 화면은 열린 원장 행으로 폴백한다")
    void nothingRunning_null() {
        assertThat(detail(null, null, null, List.of()).currentStageText()).isNull();
    }

    @Test
    @DisplayName("failedStepNote — 가장 최근 실패 행의 사유, 없으면 null")
    void failedStepNote_latestFailedRow() {
        List<GuestServerDetailResponse.Step> steps = List.of(
                new GuestServerDetailResponse.Step(ProvisioningPhase.RAID_CONFIGURATION, ProvisioningPhaseStep.RAID_APPLYING,
                        ProvisioningStatus.FAILED, T0, T0.plusMinutes(1), "지정 카드 불일치"),
                new GuestServerDetailResponse.Step(ProvisioningPhase.RAID_CONFIGURATION, ProvisioningPhaseStep.RAID_APPLYING,
                        ProvisioningStatus.FAILED, T0.plusMinutes(5), T0.plusMinutes(6), "재시도 뒤 다시 불일치"));

        assertThat(detail(null, null, null, steps).failedStepNote()).isEqualTo("재시도 뒤 다시 불일치");
        assertThat(detail(null, null, null, List.of()).failedStepNote()).isNull();
    }
}
