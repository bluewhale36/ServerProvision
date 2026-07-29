package com.example.serverprovision.execution.pxeinfra.apply;

import com.example.serverprovision.execution.pxeinfra.exception.DhcpConfigInvalidException;
import com.example.serverprovision.execution.pxeinfra.exception.DhcpConfigRestoreFailedException;
import com.example.serverprovision.execution.pxeinfra.exception.DhcpServiceControlFailedException;

/**
 * dhcpd 조각 적용 파이프라인의 귀결 판정 SSOT. 각 상수가 "적용 안 됨을 어떤 예외로 알릴지" 를 스스로 안다
 * (method-per-constant) — switch/if-else 분기 없이 새 귀결이 추가돼도 상수 정의로 흡수된다.
 *
 * <p>적용 파이프라인은 예외를 던지지 않고 {@link ApplyOutcome} 으로 귀결하며, 트랜잭션 커밋(적용 결과 기록)
 * 이후에 {@link #throwIfNotApplied} 를 호출해 실패를 HTTP 응답으로 승격한다(트랜잭션 롤백 방지).</p>
 */
public enum ApplyResult {

    APPLIED {
        @Override
        public void throwIfNotApplied(ApplyOutcome outcome) {
            // 적용 성공 — 던지지 않음.
        }
    },
    REJECTED {
        @Override
        public void throwIfNotApplied(ApplyOutcome outcome) {
            throw new DhcpConfigInvalidException(outcome.gateOutput());
        }
    },
    ROLLED_BACK {
        @Override
        public void throwIfNotApplied(ApplyOutcome outcome) {
            throw new DhcpServiceControlFailedException(outcome.detail());
        }
    },
    RESTORE_FAILED {
        @Override
        public void throwIfNotApplied(ApplyOutcome outcome) {
            throw new DhcpConfigRestoreFailedException(outcome.detail());
        }
    };

    public abstract void throwIfNotApplied(ApplyOutcome outcome);
}
