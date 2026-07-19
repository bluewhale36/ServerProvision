package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1-0a CP4 — 전이·신호 invariant. 이 메서드들이 커서 SSOT 의 유일한 변경 통로이므로,
 * 역행 금지 · 실패↔종단 상호배타 · 개시 SSOT 판정을 여기서 고정한다.
 */
class ProvisioningProgressTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 7, 12, 12, 0);
    private static final LocalDateTime T1 = T0.plusMinutes(1);

    private ProvisioningProgress seed() {
        return ProvisioningProgress.builder()
                .currentPhase(ProvisioningPhase.BOOTSTRAPPING)
                .lastTransitionAt(T0)
                .build();
    }

    private ProvisioningProgress started() {
        ProvisioningProgress p = seed();
        p.start(T0);
        return p;
    }

    // ==== start / isStartableWith (DEC-26) ====================================

    @Test
    @DisplayName("start — startedAt + lastTransitionAt 기록")
    void start_records() {
        ProvisioningProgress p = seed();
        p.start(T1);
        assertThat(p.getStartedAt()).isEqualTo(T1);
        assertThat(p.getLastTransitionAt()).isEqualTo(T1);
        assertThat(p.isStarted()).isTrue();
    }

    @Test
    @DisplayName("start — 재개시는 invariant 거부 (서비스 가드를 뚫은 프로그램 버그의 표식)")
    void start_twice_rejected() {
        ProvisioningProgress p = seed();
        p.start(T1);
        assertThatThrownBy(() -> p.start(T1.plusMinutes(1))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("isStartableWith — 미개시+미회수만 true (뷰 노출 · 서버 가드 공유 SSOT)")
    void startable_truthTable() {
        ProvisioningProgress p = seed();
        assertThat(p.isStartableWith(null)).isTrue();
        assertThat(p.isStartableWith(T0)).isFalse();          // 회수됨
        p.start(T1);
        assertThat(p.isStartableWith(null)).isFalse();        // 이미 개시
    }

    // ==== advanceTo (DEC-2 · DEC-26) ===========================================

    @Test
    @DisplayName("advanceTo — 전진 전이 + lastTransitionAt 갱신")
    void advance_forward() {
        ProvisioningProgress p = started();
        p.advanceTo(ProvisioningPhase.DIAGNOSE_LINUX, T1);
        assertThat(p.getCurrentPhase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);
        assertThat(p.getLastTransitionAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("advanceTo — 개시 전 전이 거부 (DEC-26 게이트: '미개시 + 커서 진행' 은 표현 불가 상태)")
    void advance_beforeStart_rejected() {
        ProvisioningProgress p = seed();
        assertThatThrownBy(() -> p.advanceTo(ProvisioningPhase.DIAGNOSE_LINUX, T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("advanceTo — 역행·동일 phase 거부 (역행 금지 invariant)")
    void advance_backward_rejected() {
        ProvisioningProgress p = started();
        p.advanceTo(ProvisioningPhase.OS_INSTALLING, T1);
        assertThatThrownBy(() -> p.advanceTo(ProvisioningPhase.DIAGNOSE_LINUX, T1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> p.advanceTo(ProvisioningPhase.OS_INSTALLING, T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("advanceTo — 실패·종단 후 전이 거부")
    void advance_afterTerminalSignal_rejected() {
        ProvisioningProgress failed = started();
        failed.markFailed(ProvisioningPhaseStep.INFORMATION_COLLECTING, T1);
        assertThatThrownBy(() -> failed.advanceTo(ProvisioningPhase.DIAGNOSE_LINUX, T1))
                .isInstanceOf(IllegalStateException.class);

        ProvisioningProgress completed = started();
        completed.markCompleted(T1);
        assertThatThrownBy(() -> completed.advanceTo(ProvisioningPhase.DIAGNOSE_LINUX, T1))
                .isInstanceOf(IllegalStateException.class);
    }

    // ==== markFailed ↔ markCompleted 상호배타 (DEC-4·25) ========================

    @Test
    @DisplayName("markFailed — 시각+실패 step 기록, 종단 상태에서는 거부")
    void markFailed_records_andMutuallyExclusive() {
        ProvisioningProgress p = seed();
        p.markFailed(ProvisioningPhaseStep.BIOS_UPDATING, T1);
        assertThat(p.getFailedAt()).isEqualTo(T1);
        assertThat(p.getFailedStepCode()).isEqualTo(ProvisioningPhaseStep.BIOS_UPDATING);

        ProvisioningProgress completed = seed();
        completed.markCompleted(T1);
        assertThatThrownBy(() -> completed.markFailed(ProvisioningPhaseStep.BIOS_UPDATING, T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markFailed — 중복 실패 기록 거부 (해제는 운영자 재시도 액션 소관, E1-2)")
    void markFailed_twice_rejected() {
        ProvisioningProgress p = seed();
        p.markFailed(ProvisioningPhaseStep.BIOS_UPDATING, T1);
        assertThatThrownBy(() -> p.markFailed(ProvisioningPhaseStep.BMC_UPDATING, T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markCompleted — 시각 기록, 실패 상태·중복 종단은 거부")
    void markCompleted_records_andMutuallyExclusive() {
        ProvisioningProgress p = seed();
        p.markCompleted(T1);
        assertThat(p.getCompletedAt()).isEqualTo(T1);
        assertThatThrownBy(() -> p.markCompleted(T1)).isInstanceOf(IllegalStateException.class);

        ProvisioningProgress failed = seed();
        failed.markFailed(ProvisioningPhaseStep.OS_INSTALLING, T1);
        assertThatThrownBy(() -> failed.markCompleted(T1)).isInstanceOf(IllegalStateException.class);
    }

    // ==== E1-2 — 수동 실패 · 재시도 · 차단 (DEC-4) ============================

    private ProvisioningProgress.ProvisioningProgressBuilder diag() {
        return ProvisioningProgress.builder()
                .currentPhase(ProvisioningPhase.DIAGNOSE_LINUX).lastTransitionAt(T0);
    }

    @Test
    @DisplayName("markFailedManually — failedStepCode=null 수동 표식 (plan Q6)")
    void markFailedManually_setsNullStep() {
        ProvisioningProgress p = diag().startedAt(T0).build();
        p.markFailedManually(T1);
        assertThat(p.isFailed()).isTrue();
        assertThat(p.getFailedStepCode()).isNull();
    }

    @Test
    @DisplayName("isManualFailable — 진행 중(개시·미회수·미실패·미종단)에서만 true (뷰·가드 SSOT)")
    void isManualFailable_matrix() {
        assertThat(diag().startedAt(T0).build().isManualFailable(null)).isTrue();
        assertThat(diag().build().isManualFailable(null)).isFalse();                    // 미개시
        assertThat(diag().startedAt(T0).build().isManualFailable(T0)).isFalse();        // 회수
        assertThat(diag().startedAt(T0).failedAt(T0).build().isManualFailable(null)).isFalse();
        assertThat(diag().startedAt(T0).completedAt(T0).build().isManualFailable(null)).isFalse();
    }

    @Test
    @DisplayName("clearFailed — 실패 해제(전진 가드의 유일한 명시 예외) · 미실패면 IllegalState")
    void clearFailed_resetsSignals() {
        ProvisioningProgress p = diag().startedAt(T0).failedAt(T0)
                .failedStepCode(ProvisioningPhaseStep.INFORMATION_COLLECTING).build();
        p.clearFailed(T1);
        assertThat(p.isFailed()).isFalse();
        assertThat(p.getFailedStepCode()).isNull();
        assertThatThrownBy(() -> p.clearFailed(T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("isRetryBlocked — 펌웨어 flash 실패(BIOS/BMC_UPDATING)만 차단 (벽돌 리스크 SSOT)")
    void retryBlocked_onlyFirmwareSteps() {
        assertThat(diag().startedAt(T0).failedAt(T0)
                .failedStepCode(ProvisioningPhaseStep.BIOS_UPDATING).build().isRetryBlocked()).isTrue();
        assertThat(diag().startedAt(T0).failedAt(T0)
                .failedStepCode(ProvisioningPhaseStep.BMC_UPDATING).build().isRetryBlocked()).isTrue();
        assertThat(diag().startedAt(T0).failedAt(T0)
                .failedStepCode(ProvisioningPhaseStep.INFORMATION_COLLECTING).build().isRetryBlocked()).isFalse();
        assertThat(diag().startedAt(T0).failedAt(T0).build().isRetryBlocked()).isFalse();   // 수동 전환(null)
    }
}
