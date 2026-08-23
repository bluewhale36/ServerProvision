package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E2-1-b — 진입 게이트가 집행하는 결손 사다리(토론 D1). 진입 결손은 대기, 시한이 지나면 실패,
 * 재료가 돌아오면 자동 재개, 착수한 게스트는 아예 판정 대상이 아니다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PhaseEntryGateTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 22, 12, 0);

    @Mock ProvisioningHistoryRecorder provisioningHistoryRecorder;
    @Mock ProvisioningPhaseExecutor executor;

    private final HoldTtlPolicy ttlPolicy = new HoldTtlPolicy(Duration.ofHours(48));

    private PhaseEntryGate gate(PhaseReadiness verdict) {
        given(executor.phase()).willReturn(ProvisioningPhase.FIRMWARE_UPDATING);
        given(executor.readiness(any(), any())).willReturn(verdict);
        return new PhaseEntryGate(new PhaseExecutorRegistry(List.of(executor)), provisioningHistoryRecorder, ttlPolicy);
    }

    private static PhaseReadiness blocked() {
        return PhaseReadiness.of(ReadinessGrade.BLOCKED, List.of("BIOS — 무결성 표식이 없습니다"), "BIOS=MARKER_MISSING");
    }

    private static GuestServer server() {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    }

    private static ProvisioningProgress firmwareProgress(ProvisioningMotion motion) {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.BIOS_UPDATING)
                .lastTransitionAt(T)
                .build();
        // motion 은 전이 메서드로만 바뀐다 — 시험도 같은 통로를 쓴다(빌더로 무효 상태를 만들지 않는다).
        p.start(T);   // 개시 = 부팅 대기 진입
        if (motion == ProvisioningMotion.HOLD) {
            p.holdForShortage(T);
        } else if (motion == ProvisioningMotion.STEP_RUNNING) {
            p.positionAt(ProvisioningPhaseStep.BIOS_UPDATING, T);
        }
        return p;
    }

    // ==== 대기 진입 · 재개 · 시한 =========================================

    @Test
    @DisplayName("BLOCKED + 부팅 대기 → 결손 대기 진입(HOLD), 실패 아님")
    void blocked_entersHold() {
        ProvisioningProgress progress = firmwareProgress(ProvisioningMotion.AWAITING_BOOT);

        PhaseReadiness readiness = gate(blocked()).evaluate(server(), progress, T.plusMinutes(1));

        assertThat(readiness.isBlocked()).isTrue();
        assertThat(progress.isHolding()).isTrue();
        assertThat(progress.isFailed()).isFalse();
        assertThat(progress.getLastTransitionAt()).isEqualTo(T.plusMinutes(1));   // 시한 기점
        verify(provisioningHistoryRecorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("BLOCKED + 이미 대기 + 시한 전 → 아무 것도 하지 않는다(다음 폴링에서 다시 본다)")
    void blocked_withinTtl_staysHolding() {
        ProvisioningProgress progress = firmwareProgress(ProvisioningMotion.HOLD);

        gate(blocked()).evaluate(server(), progress, T.plusHours(47));

        assertThat(progress.isHolding()).isTrue();
        assertThat(progress.isFailed()).isFalse();
        assertThat(progress.getLastTransitionAt()).isEqualTo(T);   // 기점 불변 — 시한이 뒤로 밀리지 않는다
    }

    @Test
    @DisplayName("BLOCKED + 시한 만료 → 실패 전환 + 사유 원장 행(사유는 파생 불가라 사건 시점 기록)")
    void blocked_expired_marksFailedWithLedgerRow() {
        ProvisioningProgress progress = firmwareProgress(ProvisioningMotion.HOLD);
        GuestServer server = server();

        gate(blocked()).evaluate(server, progress, T.plusHours(49));

        assertThat(progress.isFailed()).isTrue();
        assertThat(progress.getMotion()).isNull();                                  // 실행 창 밖(D4 불변식)
        assertThat(progress.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.BIOS_UPDATING);   // 커서 = 막힌 지점
        verify(provisioningHistoryRecorder).recordInstant(eq(server), eq(ProvisioningPhaseStep.BIOS_UPDATING),
                eq(ProvisioningStatus.FAILED),
                eq(ProvisioningHistory.holdTtlMeta("BIOS=MARKER_MISSING", Duration.ofHours(48))),
                eq(T.plusHours(49)));
    }

    @Test
    @DisplayName("재료 복구 + 대기 중 → 자동 재개(운영자 개입 0)")
    void resolved_whileHolding_resumes() {
        ProvisioningProgress progress = firmwareProgress(ProvisioningMotion.HOLD);

        gate(PhaseReadiness.ready()).evaluate(server(), progress, T.plusHours(1));

        assertThat(progress.isHolding()).isFalse();
        assertThat(progress.getMotion()).isEqualTo(ProvisioningMotion.AWAITING_BOOT);
    }

    @Test
    @DisplayName("DEGRADED 는 대기가 아니다 — 결손 축만 건너뛰고 진행한다")
    void degraded_doesNotHold() {
        ProvisioningProgress progress = firmwareProgress(ProvisioningMotion.AWAITING_BOOT);

        PhaseReadiness readiness = gate(PhaseReadiness.of(ReadinessGrade.DEGRADED,
                List.of("BMC — 사용할 수 있는 펌웨어가 없습니다"), "BMC=NO_CANDIDATE"))
                .evaluate(server(), progress, T.plusMinutes(1));

        assertThat(readiness.isBlocked()).isFalse();
        assertThat(progress.isHolding()).isFalse();
    }

    // ==== 비적용 조건 ====================================================

    @Test
    @DisplayName("작업 중(STEP_RUNNING)이면 판정 자체를 하지 않는다 — 착수 후 결손은 실패 소관(D1)")
    void stepRunning_isNotGated() {
        ProvisioningProgress progress = firmwareProgress(ProvisioningMotion.STEP_RUNNING);

        PhaseReadiness readiness = gate(blocked()).evaluate(server(), progress, T.plusMinutes(1));

        assertThat(readiness.grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(progress.isHolding()).isFalse();
        verify(executor, never()).readiness(any(), any());
    }

    @Test
    @DisplayName("실행 창 밖(미개시 · 실패 · 종단 · 회수)은 게이트 대상이 아니다 — dispatch 상위 행이 받는다")
    void outsideExecutionWindow_isNotGated() {
        PhaseEntryGate gate = gate(blocked());

        ProvisioningProgress notStarted = ProvisioningProgress.builder()
                .id(UUID.randomUUID()).currentStep(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING)
                .lastTransitionAt(T).build();
        assertThat(gate.evaluate(server(), notStarted, T).grade()).isEqualTo(ReadinessGrade.READY);

        ProvisioningProgress failed = firmwareProgress(ProvisioningMotion.AWAITING_BOOT);
        failed.markFailed(T);
        assertThat(gate.evaluate(server(), failed, T).grade()).isEqualTo(ReadinessGrade.READY);

        ProvisioningProgress completed = firmwareProgress(ProvisioningMotion.AWAITING_BOOT);
        completed.markCompleted(T);
        assertThat(gate.evaluate(server(), completed, T).grade()).isEqualTo(ReadinessGrade.READY);

        GuestServer decommissioned = GuestServer.builder().id(UUID.randomUUID())
                .systemUUID(UUID.randomUUID()).decommissionedAt(T).build();
        ProvisioningProgress running = firmwareProgress(ProvisioningMotion.AWAITING_BOOT);
        assertThat(gate.evaluate(decommissioned, running, T).grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(running.isHolding()).isFalse();
    }

    @Test
    @DisplayName("실행기 미등록 phase 는 판정할 재료가 없다 — 준비됨으로 두고 dispatch 가 HOLD 를 안내한다")
    void phaseWithoutExecutor_isReady() {
        PhaseEntryGate bare = new PhaseEntryGate(new PhaseExecutorRegistry(List.of()),
                provisioningHistoryRecorder, ttlPolicy);
        ProvisioningProgress progress = firmwareProgress(ProvisioningMotion.AWAITING_BOOT);

        assertThat(bare.evaluate(server(), progress, T).grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(progress.isHolding()).isFalse();
    }
}
