package com.example.serverprovision.execution.pxeinfra.apply;

import com.example.serverprovision.execution.pxeinfra.exception.DhcpConfigInvalidException;
import com.example.serverprovision.execution.pxeinfra.exception.DhcpConfigRestoreFailedException;
import com.example.serverprovision.execution.pxeinfra.exception.DhcpServiceControlFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1-I-3-c — 적용 귀결 판정 SSOT 검증. 각 {@link ApplyResult} 상수가 "적용 안 됨을 어떤 예외로 승격할지" 를
 * 스스로 아는 method-per-constant 계약을 못 박는다(switch 없이 상수가 예외를 소유). {@link ApplyOutcome} 의 위임도
 * 함께 확인한다.
 */
class ApplyResultTest {

    @Test
    @DisplayName("APPLIED — throwIfNotApplied 는 던지지 않는다")
    void applied_noThrow() {
        assertThatCode(() -> ApplyResult.APPLIED.throwIfNotApplied(ApplyOutcome.applied(7L)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("REJECTED — 게이트 원문을 담은 DhcpConfigInvalidException(400)")
    void rejected_throwsInvalid() {
        ApplyOutcome outcome = ApplyOutcome.rejected("line 5: semicolon expected");

        assertThatThrownBy(() -> ApplyResult.REJECTED.throwIfNotApplied(outcome))
                .isInstanceOf(DhcpConfigInvalidException.class)
                .hasMessageContaining("line 5: semicolon expected");
    }

    @Test
    @DisplayName("ROLLED_BACK — detail 을 담은 DhcpServiceControlFailedException(500)")
    void rolledBack_throwsServiceControl() {
        ApplyOutcome outcome = ApplyOutcome.rolledBack("재기동 실패, 이전 구성 복원");

        assertThatThrownBy(() -> ApplyResult.ROLLED_BACK.throwIfNotApplied(outcome))
                .isInstanceOf(DhcpServiceControlFailedException.class)
                .hasMessage("재기동 실패, 이전 구성 복원");
    }

    @Test
    @DisplayName("RESTORE_FAILED — 수동 복구 안내를 담은 DhcpConfigRestoreFailedException(500)")
    void restoreFailed_throwsRestoreFailed() {
        ApplyOutcome outcome = ApplyOutcome.restoreFailed("수동 복구 필요");

        assertThatThrownBy(() -> ApplyResult.RESTORE_FAILED.throwIfNotApplied(outcome))
                .isInstanceOf(DhcpConfigRestoreFailedException.class)
                .hasMessage("수동 복구 필요");
    }

    @Test
    @DisplayName("ApplyOutcome.throwIfNotApplied — result 상수에 위임")
    void outcome_delegatesToResult() {
        assertThat(ApplyOutcome.applied(1L).result()).isEqualTo(ApplyResult.APPLIED);
        assertThatCode(() -> ApplyOutcome.applied(1L).throwIfNotApplied()).doesNotThrowAnyException();
        assertThatThrownBy(() -> ApplyOutcome.rejected("x").throwIfNotApplied())
                .isInstanceOf(DhcpConfigInvalidException.class);
    }
}
