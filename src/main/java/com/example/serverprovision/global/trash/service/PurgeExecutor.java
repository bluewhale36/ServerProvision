package com.example.serverprovision.global.trash.service;

import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;

/**
 * S5-2-4 — hard-delete 의 3 진입경로 (USER_DIRECT / NUDGE_REPLACE / TTL_AUTO) 가 통과하는
 * 단일 진입점. retry / purge_log INSERT / NotificationDispatcher 호출이 본 클래스 안에 응집.
 *
 * <p>typed-name 검증은 본 단계 진입 전 (controller / NudgeService) 에서 통과 후 호출 — 본 인터페이스는
 * {@link PurgeRequest#typedName()} 을 검증하지 않고 감사용으로만 details 에 기록한다.</p>
 *
 * <p>CP2 — 시그니처만. 본체 (retry 알고리즘 + backoff + purge_log insert) 는 CP4.</p>
 */
public interface PurgeExecutor {

    /**
     * 단일 cron tick / 단일 사용자 호출 = purge_log 1행.
     *
     * <ul>
     *   <li>TTL_AUTO 만 cron tick 내 retry (1s · 2s · 4s exponential backoff)</li>
     *   <li>SUCCESS : purged_at NOT NULL + SuccessDetails JSON</li>
     *   <li>FAILED  : purged_at NULL + FailedDetails JSON (attemptNumber = countFailed + 1)</li>
     * </ul>
     *
     * @param request 진입경로별 메타 + 자원 식별자
     * @return Success(logId) 또는 Failed(logId, cause)
     */
    PurgeResult execute(PurgeRequest request);
}
