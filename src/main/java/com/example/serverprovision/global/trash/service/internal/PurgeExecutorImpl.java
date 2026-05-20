package com.example.serverprovision.global.trash.service.internal;

import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.trash.PurgeLogDetails;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.entity.PurgeLog;
import com.example.serverprovision.global.trash.enums.PurgeOutcome;
import com.example.serverprovision.global.trash.exception.PurgeIoFailedException;
import com.example.serverprovision.global.trash.repository.PurgeLogRepository;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.global.trash.service.TrashSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * S5-2-4 CP4 — PurgeExecutor 본체.
 *
 * <p>흐름:</p>
 * <ol>
 *   <li>scanner.findTrashedById 로 snapshot 확보 (displayName / manifestHash / paths)</li>
 *   <li>origin.retriesAllowed() 가 true 면 trash_settings.retryMaxAttempts 회 retry — backoff 1s / 2s / 4s</li>
 *   <li>성공 시 purge_log SUCCESS row INSERT (purged_at NOT NULL)</li>
 *   <li>실패 시 purge_log FAILED row INSERT (purged_at NULL, attemptNumber = past FAILED count + 1)</li>
 * </ol>
 *
 * <p><strong>트랜잭션</strong> : 단일 cron tick / 1 사용자 호출 = 1 purge_log row. 본 메서드 안에서
 * scanner.purgeFromTrash 호출 → 도메인 service 의 hard-delete 흐름 (cascade 포함) → 마지막에
 * log INSERT. propagation=REQUIRES_NEW 로 호출자 트랜잭션과 분리 — log 영속화가 호출자 rollback 에
 * 휩쓸리지 않도록.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurgeExecutorImpl implements PurgeExecutor {

    private final List<MarkableScanner> scanners;
    private final TrashSettingsService settingsService;
    private final PurgeLogRepository purgeLogRepository;

    /** scanner 등록을 ResourceType 으로 색인 — 본 클래스의 단일 lookup 진입점. */
    private Map<ResourceType, MarkableScanner> scannerByType() {
        return scanners.stream().collect(Collectors.toMap(MarkableScanner::supportedType, s -> s));
    }

    @Override
    @Transactional
    public PurgeResult execute(PurgeRequest request) {
        MarkableScanner scanner = scannerByType().get(request.resourceType());
        if (scanner == null) {
            throw new IllegalStateException("지원하지 않는 자원 종류 : " + request.resourceType());
        }
        Optional<Markable> snapshotOpt = scanner.findTrashedById(request.resourceId());
        // snapshot 미발견 = 휴지통에 없음. ghost 또는 이미 hard-delete 됨 — 실패 row 만 남기고 종료.
        if (snapshotOpt.isEmpty()) {
            return persistMissingSnapshot(request);
        }
        Markable snapshot = snapshotOpt.get();
        String displayName = snapshot.displayName();

        int maxAttempts = request.origin().retriesAllowed() ? settingsService.getRetryMaxAttempts() : 1;
        long backoffBaseMs = settingsService.getRetryBackoffBaseMs();

        Instant occurredAt = Instant.now();
        Exception lastError = null;
        for (int tickAttempt = 1; tickAttempt <= maxAttempts; tickAttempt++) {
            try {
                scanner.purgeFromTrash(request.resourceId());
                Instant purgedAt = Instant.now();
                PurgeLogDetails.Success details = new PurgeLogDetails.Success(
                        request.triggeredBy(),
                        toStringOrNull(snapshot.getResourcePath()),
                        toStringOrNull(snapshot.getResourcePath()),
                        snapshot.getManifestHash(),
                        snapshot.getMarkerSignature(),
                        tickAttempt);
                PurgeLog saved = purgeLogRepository.save(
                        PurgeLog.success(request, displayName, occurredAt, purgedAt, details));
                log.info("[purge-executor] SUCCESS type={} id={} origin={} attempt={} logId={}",
                        request.resourceType(), request.resourceId(), request.origin(), tickAttempt, saved.getId());
                return new PurgeResult.Success(request, saved.getId());
            } catch (Exception ex) {
                lastError = ex;
                log.warn("[purge-executor] attempt {}/{} failed. type={} id={} cause={}",
                        tickAttempt, maxAttempts,
                        request.resourceType(), request.resourceId(), ex.toString());
                if (tickAttempt < maxAttempts) {
                    sleepBackoff(tickAttempt, backoffBaseMs);
                }
            }
        }

        long historicalFailed = purgeLogRepository.countByResourceTypeAndResourceIdAndOutcome(
                request.resourceType(), request.resourceId(), PurgeOutcome.FAILED);
        PurgeLogDetails.Failed details = new PurgeLogDetails.Failed(
                request.triggeredBy(),
                (int) historicalFailed + 1,
                maxAttempts,
                lastError.getClass().getSimpleName(),
                truncate(lastError.getMessage(), 500),
                null);
        PurgeLog saved = purgeLogRepository.save(
                PurgeLog.failure(request, displayName, occurredAt, details));
        log.warn("[purge-executor] FAILED type={} id={} origin={} attempts={} logId={} cause={}",
                request.resourceType(), request.resourceId(), request.origin(),
                maxAttempts, saved.getId(), lastError.toString());
        return new PurgeResult.Failed(request, saved.getId(), wrapIfNeeded(lastError));
    }

    /** snapshot 미발견 시 1행 FAILED row 만 남기고 종료. retry 안 함 — 자원이 없으므로. */
    private PurgeResult persistMissingSnapshot(PurgeRequest request) {
        PurgeLogDetails.Failed details = new PurgeLogDetails.Failed(
                request.triggeredBy(),
                1, 0,
                "MarkableNotFound",
                "휴지통에 자원이 없어 hard-delete 진입 자체를 거절했어요. (이미 삭제되었거나 ghost 상태)",
                null);
        PurgeLog saved = purgeLogRepository.save(
                PurgeLog.failure(request,
                        request.resourceType().name() + " #" + request.resourceId(),
                        Instant.now(), details));
        log.warn("[purge-executor] snapshot not found — type={} id={} logId={}",
                request.resourceType(), request.resourceId(), saved.getId());
        return new PurgeResult.Failed(request, saved.getId(),
                new PurgeIoFailedException("자원이 휴지통에 없습니다. id=" + request.resourceId()));
    }

    private static void sleepBackoff(int tickAttempt, long backoffBaseMs) {
        long waitMs = backoffBaseMs * (1L << (tickAttempt - 1));
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String toStringOrNull(java.nio.file.Path p) {
        return p == null ? null : p.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static Throwable wrapIfNeeded(Exception ex) {
        return ex instanceof PurgeIoFailedException ? ex : new PurgeIoFailedException(ex.getMessage(), ex);
    }
}
