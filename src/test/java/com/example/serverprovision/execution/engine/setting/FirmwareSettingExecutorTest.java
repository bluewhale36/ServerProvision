package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3-1 — 펌웨어 설정 phase 실행기. 빈 등록만으로 dispatch 의 HOLD 행이 위임으로 바뀌고(DEC-6), 게스트에게는
 * 기다리라는 스크립트만 준다 — 실제 일은 워커가 BMC 로 한다.
 */
class FirmwareSettingExecutorTest {

    private final FirmwareSettingExecutor executor = new FirmwareSettingExecutor();
    private final GuestServer server = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();

    @Test
    @DisplayName("phase 판별자 — FIRMWARE_SETTING")
    void phase_isFirmwareSetting() {
        assertThat(executor.phase()).isEqualTo(ProvisioningPhase.FIRMWARE_SETTING);
    }

    @Test
    @DisplayName("bootScript — 설정 적용 대기 + 원본 쿼리로 재진입(chain)")
    void bootScript_awaitsBiosSetting() {
        assertThat(executor.bootScript(server, started(), "systemUUID=abc"))
                .contains("applying bios settings via bmc")
                .contains("chain /api/pxe/v1/boot?systemUUID=abc");
    }

    @Test
    @DisplayName("readiness — default 준비됨(목표 유무 · 보드 일치는 워커의 행 판정이 가린다)")
    void readiness_isReadyByDefault() {
        assertThat(executor.readiness(server, started()).grade()).isEqualTo(ReadinessGrade.READY);
    }

    private static ProvisioningProgress started() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.BIOS_SETTING)
                .lastTransitionAt(LocalDateTime.of(2026, 8, 25, 12, 0))
                .build();
        p.start(LocalDateTime.of(2026, 8, 25, 12, 0));
        return p;
    }
}
