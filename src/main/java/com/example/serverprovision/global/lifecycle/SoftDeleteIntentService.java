package com.example.serverprovision.global.lifecycle;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.lifecycle.exception.SoftDeleteRequiresIntentException;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.service.PathReconciliationService;
import com.example.serverprovision.management.common.exception.PathCorrectionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

/**
 * MK3-2 (DCM3-2.1 ~ DCM3-2.16) — softDelete reject 정책의 공통 진입점 헬퍼.
 *
 * <p>4 도메인 service (OS/BIOS/BMC/Subprogram) 가 본 헬퍼를 통해 사전조건 검사 + saga + forcedClear 를
 * 공유한다. 4 service 의 코드 중복 차단 (CLAUDE.md §중복된 코드 불가침).</p>
 *
 * <p>도메인-specific 부분은 {@link Supplier} 콜백으로 위임 — 정상 softDelete 진입점은 호출자가 결정.</p>
 *
 * <h3>책임</h3>
 * <ul>
 *   <li>{@link #checkPrecondition} — softDelete 진입 전 사전조건 검증. flag false 면 통과.
 *       위반 시 DeleteIntent 발급 + {@link SoftDeleteRequiresIntentException} throw</li>
 *   <li>{@link #reconcileThenDelete} — saga "위치 정정 후 삭제". 자동 재시도 3회 exponential backoff</li>
 *   <li>{@link #forcedClear} — saga "강제 정리". MK3-1 의 applyGhostClear 재사용</li>
 * </ul>
 */
@Slf4j
@Service
public class SoftDeleteIntentService {

	private final ResourceExistenceChecker resourceExistenceChecker;
	private final DeleteIntentRegistry deleteIntentRegistry;
	private final PathReconciliationService pathReconciliationService;
	private final List<MarkableScanner> scanners;

	/**
	 * DCM3-2.10 — feature flag 게이팅. default false (도입 직후) → 운영 검증 후 true.
	 */
	@Value("${provision.softdelete.reject-on-missing:false}")
	private boolean rejectOnMissing;

	/**
	 * MK3-2 순환 참조 해소 — 본 cross-cutting helper 는 두 측 (reconciliation + scanners) 모두 들고 있는데,
	 * scanners (BoardBiosMarkableScanner / IsoMarkableScanner / ...) 가 도메인 service 를 의존하고,
	 * 도메인 service 가 다시 본 helper 를 의존하는 cycle 이 발생.
	 * <p>가장 작은 변경 지점인 본 생성자 단의 두 의존성에 {@link Lazy} 를 적용해 cycle 차단.
	 * 런타임 호출 시점은 사용자 modal 응답 직후라 lazy proxy 의 첫 호출 비용은 무시 가능.</p>
	 */
	public SoftDeleteIntentService(
			ResourceExistenceChecker resourceExistenceChecker,
			DeleteIntentRegistry deleteIntentRegistry,
			@Lazy PathReconciliationService pathReconciliationService,
			@Lazy List<MarkableScanner> scanners
	) {
		this.resourceExistenceChecker = resourceExistenceChecker;
		this.deleteIntentRegistry = deleteIntentRegistry;
		this.pathReconciliationService = pathReconciliationService;
		this.scanners = scanners;
	}

	/**
	 * DCM3-2.4 — saga 자동 재시도 정책.
	 */
	private static final int RETRY_MAX_ATTEMPTS = 3;
	private static final long RETRY_INITIAL_DELAY_MS = 1_000L;

	/**
	 * MK3-2 (DCM3-2.1) — softDelete 진입 시 사전조건 검사. 위반 시 reject + intent 발급.
	 *
	 * <p>flag {@code provision.softdelete.reject-on-missing=false} 일 때는 검사 자체를 건너뛰고 통과
	 * (기존 MK3 동작 유지 — 회귀 차단 + 즉시 롤백 경로).</p>
	 */
	public <T extends LifecycleEntity & Markable> void checkPrecondition(T entity) {
		if (!rejectOnMissing) {
			return;
		}
		if (resourceExistenceChecker.exists(entity.getResourcePath())) {
			return;
		}
		// DCM3-2.1 — 사전조건 위반. ghostCandidate 는 entity 의 lifecycle 이 이미 soft-deleted 상태인지로 판정.
		// softDelete 진입 시점이라 보통 false 지만 도입 시점의 잔존 ghost 에 reject 가 닿는 케이스도 가능.
		boolean ghostCandidate = entity.isDeleted()
				&& entity.getTrashedAt() == null
				&& entity.getTrashedPath() == null;
		DeleteIntent intent = deleteIntentRegistry.issue(
				entity.getResourceType(),
				entity.getResourceId(),
				entity.getResourcePath(),
				ghostCandidate
		);
		log.info(
				"[softdelete-reject] intent issued. token={} type={} id={} missingPath={}",
				intent.token().asString(), intent.resourceType(), intent.resourceId(), intent.missingPath()
		);
		throw new SoftDeleteRequiresIntentException(intent);
	}

	/**
	 * MK3-2 (DCM3-2.4) — saga "위치 정정 후 삭제". 자동 재시도 3회 exponential backoff.
	 *
	 * <p>흐름 :</p>
	 * <ol>
	 *   <li>{@code scanForResource(type, id)} 로 단일 자원 drift 분류</li>
	 *   <li>PATH_DRIFT 가 발견되지 않으면 {@link PathCorrectionFailedException} (422)</li>
	 *   <li>{@code persistAndForcedApply(drift)} 로 강제 적용 (auto-apply 전역 설정 우회)</li>
	 *   <li>호출자의 {@code normalSoftDelete} 콜백 실행</li>
	 * </ol>
	 *
	 * <p>1~4 단계 일시 실패 시 {@link RuntimeException} 을 받아 1s/2s/4s 간격으로 3회 재시도.
	 * 모두 실패하면 {@link PathCorrectionFailedException} 으로 fail-stop.</p>
	 */
	public void reconcileThenDelete(ResourceType type, Long resourceId, Runnable normalSoftDelete) {
		for (int attempt = 1; attempt <= RETRY_MAX_ATTEMPTS; attempt++) {
			try {
				List<Drift> drifts = pathReconciliationService.scanForResource(type, resourceId);
				Drift pathDrift = drifts.stream()
						.filter(d -> d.getKind() == com.example.serverprovision.global.marker.DriftKind.PATH_DRIFT)
						.findFirst()
						.orElse(null);
				if (pathDrift == null) {
					throw new PathCorrectionFailedException(
							"자원의 새 위치를 찾지 못했습니다 — " + type + "#" + resourceId
									+ " · 강제 정리를 사용하시거나 자원을 인벤토리 스캔 범위 내로 복원해주세요.");
				}
				pathReconciliationService.persistAndForcedApply(pathDrift);
				normalSoftDelete.run();
				log.info(
						"[softdelete-saga] reconcileThenDelete 성공. type={} id={} attempt={}/{}",
						type, resourceId, attempt, RETRY_MAX_ATTEMPTS
				);
				return;
			} catch (PathCorrectionFailedException e) {
				// PATH_DRIFT 미발견은 재시도 의미 없음 — 즉시 throw.
				throw e;
			} catch (RuntimeException e) {
				if (attempt == RETRY_MAX_ATTEMPTS) {
					log.error(
							"[softdelete-saga] reconcileThenDelete {}회 재시도 모두 실패. type={} id={}",
							RETRY_MAX_ATTEMPTS, type, resourceId, e
					);
					throw new PathCorrectionFailedException(
							"자동 재시도 " + RETRY_MAX_ATTEMPTS + "회 모두 실패했습니다 : " + e.getMessage()
									+ " · 강제 정리를 사용하시거나 잠시 후 다시 시도해주세요.");
				}
				long delay = RETRY_INITIAL_DELAY_MS * (1L << (attempt - 1));
				log.warn(
						"[softdelete-saga] reconcileThenDelete attempt {}/{} 실패. {}ms 후 재시도. msg={}",
						attempt, RETRY_MAX_ATTEMPTS, delay, e.getMessage()
				);
				try {
					Thread.sleep(delay);
				} catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw new PathCorrectionFailedException("재시도 대기 중 인터럽트 발생");
				}
			}
		}
	}

	/**
	 * MK3-2 (DCM3-2.5) — saga "강제 정리". MK3-1 의 applyGhostClear 와 분리 (lifecycle 검증 없이 정리).
	 * 4 scanner 의 {@link MarkableScanner#applyForcedClear} 신규 SPI 호출.
	 */
	public void forcedClear(ResourceType type, Long resourceId) {
		MarkableScanner scanner = scanners.stream()
				.filter(s -> s.supportedType() == type)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No MarkableScanner for type: " + type));
		scanner.applyForcedClear(resourceId);
		log.info("[softdelete-saga] forcedClear 완료. type={} id={}", type, resourceId);
	}
}
