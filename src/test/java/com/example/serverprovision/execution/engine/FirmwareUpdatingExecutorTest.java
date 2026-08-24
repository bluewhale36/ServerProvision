package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.engine.firmware.AxisResolution;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxisReason;
import com.example.serverprovision.execution.engine.firmware.FirmwareResolution;
import com.example.serverprovision.execution.engine.firmware.FirmwareResolutionProvider;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdatingExecutor;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import org.junit.jupiter.api.DisplayName;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * E2-1-b — 펌웨어 phase 실행기. 준비도는 해석을 그대로 옮기고, 부팅 스크립트는 "해석은 끝났고 굽는
 * 엔진은 아직 없다" 를 명시한다(조용한 통과 금지).
 */
@ExtendWith(MockitoExtension.class)
class FirmwareUpdatingExecutorTest {

    @Mock FirmwareResolutionProvider firmwareResolutionProvider;
    @InjectMocks FirmwareUpdatingExecutor executor;

    private final GuestServer server = GuestServer.builder()
            .id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();

    @Test
    @DisplayName("phase 판별자 — 빈 등록만으로 dispatch 의 미구현 HOLD 행이 위임으로 바뀐다")
    void phase_isFirmwareUpdating() {
        assertThat(executor.phase()).isEqualTo(ProvisioningPhase.FIRMWARE_UPDATING);
    }

    @Test
    @DisplayName("readiness — 해석 결과를 그대로 준비도로(별도 검증 로직을 짓지 않는다)")
    void readiness_mirrorsResolution() {
        given(firmwareResolutionProvider.resolveFor(server.getId())).willReturn(Optional.of(
                new FirmwareResolution(
                        AxisResolution.of(FirmwareAxisReason.SIGNATURE_INVALID),
                        AxisResolution.selected(2L, "13.06.26", "/tmp/fw/13.06.26.img"))));

        PhaseReadiness readiness = executor.readiness(server, (ProvisioningProgress) null);

        assertThat(readiness.grade()).isEqualTo(ReadinessGrade.BLOCKED);
        assertThat(readiness.notes()).hasSize(1);
        assertThat(readiness.wire()).isEqualTo("BIOS=SIGNATURE_INVALID BMC=13.06.26");
    }

    @Test
    @DisplayName("readiness — 할당 · 펌웨어 단계가 없으면 판정 대상이 아니다(준비됨)")
    void readiness_noAssignment_isReady() {
        given(firmwareResolutionProvider.resolveFor(any())).willReturn(Optional.empty());

        assertThat(executor.readiness(server, null).grade()).isEqualTo(ReadinessGrade.READY);
    }

    @Test
    @DisplayName("bootScript — 해석 요약을 실은 집행 대기(게스트 콘솔은 ASCII 코드로 읽는다)")
    void bootScript_awaitsFlashEngineWithSummary() {
        given(firmwareResolutionProvider.resolveFor(any())).willReturn(Optional.of(
                new FirmwareResolution(AxisResolution.selected(1L, "F27", "/tmp/fw/F27.img"),
                        AxisResolution.of(FirmwareAxisReason.NO_CANDIDATE))));

        String script = executor.bootScript(server, awaitingBoot(), "systemUUID=abc");

        assertThat(script)
                .contains("firmware plan: BIOS=F27 BMC=NO_CANDIDATE")
                .contains("awaiting flash engine")
                .contains("chain /api/pxe/v1/boot?systemUUID=abc");
    }

    @Test
    @DisplayName("bootScript — 집행에 착수한 게스트가 돌아오면 반영 확인 대기(다시 굽게 하지 않는다)")
    void bootScript_whenFlashing_awaitsVerification() {
        String script = executor.bootScript(server, stepRunning(), "systemUUID=abc");

        assertThat(script)
                .contains("verifying inventory")
                .contains("chain /api/pxe/v1/boot?systemUUID=abc");
        // 집행 중에는 해석을 다시 돌리지 않는다 — 무엇을 구웠는지는 원장이 기억한다(E2-2 D-4).
        then(firmwareResolutionProvider).shouldHaveNoInteractions();
    }

    /** 착수 전 — 부팅 대기. 운동 양태는 전이 메서드로만 세운다(빌더로 무효 상태를 만들지 않는다). */
    private static ProvisioningProgress awaitingBoot() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.BIOS_UPDATING)
                .lastTransitionAt(java.time.LocalDateTime.of(2026, 8, 23, 10, 0))
                .build();
        p.start(java.time.LocalDateTime.of(2026, 8, 23, 10, 0));
        return p;
    }

    /** 집행 중 — 워커가 착수해 커서를 축 step 에 놓은 상태. */
    private static ProvisioningProgress stepRunning() {
        ProvisioningProgress p = awaitingBoot();
        p.positionAt(ProvisioningPhaseStep.BIOS_UPDATING, java.time.LocalDateTime.of(2026, 8, 23, 10, 1));
        return p;
    }

    @Test
    @DisplayName("PhaseReadiness.summary — 사유가 여럿이면 이어 붙이고, 준비됨은 등급 라벨")
    void summary_joinsNotes() {
        assertThat(PhaseReadiness.ready().summary()).isEqualTo(ReadinessGrade.READY.getDescription());
        assertThat(PhaseReadiness.of(ReadinessGrade.DEGRADED, List.of("a", "b"), "w").summary())
                .isEqualTo("a / b");
    }
}
