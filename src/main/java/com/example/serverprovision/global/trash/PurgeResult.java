package com.example.serverprovision.global.trash;

/**
 * S5-2-4 — PurgeExecutor 의 실행 결과 sealed type.
 *
 * <p>switch 대신 패턴 매칭 또는 sealed permits 만으로 호출자 분기 가능. CP4 본체에서 구현.</p>
 */
public sealed interface PurgeResult permits PurgeResult.Success, PurgeResult.Failed {

    PurgeRequest request();

    /** 성공 — purge_log 의 SUCCESS 행 id 보유. */
    record Success(PurgeRequest request, Long logId) implements PurgeResult {}

    /** 실패 — purge_log 의 FAILED 행 id + 마지막 throwable. */
    record Failed(PurgeRequest request, Long logId, Throwable cause) implements PurgeResult {}
}
