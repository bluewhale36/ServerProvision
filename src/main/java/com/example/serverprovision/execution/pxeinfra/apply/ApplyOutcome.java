package com.example.serverprovision.execution.pxeinfra.apply;

/**
 * dhcpd 조각 적용 파이프라인의 귀결 값. 예외 대신 이 값으로 파이프라인이 종료되며, 트랜잭션 커밋 후
 * {@link #throwIfNotApplied()} 로 실패를 HTTP 응답으로 승격한다.
 *
 * @param result           귀결 판정
 * @param appliedVersionId 성공 시 archive 된 이전본 버전 id(최초 적용이면 null)
 * @param gateOutput       {@code dhcpd -t} 원문(REJECTED 일 때, 그 외 null)
 * @param detail           500 안내 문구(ROLLED_BACK/RESTORE_FAILED 일 때, 그 외 null)
 */
public record ApplyOutcome(
        ApplyResult result,
        Long appliedVersionId,
        String gateOutput,
        String detail
) {

    public static ApplyOutcome applied(Long appliedVersionId) {
        return new ApplyOutcome(ApplyResult.APPLIED, appliedVersionId, null, null);
    }

    public static ApplyOutcome rejected(String gateOutput) {
        return new ApplyOutcome(ApplyResult.REJECTED, null, gateOutput, null);
    }

    public static ApplyOutcome rolledBack(String detail) {
        return new ApplyOutcome(ApplyResult.ROLLED_BACK, null, null, detail);
    }

    public static ApplyOutcome restoreFailed(String detail) {
        return new ApplyOutcome(ApplyResult.RESTORE_FAILED, null, null, detail);
    }

    /** 적용 실패면 {@link ApplyResult} 별 예외를 던진다(APPLIED 면 no-op). */
    public void throwIfNotApplied() {
        result.throwIfNotApplied(this);
    }
}
