package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1-0a → ES-2 — 전이 · 신호 invariant. 이 메서드들이 커서 SSOT 의 유일한 변경 통로이므로,
 * phase 축 역행 금지 · 같은 phase 안 재시작 관용(D-1) · 실패↔종단 상호배타 · motion 실행 창
 * 결합(D4)을 여기서 고정한다.
 */
class ProvisioningProgressTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 7, 12, 12, 0);
    private static final LocalDateTime T1 = T0.plusMinutes(1);

    private ProvisioningProgress seed() {
        // 등록 seed 계약(ES-2 D-1) — 커서는 다음 목표인 진단 진입 step, motion 은 미개시라 NULL.
        return ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING)
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
    @DisplayName("start — startedAt + lastTransitionAt 기록, motion=AWAITING_BOOT 진입(D4)")
    void start_records() {
        ProvisioningProgress p = seed();
        assertThat(p.getMotion()).isNull();                   // 실행 창 밖 NULL 불변식
        p.start(T1);
        assertThat(p.getStartedAt()).isEqualTo(T1);
        assertThat(p.getLastTransitionAt()).isEqualTo(T1);
        assertThat(p.isStarted()).isTrue();
        assertThat(p.getMotion()).isEqualTo(ProvisioningMotion.AWAITING_BOOT);
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

    // ==== positionAt — 같은 phase 안 커서 이동 (ES-2 D-1 ⓐ) ====================

    @Test
    @DisplayName("positionAt — 같은 phase 앞으로 이동 + motion=STEP_RUNNING")
    void position_forward_withinPhase() {
        ProvisioningProgress p = started();
        p.positionAt(ProvisioningPhaseStep.INFORMATION_COLLECTING, T1);
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);
        assertThat(p.getMotion()).isEqualTo(ProvisioningMotion.STEP_RUNNING);
        assertThat(p.getLastTransitionAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("positionAt — 같은 phase 뒤로 이동 허용 (재부팅 후 phase 첫 step 재수행 관용)")
    void position_backward_withinPhase_tolerated() {
        ProvisioningProgress p = started();
        p.positionAt(ProvisioningPhaseStep.INFORMATION_COLLECTING, T1);
        p.positionAt(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING, T1.plusMinutes(1));   // 재시작
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);
        assertThat(p.currentPhase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);    // phase 불변
    }

    @Test
    @DisplayName("positionAt — 같은 step 은 무이동과 동치(멱등)")
    void position_sameStep_idempotent() {
        ProvisioningProgress p = started();
        p.positionAt(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING, T1);
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);
        assertThat(p.getMotion()).isEqualTo(ProvisioningMotion.STEP_RUNNING);
    }

    @Test
    @DisplayName("positionAt — 다른 phase 의 step 은 거부 (커서 phase 는 open 보고로 못 바꾼다 — 내부 버그 안전망)")
    void position_phaseMismatch_rejected() {
        ProvisioningProgress p = started();
        assertThatThrownBy(() -> p.positionAt(ProvisioningPhaseStep.BIOS_UPDATING, T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("positionAt(R13) — 미개시 진단 창은 허용, 미개시 + 진단 밖 · 실패 · 종단은 거부")
    void position_outsideExecutionWindow_rejected() {
        // R13 — 등록 즉시 진단이 자동 진행되므로 미개시 진단 phase 안 커서 이동은 정상이다.
        ProvisioningProgress notStarted = seed();
        notStarted.positionAt(ProvisioningPhaseStep.INFORMATION_COLLECTING, T1);
        assertThat(notStarted.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);
        assertThat(notStarted.getMotion()).isEqualTo(ProvisioningMotion.STEP_RUNNING);

        // 미개시 커서가 진단 밖으로 나가는 것은 여전히 표현 불가(개시 게이트의 도메인쪽 절반).
        assertThatThrownBy(() -> seed().positionAt(ProvisioningPhaseStep.BIOS_UPDATING, T1))
                .isInstanceOf(IllegalStateException.class);

        ProvisioningProgress failed = started();
        failed.markFailed(T1);
        assertThatThrownBy(() -> failed.positionAt(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING, T1))
                .isInstanceOf(IllegalStateException.class);

        ProvisioningProgress completed = started();
        completed.markCompleted(T1);
        assertThatThrownBy(() -> completed.positionAt(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING, T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markCompleted(R13) — 개시 전 종단은 표현 불가 (수집 완주는 개시 대기로 유보)")
    void markCompleted_beforeStart_rejected() {
        ProvisioningProgress notStarted = seed();
        notStarted.positionAt(ProvisioningPhaseStep.INFORMATION_PERSISTING, T1);
        assertThatThrownBy(() -> notStarted.markCompleted(T1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("isStartableWith(R13) — 실패 상태는 개시 불가 (회복 경로는 재시도)")
    void startable_failed_rejected() {
        ProvisioningProgress notStartedFailed = seed();
        notStartedFailed.markFailed(T1);   // 미개시 진단 창의 게스트 FAILED 보고로 생기는 상태
        assertThat(notStartedFailed.isStartableWith(null)).isFalse();
    }

    @Test
    @DisplayName("isDisruptionBlocked — 펌웨어를 굽는 창에만 참: 진단 · 대기(HOLD) · 실패 · 미개시는 거짓")
    void disruptionBlocked_onlyDuringFirmwareFlash() {
        ProvisioningProgress flashing = started();
        flashing.advanceToEntry(ProvisioningPhaseStep.BIOS_UPDATING, T1);
        flashing.positionAt(ProvisioningPhaseStep.BIOS_UPDATING, T1);
        assertThat(flashing.isDisruptionBlocked()).isTrue();
        assertThat(flashing.isManualFailable(null)).isFalse();   // 굽는 중엔 수동 실패도 불가

        ProvisioningProgress holding = started();
        holding.advanceToEntry(ProvisioningPhaseStep.BIOS_UPDATING, T1);
        holding.holdForShortage(T1);   // 결손 대기는 굽기 전 — 차단 없음
        assertThat(holding.isDisruptionBlocked()).isFalse();

        assertThat(started().isDisruptionBlocked()).isFalse();   // 진단 커서
        assertThat(seed().isDisruptionBlocked()).isFalse();      // 미개시

        flashing.markFailed(T1);   // 실패로 닫히면 창이 끝난다
        assertThat(flashing.isDisruptionBlocked()).isFalse();
    }

    // ==== advanceToEntry — phase 경계 pre-position (ES-2 D-1 ⓑ) ================

    @Test
    @DisplayName("advanceToEntry — 다음 phase 진입 step 으로 전진 + motion=AWAITING_BOOT(재부팅 대기)")
    void advanceToEntry_forward() {
        ProvisioningProgress p = started();
        p.advanceToEntry(ProvisioningPhaseStep.BIOS_UPDATING, T1);
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.BIOS_UPDATING);
        assertThat(p.currentPhase()).isEqualTo(ProvisioningPhase.FIRMWARE_UPDATING);
        assertThat(p.getMotion()).isEqualTo(ProvisioningMotion.AWAITING_BOOT);
        assertThat(p.getLastTransitionAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("advanceToEntry — 같은 phase · 역행 phase 거부 (역행 금지는 phase 축 invariant)")
    void advanceToEntry_backwardOrSamePhase_rejected() {
        ProvisioningProgress p = started();
        p.advanceToEntry(ProvisioningPhaseStep.OS_INSTALLING, T1);
        assertThatThrownBy(() -> p.advanceToEntry(ProvisioningPhaseStep.BIOS_UPDATING, T1))
                .isInstanceOf(IllegalStateException.class);   // 역행
        assertThatThrownBy(() -> p.advanceToEntry(ProvisioningPhaseStep.OS_INSTALLING, T1))
                .isInstanceOf(IllegalStateException.class);   // 같은 phase
    }

    @Test
    @DisplayName("advanceToEntry — 개시 전 · 실패 · 종단 후 전이 거부")
    void advanceToEntry_outsideExecutionWindow_rejected() {
        ProvisioningProgress notStarted = seed();
        assertThatThrownBy(() -> notStarted.advanceToEntry(ProvisioningPhaseStep.BIOS_UPDATING, T1))
                .isInstanceOf(IllegalStateException.class);

        ProvisioningProgress failed = started();
        failed.markFailed(T1);
        assertThatThrownBy(() -> failed.advanceToEntry(ProvisioningPhaseStep.BIOS_UPDATING, T1))
                .isInstanceOf(IllegalStateException.class);

        ProvisioningProgress completed = started();
        completed.markCompleted(T1);
        assertThatThrownBy(() -> completed.advanceToEntry(ProvisioningPhaseStep.BIOS_UPDATING, T1))
                .isInstanceOf(IllegalStateException.class);
    }

    // ==== markFailed ↔ markCompleted 상호배타 (DEC-4·25) + motion NULL (D4) =====

    @Test
    @DisplayName("markFailed — 시각 기록 + motion=NULL(실행 창 밖), 종단 상태에서는 거부")
    void markFailed_records_andMutuallyExclusive() {
        ProvisioningProgress p = started();
        p.markFailed(T1);
        assertThat(p.getFailedAt()).isEqualTo(T1);
        assertThat(p.getMotion()).isNull();                   // D4 불변식 — 실패는 실행 창 밖
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);   // 커서 = 실패 지점

        ProvisioningProgress completed = started();
        completed.markCompleted(T1);
        assertThatThrownBy(() -> completed.markFailed(T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markFailed — 중복 실패 기록 거부 (해제는 운영자 재시도 액션 소관, E1-2)")
    void markFailed_twice_rejected() {
        ProvisioningProgress p = started();
        p.markFailed(T1);
        assertThatThrownBy(() -> p.markFailed(T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("markCompleted — 시각 기록 + motion=NULL, 실패 상태·중복 종단은 거부")
    void markCompleted_records_andMutuallyExclusive() {
        ProvisioningProgress p = started();
        p.markCompleted(T1);
        assertThat(p.getCompletedAt()).isEqualTo(T1);
        assertThat(p.getMotion()).isNull();
        assertThatThrownBy(() -> p.markCompleted(T1)).isInstanceOf(IllegalStateException.class);

        ProvisioningProgress failed = started();
        failed.markFailed(T1);
        assertThatThrownBy(() -> failed.markCompleted(T1)).isInstanceOf(IllegalStateException.class);
    }

    // ==== E1-2 — 수동 실패 · 재시도 · 차단 (DEC-4 → ES-2 D-5) ==================

    private ProvisioningProgress.ProvisioningProgressBuilder diag() {
        return ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.INFORMATION_COLLECTING).lastTransitionAt(T0);
    }

    @Test
    @DisplayName("markFailedManually — 실패 신호 + 커서 유지 (수동 표식은 원장 instant 행 소관, D-5)")
    void markFailedManually_keepsCursor() {
        ProvisioningProgress p = diag().startedAt(T0).build();
        p.markFailedManually(T1);
        assertThat(p.isFailed()).isTrue();
        assertThat(p.getMotion()).isNull();
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);
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
    @DisplayName("clearFailed — 실패 해제 + motion=AWAITING_BOOT(재시도 대기) · 미실패면 IllegalState")
    void clearFailed_resetsSignals() {
        ProvisioningProgress p = diag().startedAt(T0).failedAt(T0).build();
        p.clearFailed(T1);
        assertThat(p.isFailed()).isFalse();
        assertThat(p.getMotion()).isEqualTo(ProvisioningMotion.AWAITING_BOOT);
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.INFORMATION_COLLECTING);   // 커서 유지
        assertThatThrownBy(() -> p.clearFailed(T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("isFirmwarePhaseFailure — 실패 지점이 펌웨어 step 인가라는 사실만 답한다(차단 정책은 RetryPolicy 소관)")
    void firmwarePhaseFailure_isFactOnly() {
        assertThat(ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.BIOS_UPDATING).lastTransitionAt(T0)
                .startedAt(T0).failedAt(T0).build().isFirmwarePhaseFailure()).isTrue();
        assertThat(ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.BMC_UPDATING).lastTransitionAt(T0)
                .startedAt(T0).failedAt(T0).build().isFirmwarePhaseFailure()).isTrue();
        assertThat(diag().startedAt(T0).failedAt(T0).build().isFirmwarePhaseFailure()).isFalse();   // 비펌웨어 step
        // 실패가 아니면 지점이 펌웨어여도 사실이 성립하지 않는다.
        assertThat(ProvisioningProgress.builder()
                .currentStep(ProvisioningPhaseStep.BIOS_UPDATING).lastTransitionAt(T0)
                .startedAt(T0).build().isFirmwarePhaseFailure()).isFalse();
    }

    // ==== E2-1-b — 자원 결손 대기 전이 =========================================

    @Test
    @DisplayName("holdForShortage — 부팅 대기에서만 대기 진입 + 시한 기점(lastTransitionAt) 갱신")
    void hold_fromAwaitingBoot() {
        ProvisioningProgress p = started();
        p.holdForShortage(T1);
        assertThat(p.isHolding()).isTrue();
        assertThat(p.getMotion()).isEqualTo(ProvisioningMotion.HOLD);
        assertThat(p.getLastTransitionAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("holdForShortage — 작업 중(STEP_RUNNING)에서는 거부 : 착수 후 결손은 대기가 아니라 실패(D1)")
    void hold_fromStepRunning_rejected() {
        ProvisioningProgress p = started();
        p.positionAt(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING, T1);   // 작업 착수
        assertThatThrownBy(() -> p.holdForShortage(T1)).isInstanceOf(IllegalStateException.class);
        assertThat(p.getMotion()).isEqualTo(ProvisioningMotion.STEP_RUNNING);
    }

    @Test
    @DisplayName("resumeFromHold — 대기 해소 → 부팅 대기 복귀 / 대기 중이 아니면 거부")
    void resume_fromHold() {
        ProvisioningProgress p = started();
        p.holdForShortage(T1);
        p.resumeFromHold(T1.plusMinutes(1));
        assertThat(p.isHolding()).isFalse();
        assertThat(p.getMotion()).isEqualTo(ProvisioningMotion.AWAITING_BOOT);
        assertThatThrownBy(() -> p.resumeFromHold(T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("대기 중 실패 전환 — motion 은 NULL 로(실행 창 밖 불변식), 커서는 막힌 지점 유지")
    void hold_thenFail_clearsMotion() {
        ProvisioningProgress p = started();
        p.holdForShortage(T1);
        p.markFailed(T1.plusMinutes(2));
        assertThat(p.getMotion()).isNull();
        assertThat(p.isHolding()).isFalse();
        assertThat(p.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING);
    }

    // ==== currentPhase 파생 (ES-2 D3 — 정보 손실 없음) ==========================

    @Test
    @DisplayName("currentPhase — 커서 step 의 phase 파생 (소비처 판정 · 화면 공급의 단일 통로)")
    void currentPhase_derivation() {
        assertThat(seed().currentPhase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);
        ProvisioningProgress p = started();
        p.advanceToEntry(ProvisioningPhaseStep.RAID_INVENTORY_COLLECTING, T1);
        assertThat(p.currentPhase()).isEqualTo(ProvisioningPhase.RAID_CONFIGURATION);
    }
}
