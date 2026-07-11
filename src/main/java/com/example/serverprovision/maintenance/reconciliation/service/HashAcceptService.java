package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.global.trash.service.TypedNameGuard;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * S6-3-4 — "내용 변경 수용" : 의도된 파일 교체를 사람이 선언하면 등록 지문을 현재 내용으로 갱신한다.
 * 변조 가능성을 정본으로 굳히는 가장 무거운 액션이라 다음의 안전장치를 겹친다:
 *
 * <ul>
 *   <li><b>자원명 입력 확인</b>({@link TypedNameGuard} — 영구삭제와 동급의 승인 의식) + [AUDIT] 감사 로그.</li>
 *   <li><b>감지 시점 지문({@code drift.observedHash})과 실행 시점 재계산 지문의 대조</b> — 그 사이
 *       파일이 또 바뀌었으면 실패 처리해, 사용자가 확인한 내용과 다른 것이 정본화되는 사고를 차단
 *       (Tripwire high-security 모드 등가 — 업계 조사 report 참조).</li>
 *   <li>대용량 지문 재계산(수 분)은 비동기 — 점검 잠금과 공유하지 않는다(최악은 옛 판정 1회 재보고,
 *       다음 점검에서 자연 소멸). 같은 카드의 중복 시작만 차단.</li>
 * </ul>
 */
@Slf4j
@Service
public class HashAcceptService {

	private final Map<ResourceType, MarkableScanner> scanners;
	private final ProvisionMarkerService markerService;
	private final BackgroundJobService backgroundJobService;
	private final DriftRepository driftRepository;
	private final PathReconciliationService reconciliationService;
	private final HashAcceptService self;
	// 자원 (종류, 번호) 단위 중복 차단 — driftId 단위면 같은 자원을 가리키는 구 보고서의 다른 카드로
	// 동시 수용이 뚫린다 (적대적 검증 발견).
	private final Set<ResourceKey> inFlight = ConcurrentHashMap.newKeySet();

	public HashAcceptService(
			List<MarkableScanner> scanners,
			ProvisionMarkerService markerService,
			BackgroundJobService backgroundJobService,
			DriftRepository driftRepository,
			PathReconciliationService reconciliationService,
			@Lazy HashAcceptService self
	) {
		this.scanners = scanners.stream()
				.collect(Collectors.toUnmodifiableMap(MarkableScanner::supportedType, s -> s));
		this.markerService = markerService;
		this.backgroundJobService = backgroundJobService;
		this.driftRepository = driftRepository;
		this.reconciliationService = reconciliationService;
		this.self = self;
	}

	/**
	 * 동기 검증부 — 통과 시 백그라운드 작업을 시작하고 jobId 를 반환. 거절은 전부 409 + 이유.
	 */
	public String triggerAccept(Long driftId, String typedName) {
		// 수용도 "시스템이 상태를 바꾸는 해결"이므로 전면 차단 마스터의 지배를 받는다 —
		// 관찰(동결) 모드에서 가장 파괴적인 정본화만 뚫리는 비일관 차단 (적대적 검증 발견, SSOT 공유).
		if (!reconciliationService.isResolutionEnabled()) {
			throw DriftResolutionNotAllowedException.globalOff();
		}
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new DriftNotFoundException(driftId));
		if (drift.getKind() != DriftKind.HASH_MISMATCH) {
			throw DriftResolutionNotAllowedException.notApplicable(drift.getKind());
		}
		if (drift.getObservedHash() == null) {
			// 스냅샷 도입 전에 만들어진 카드 — 대조 기준이 없어 진행 불가. 재점검으로 새 카드 유도.
			throw DriftResolutionNotAllowedException.staleState();
		}
		Markable resource = scannerFor(drift.getResourceType())
				.findActiveMarkableById(drift.getResourceId())
				.orElseThrow(DriftResolutionNotAllowedException::staleState);
		TypedNameGuard.verify(resource, typedName);
		ResourceKey key = new ResourceKey(drift.getResourceType(), drift.getResourceId());
		if (!inFlight.add(key)) {
			throw DriftResolutionNotAllowedException.acceptInProgress();
		}
		String jobId;
		try {
			jobId = backgroundJobService.register(
					JobType.HASH_ACCEPT,
					"내용 수용 — " + resource.displayName(),
					"지문 재계산 후 정본 갱신",
					BackgroundJobService.stagesOf(HashAcceptStage.values())
			);
		} catch (RuntimeException e) {
			inFlight.remove(key);
			throw e;
		}
		self.runAccept(jobId, driftId, key);
		return jobId;
	}

	@Async
	public void runAccept(String jobId, Long driftId, ResourceKey key) {
		try {
			backgroundJobService.startStage(jobId, HashAcceptStage.ACCEPTING);
			String resourceName = self.performAccept(driftId);
			backgroundJobService.complete(jobId, Map.of("acceptedResource", resourceName));
		} catch (RuntimeException e) {
			log.error("[hash-accept] 내용 수용 실패. driftId={}", driftId, e);
			backgroundJobService.fail(jobId, "수용 실패 : " + e.getMessage());
		} finally {
			inFlight.remove(key);
		}
	}

	@Transactional
	public String performAccept(Long driftId) {
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new IllegalStateException("카드가 이미 정리되었습니다 — 재점검 후 확인하세요."));
		MarkableScanner scanner = scannerFor(drift.getResourceType());
		Markable resource = scanner.findActiveMarkableById(drift.getResourceId())
				.orElseThrow(() -> new IllegalStateException("자원이 더 이상 활성 상태가 아닙니다."));

		Optional<String> recomputed = scanner.recomputeManifestHash(resource);
		if (recomputed.isEmpty()) {
			throw new IllegalStateException("본체 파일에 접근할 수 없습니다 — 파일 상태를 확인하세요.");
		}
		if (!recomputed.get().equals(drift.getObservedHash())) {
			// 사용자가 카드에서 확인·대조한 그 내용과 다른 것이 정본화되는 사고 차단.
			throw new IllegalStateException("확인한 내용과 파일이 다시 달라졌습니다 — 재점검 후 새 보고에서 진행하세요.");
		}

		MarkerContent existing = markerService.read(resource.getResourcePath(), resource.getMarkerLayout());
		MarkerContent updated = new MarkerContent(
				existing.resourceType(), existing.resourceId(), existing.attributes(),
				Instant.now(),                 // 정본 인정 시각 갱신
				recomputed.get(), null
		);
		String signature = markerService.computeSignature(updated);

		// 순서가 안전장치다 (적대적 검증 CRITICAL 반영) — DB 변이(카드 제거·엔티티 갱신)를 먼저 실행하고
		// 즉시 flush 해, 동시 dismiss/보고서 정리와의 충돌(낙관적 잠금)을 "파일 쓰기 전"에 표면화한다.
		// 종전 순서(파일 먼저)는 충돌 롤백 시 파일만 정본화되고 감사 로그가 소실되는 최악 조합이었다.
		// 파일 쓰기를 마지막에 두면 잔여 위험은 "커밋 자체 실패" 한 가지로 줄고, 그 경우 마커(신값)와
		// DB(구값)의 어긋남은 다음 정밀 점검이 다시 보고한다 (침묵 소실 없음).
		resource.reissueMarker(recomputed.get(), signature);
		drift.getReport().removeDrift(drift);
		driftRepository.flush();

		markerService.write(resource.getResourcePath(), resource.getMarkerLayout(), updated.withSignature(signature));
		log.warn("[AUDIT] 내용 변경 수용 — {}#{} ({}) 등록 지문 {}… → {}… (자원명 확인 경유, drift {})",
				drift.getResourceType(), drift.getResourceId(), resource.displayName(),
				truncate(existing.manifestHash()), truncate(recomputed.get()), driftId);
		return resource.displayName();
	}

	private MarkableScanner scannerFor(ResourceType type) {
		MarkableScanner scanner = scanners.get(type);
		if (scanner == null) {
			throw new IllegalStateException("지원하지 않는 자원 종류 : " + type);
		}
		return scanner;
	}

	private static String truncate(String hash) {
		return hash == null ? "?" : hash.substring(0, Math.min(12, hash.length()));
	}
}
