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

	/**
	 * scanner 등록을 ResourceType 으로 색인 — 본 클래스의 단일 lookup 진입점.
	 */
	private Map<ResourceType, MarkableScanner> scannerByType() {
		return scanners.stream().collect(Collectors.toMap(MarkableScanner::supportedType, s -> s));
	}

	@Override
	// S6-2-3 — 클래스 javadoc 이 명시한 REQUIRES_NEW 가 실제 어노테이션에 빠져 있었다. 종전 호출부
	// (컨트롤러/cron)는 트랜잭션 밖이라 드러나지 않았으나, TrashLostClearResolution 이 @Transactional 인
	// apply() 내부에서 처음 호출하면서 — 실패 시 FAILED 감사 로그가 호출자 롤백에 휩쓸려 소실되는
	// 결함으로 실체화 (적대적 검증 발견). 분리 트랜잭션으로 감사 기록의 생존을 보장한다.
	@Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
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
				// S6-2-3 — 도메인 purge 는 원위치 부산물만 정리한다(soft-delete 가 원위치 경로를 보존하는
				// 설계라 실물은 휴지통에 있음). 여기서 휴지통 실물을 함께 정리하지 않으면 영구삭제가
				// 끝나도 파일이 휴지통(점검 수색 제외 구역)에 영원히 남는다 — 세 진입경로가 모두
				// 지나는 단일 지점이라 이 한 곳 보강으로 전 도메인·전 경로에 적용된다.
				if (snapshot instanceof com.example.serverprovision.global.entity.LifecycleEntity lifecycle
						&& lifecycle.getTrashedPath() != null) {
					deleteQuietly(lifecycle.getTrashedPath());
				}
				Instant purgedAt = Instant.now();
				PurgeLogDetails.Success details = new PurgeLogDetails.Success(
						request.triggeredBy(),
						toStringOrNull(snapshot.getResourcePath()),
						toStringOrNull(snapshot.getResourcePath()),
						snapshot.getManifestHash(),
						snapshot.getMarkerSignature(),
						tickAttempt
				);
				PurgeLog saved = purgeLogRepository.save(
						PurgeLog.success(request, displayName, occurredAt, purgedAt, details));
				log.info(
						"[purge-executor] SUCCESS type={} id={} origin={} attempt={} logId={}",
						request.resourceType(), request.resourceId(), request.origin(), tickAttempt, saved.getId()
				);
				return new PurgeResult.Success(request, saved.getId());
			} catch (Exception ex) {
				lastError = ex;
				log.warn(
						"[purge-executor] attempt {}/{} failed. type={} id={} cause={}",
						tickAttempt, maxAttempts,
						request.resourceType(), request.resourceId(), ex.toString()
				);
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
				null
		);
		PurgeLog saved = purgeLogRepository.save(
				PurgeLog.failure(request, displayName, occurredAt, details));
		log.warn(
				"[purge-executor] FAILED type={} id={} origin={} attempts={} logId={} cause={}",
				request.resourceType(), request.resourceId(), request.origin(),
				maxAttempts, saved.getId(), lastError.toString()
		);
		return new PurgeResult.Failed(request, saved.getId(), wrapIfNeeded(lastError));
	}

	/**
	 * snapshot 미발견 시 1행 FAILED row 만 남기고 종료. retry 안 함 — 자원이 없으므로.
	 */
	private PurgeResult persistMissingSnapshot(PurgeRequest request) {
		PurgeLogDetails.Failed details = new PurgeLogDetails.Failed(
				request.triggeredBy(),
				1, 0,
				"MarkableNotFound",
				"휴지통에 자원이 없어 hard-delete 진입 자체를 거절했어요. (이미 삭제되었거나 ghost 상태)",
				null
		);
		PurgeLog saved = purgeLogRepository.save(
				PurgeLog.failure(
						request,
						request.resourceType().name() + " #" + request.resourceId(),
						Instant.now(), details
				));
		log.warn(
				"[purge-executor] snapshot not found — type={} id={} logId={}",
				request.resourceType(), request.resourceId(), saved.getId()
		);
		return new PurgeResult.Failed(
				request, saved.getId(),
				new PurgeIoFailedException("자원이 휴지통에 없습니다. id=" + request.resourceId())
		);
	}

	/**
	 * S6-2-3 — 휴지통 실물 정리는 best-effort. 실패해도 purge 자체(기록 삭제)는 성공으로 유지하고 로그만 남긴다.
	 */
	private static void deleteQuietly(String rawPath) {
		try {
			java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(rawPath));
		} catch (java.io.IOException | java.nio.file.InvalidPathException e) {
			// Path 해석 실패까지 함께 흡수 — 이미 성공한 purge 를 실물 정리 문제로 실패 처리하지 않는다.
			log.warn("[purge-executor] 휴지통 실물 정리 실패. path={}, msg={}", rawPath, e.getMessage());
		}
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
