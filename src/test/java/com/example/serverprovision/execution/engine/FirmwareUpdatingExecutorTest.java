package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import org.junit.jupiter.api.DisplayName;
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
                        AxisResolution.selected(2L, "13.06.26"))));

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
                new FirmwareResolution(AxisResolution.selected(1L, "F27"),
                        AxisResolution.of(FirmwareAxisReason.NO_CANDIDATE))));

        String script = executor.bootScript(server, null, "systemUUID=abc");

        assertThat(script)
                .contains("firmware plan: BIOS=F27 BMC=NO_CANDIDATE")
                .contains("awaiting flash engine")
                .contains("chain /api/pxe/v1/boot?systemUUID=abc");
    }

    @Test
    @DisplayName("PhaseReadiness.summary — 사유가 여럿이면 이어 붙이고, 준비됨은 등급 라벨")
    void summary_joinsNotes() {
        assertThat(PhaseReadiness.ready().summary()).isEqualTo(ReadinessGrade.READY.getDescription());
        assertThat(PhaseReadiness.of(ReadinessGrade.DEGRADED, List.of("a", "b"), "w").summary())
                .isEqualTo("a / b");
    }
}
