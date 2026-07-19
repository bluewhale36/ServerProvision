package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.*;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.ReconciliationAlreadyRunningException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.DriftResolution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MK1 본체 — 자원 인벤토리와 디스크 마커를 대조해 {@code DriftReport} 를 생성·영속화한다.
 *
 * <p>스캔 트리거:
 * <ol>
 *   <li>시작시 1회 (quick) — {@code @EventListener(ApplicationReadyEvent)}</li>
 *   <li>Quick 주기 — default 1h. 마커 서명만 검증, 내용 변조 못 잡음</li>
 *   <li>Deep 주기 — default 24h. manifestHash 재계산 — 내용 변조 감지</li>
 *   <li>수동 — {@link #triggerScan(boolean)}</li>
 * </ol>
 *
 * <p>스캔 범위 (D19): DB 활성+softDeleted 자원의 path.parent union 동적. 도메인별 분리 디렉토리 자동 대응.
 * {@code reconciliation.scan.extra-roots} 콤마 구분 설정으로 명시 추가 가능.</p>
 *
 * <p>FIFO prune (D15): 보고서 영속화 후 retention-count 초과면 가장 오래된 행 삭제.</p>
 */
@Slf4j
@Service
public class PathReconciliationService {

	public PathReconciliationService(
			List<MarkableScanner> scanners,
			ProvisionMarkerService markerService,
			BackgroundJobService backgroundJobService,
			DriftReportRepository driftReportRepository,
			DriftRepository driftRepository,
			List<DriftResolution> resolutions,
			@Lazy PathReconciliationService self
	) {
		this.scanners = scanners;
		this.markerService = markerService;
		this.backgroundJobService = backgroundJobService;
		this.driftReportRepository = driftReportRepository;
		this.driftRepository = driftRepository;
		// S6-2-1 — kind 별 해결 전략 bean 디스패치 (1 bean = 1 kind, 중복 등록은 조립 시점 즉시 실패).
		this.resolutions = resolutions.stream()
				.collect(Collectors.toUnmodifiableMap(DriftResolution::supportedKind, r -> r));
		this.self = self;
	}

	private final List<MarkableScanner> scanners;
	private final ProvisionMarkerService markerService;
	private final BackgroundJobService backgroundJobService;
	private final DriftReportRepository driftReportRepository;
	private final DriftRepository driftRepository;
	private final Map<DriftKind, DriftResolution> resolutions;

	/**
	 * 자기 자신의 Spring proxy 참조. {@code @Async} / {@code @Transactional} 어노테이션은 프록시 경로로
	 * 진입해야 동작하는데 동일 클래스 내부 메서드 호출({@code this.runAsync(...)})은 프록시를 우회한다.
	 * 결과 : 비동기 실행이 안 되어 HTTP 요청 스레드가 deep scan 동안 블록되고, {@code performScan}
	 * 의 트랜잭션 경계도 사라져 DriftReport / Drift 영속화가 단일 트랜잭션 안에서 묶이지 못한다.
	 * {@code @Lazy} 로 자기 참조를 받아 외부 호출 형태로 진입시켜 양쪽 어노테이션을 모두 살린다.
	 */
	private final PathReconciliationService self;

	@Value("${reconciliation.scan.startup-enabled:true}")
	private boolean startupEnabled;

	@Value("${reconciliation.report.retention-count:100}")
	private int retentionCount;

	/**
	 * S6-2-1 — 스캔 중 무인 자동 적용을 허용할 kind CSV (예: PATH_DRIFT,GHOST_DB_ROW). default 빈 = 전부 수동.
	 * 과거 kind 별 boolean 키(auto-apply-path-drift / auto-apply-ghost-row)가 AUTO kind 증가마다 키·분기를
	 * 함께 늘리던 것을 단일 키로 통합. 파싱은 {@link #autoApplyKinds()}.
	 */
	@Value("${reconciliation.auto-apply.kinds:}")
	private String autoApplyKindsCsv;

	@Value("${reconciliation.scan.extra-roots:}")
	private String extraRootsCsv;

	/**
	 * MK3(DCN-NEW11) → S6-2-1 개명 — 시스템 해결 전면 차단 마스터. default true. false 시 수동 [적용] 버튼과
	 * 스캔 무인 적용이 모두 차단된다(forced saga 만 통과). MANUAL 도입으로 "auto-apply" 어휘가 수동 해결까지
	 * 덮지 못하게 되어 resolution-enabled 로 개명.
	 * <p>Boolean wrapper 사용 — unit test mock 환경에서 @Value 미주입 시 null. {@code Boolean.FALSE.equals}
	 * 로 비교하면 null = 활성 (운영자 의도 default = 활성).</p>
	 */
	@Value("${reconciliation.resolution-enabled:true}")
	private Boolean resolutionEnabled;

	/**
	 * MK3 — Trash 디렉토리 (walk 시 명시 제외). macOS 호환을 위해 `.soft-deleted` 사용.
	 */
	@Value("${trash.root:/opt/provisioning/.soft-deleted}")
	private String trashRoot;

	/**
	 * 동시 실행 차단. 스캔 시작 시 true 로, 종료 시 false 로.
	 */
	private final AtomicBoolean running = new AtomicBoolean(false);

	// ==== 트리거 ========================================================

	@EventListener(ApplicationReadyEvent.class)
	public void onStartup() {
		if (!startupEnabled) {
			log.info("[reconciliation] startup scan 비활성 (reconciliation.scan.startup-enabled=false)");
			return;
		}
		try {
			triggerScan(false);
		} catch (ReconciliationAlreadyRunningException ignored) {
			// 부팅 직후 다중 호출 방지 — 보통 발생 안 함
		}
	}

	@Scheduled(
			fixedRateString = "${reconciliation.scan.interval-ms:3600000}",
			initialDelayString = "${reconciliation.scan.interval-ms:3600000}"
	)
	public void scheduledQuickScan() {
		if (running.get()) {
			log.debug("[reconciliation] quick 주기 스캔 skip (이전 스캔 RUNNING)");
			return;
		}
		try {
			triggerScan(false);
		} catch (ReconciliationAlreadyRunningException ignored) {
		}
	}

	@Scheduled(
			fixedRateString = "${reconciliation.scan.deep-interval-ms:86400000}",
			initialDelayString = "${reconciliation.scan.deep-interval-ms:86400000}"
	)
	public void scheduledDeepScan() {
		if (running.get()) {
			log.debug("[reconciliation] deep 주기 스캔 skip (이전 스캔 RUNNING)");
			return;
		}
		try {
			triggerScan(true);
		} catch (ReconciliationAlreadyRunningException ignored) {
		}
	}

	/**
	 * 수동/주기 공용 스캔 트리거. BackgroundJob 등록 후 비동기 실행.
	 *
	 * @return BackgroundJob 의 jobId
	 */
	public String triggerScan(boolean deep) {
		if (!running.compareAndSet(false, true)) {
			throw new ReconciliationAlreadyRunningException();
		}
		String jobId = backgroundJobService.register(
				JobType.PATH_RECONCILIATION,
				deep ? "정밀 점검" : "자원 무결성 점검",
				deep ? "파일 내용 해시 재계산 포함" : "마커 서명 검증",
				BackgroundJobService.stagesOf(ReconciliationStage.values())
		);
		// self proxy 경유 — 그래야 @Async 가 살아 별도 스레드에서 실행되고 호출 스레드(보통 HTTP 요청 스레드)가
		// 곧바로 jobId 를 반환받을 수 있다. 직접 호출 시 동일 스레드에서 동기 실행되어 long-running deep scan
		// 동안 사용자 응답이 막힌다.
		self.runAsync(jobId, deep);
		return jobId;
	}

	@Async
	public void runAsync(String jobId, boolean deep) {
		try {
			backgroundJobService.startStage(jobId, ReconciliationStage.SCANNING);
			// self proxy 경유 — performScan 의 @Transactional 이 살아 보고서/drift 영속화가 단일 트랜잭션
			// 안에서 묶인다. 직접 호출 시 트랜잭션이 누락되어 save / prune 이 자동커밋으로 흩어진다.
			DriftReport report = self.performScan(deep, jobId);
			log.info(
					"[reconciliation] 스캔 완료. deep={}, totalChecked={}, drifts={}",
					deep, report.getTotalChecked(), report.getDriftCount()
			);
			// R9-1 — 완료 시점 결과 수치를 Job 에 탑재. 페이지가 bgjob:completed 토스트 문구에 사용.
			backgroundJobService.complete(jobId, Map.of(
					"driftCount", String.valueOf(report.getDriftCount())
			));
		} catch (RuntimeException e) {
			log.error("[reconciliation] 스캔 실패", e);
			backgroundJobService.fail(jobId, "스캔 실패 : " + e.getMessage());
		} finally {
			running.set(false);
		}
	}

	// ==== 마커 서명 재발급 (권고1, secret 회전 admin 도구) ====================

	/**
	 * 모든 활성 자원의 마커 signature 를 현재 secret 으로 재계산. {@code manifestHash} 는 그대로 유지한다 —
	 * 변조된 자원의 hash 가 굳어지는 것을 막고, 다음 deep scan 에서 그대로 노출되도록 한다.
	 * <p>secret 회전 시 운영자가 이 endpoint 를 1회 호출 → 모든 마커 파일이 새 secret 으로 재서명되어
	 * 다음 quick scan 의 SIGNATURE_INVALID 일괄 오탐을 막는다.</p>
	 */
	public String triggerReissueAllSignatures() {
		if (!running.compareAndSet(false, true)) {
			throw new ReconciliationAlreadyRunningException();
		}
		String jobId = backgroundJobService.register(
				JobType.MARKER_REISSUE,
				"마커 서명 재발급",
				"현재 secret 으로 모든 자원의 signature 재계산",
				// R9-1 — 스캔용 3단계 차용(거짓 진행바) 해소. 재발급은 단일 단계.
				BackgroundJobService.stagesOf(ReissueStage.values())
		);
		self.runReissueAsync(jobId);
		return jobId;
	}

	@Async
	public void runReissueAsync(String jobId) {
		int failedCount = 0;
		try {
			backgroundJobService.startStage(jobId, ReissueStage.RESIGNING);
			ReissueResult result = self.performReissue();
			failedCount = result.failures().size();
			log.warn(
					"[AUDIT] 마커 서명 재발급 완료 — successCount={}, failedCount={}, failures={}",
					result.successCount(), result.failures().size(), result.failures()
			);
			// R9-1 — 로그로만 새던 부분 실패 건수를 Job 결과로 탑재 → 페이지 토스트로 표면화.
			backgroundJobService.complete(jobId, Map.of(
					"reissueSucceeded", String.valueOf(result.successCount()),
					"reissueFailed", String.valueOf(result.failures().size())
			));
		} catch (RuntimeException e) {
			log.error("[reconciliation] 마커 재발급 실패", e);
			backgroundJobService.fail(jobId, "재발급 실패 : " + e.getMessage());
		} finally {
			running.set(false);
		}
		// R9-6 — 부분 실패가 있으면 점검을 곧바로 이어 돌린다. 재서명이 안 된 마커는 다음 주기(최대 1h)까지
		// "서명 불일치"로 방치되는데, 후속 점검이 실패 자원들을 보고서 카드로 즉시 표면화한다.
		// 잠금(running) 해제 이후에만 가능 — 점검과 재발급이 동시 실행 가드를 공유하기 때문.
		// 한계(인지·수용): 마커 파일 재서명은 됐고 DB 기록 갱신만 실패한 유형은 마커가 유효해 안 잡힌다.
		if (failedCount > 0) {
			try {
				triggerScan(false);
				log.info("[reissue] 부분 실패 {}건 — 후속 자원 무결성 점검 자동 시작", failedCount);
			} catch (ReconciliationAlreadyRunningException ignored) {
				// 그 찰나에 주기 점검이 선점했으면 그것으로 충분 — 조용히 양보.
			}
		}
	}

	@Transactional
	public ReissueResult performReissue() {
		int successCount = 0;
		List<String> failures = new ArrayList<>();
		for (MarkableScanner scanner : scanners) {
			for (Markable resource : scanner.findActiveMarkables()) {
				String label = resource.getResourceType() + "#" + resource.getResourceId();
				try {
					MarkerContent existing = markerService.read(
							resource.getResourcePath(), resource.getMarkerLayout());
					MarkerContent unsigned = existing.withoutSignature();
					String newSig = markerService.computeSignature(unsigned);
					markerService.write(
							resource.getResourcePath(), resource.getMarkerLayout(),
							unsigned.withSignature(newSig)
					);
					// manifestHash 는 그대로 유지 — 변조 가능성을 굳히지 않는다.
					resource.reissueMarker(existing.manifestHash(), newSig);
					successCount++;
				} catch (RuntimeException e) {
					failures.add(label + " : " + e.getMessage());
					log.warn("[reissue] 자원 재발급 실패. {} : {}", label, e.getMessage());
				}
			}
		}
		return new ReissueResult(successCount, failures);
	}

	/**
	 * 마커 재발급 결과 — successCount 건 갱신 + failures 는 사유 메시지 모음.
	 */
	public record ReissueResult(
			int successCount,
			List<String> failures
	) {

	}

	// ==== 스캔 알고리즘 =================================================

	/**
	 * 트랜잭션 경계는 메서드 호출 단위 — 스캔 1회의 보고서 영속화는 한 트랜잭션.
	 */
	@Transactional
	public DriftReport performScan(boolean deep, String jobId) {
		Instant start = Instant.now();

		// (1) 인벤토리 수집 — active + soft-deleted 전수 (S6-2-2).
		// 메타 자원 2종(OS_IMAGE/BOARD_MODEL)은 파일 실체가 없어(resourcePath=null) 분류 대상에서 명시 제외
		// (TrashController 의 isMetadata() 가드 선례 — 빠뜨리면 NPE/유령 오탐).
		List<Markable> activeInventory = new ArrayList<>();
		Map<MarkerKey, Markable> deletedByKey = new HashMap<>();
		Map<ResourceType, MarkableScanner> scannersByType = new HashMap<>();
		for (MarkableScanner scanner : scanners) {
			scannersByType.put(scanner.supportedType(), scanner);
			activeInventory.addAll(scanner.findActiveMarkables());
			if (!scanner.supportedType().isMetadata()) {
				for (Markable trashed : scanner.findTrashed()) {
					deletedByKey.put(new MarkerKey(trashed.getResourceType(), trashed.getResourceId()), trashed);
				}
			}
		}

		// (2) 스캔 루트 동적 산출 — active 자원의 path.parent union + extra-roots
		Set<Path> scanRoots = computeScanRoots(activeInventory);

		// (3) 디스크에서 마커 모두 수집 (파일명 패턴 *.provision.json)
		// (권고6) 부분 실패 가시화 — walk IOException 등으로 일부 root 가 누락되면 failedScanRoots 에 누적
		// HF4-5 — key 당 발견 전체를 보존한다 (List). 종전 putIfAbsent(첫 발견 1건)는 중복 사본을
		// 수집 단계에서 침묵시켰다. 소비부는 중복 탐지(4a)만 전체를 보고 나머지(4b/ORPHAN/ESCAPE)는
		// 종전과 같은 첫 발견을 쓴다 — 행동 변화를 중복 탐지에 한정.
		List<String> failedScanRoots = new ArrayList<>();
		Map<MarkerKey, List<MarkerHit>> diskMarkers = collectDiskMarkers(scanRoots, failedScanRoots);

		// (4) drift 분류
		// R9-1 — 실경계 stage 계측. startStage 는 RUNNING 표시일 뿐이라 트랜잭션 롤백 시
		// runAsync 의 fail() 이 해당 단계를 ERROR 로 마킹 — 허위 "완료" 표시가 생기지 않는다.
		backgroundJobService.startStage(jobId, ReconciliationStage.CLASSIFYING);
		List<Drift> drifts = new ArrayList<>();
		Set<MarkerKey> matchedMarkers = new HashSet<>();
		Instant now = Instant.now();

		for (Markable resource : activeInventory) {
			MarkerKey key = new MarkerKey(resource.getResourceType(), resource.getResourceId());
			Path expectedPath = resource.getResourcePath();
			Path expectedMarker = markerService.resolveMarkerFile(expectedPath, resource.getMarkerLayout());

			// 4a) DB 가 알고 있는 위치에 마커가 있는가?
			if (Files.exists(expectedMarker)) {
				matchedMarkers.add(key);
				MarkerContent content;
				try {
					content = markerService.read(expectedPath, resource.getMarkerLayout());
				} catch (RuntimeException e) {
					drifts.add(buildDrift(
							resource, DriftKind.SIGNATURE_INVALID, expectedPath.toString(),
							null, now, "마커 파싱 실패 : " + e.getMessage()
					));
					continue;
				}
				if (!markerService.verifySignature(content)) {
					drifts.add(buildDrift(
							resource, DriftKind.SIGNATURE_INVALID, expectedPath.toString(),
							null, now, "HMAC 서명 불일치 — 마커 변조 가능성"
					));
					continue;
				}
				// S6-1 — 마커가 정상이어도 본체가 없으면 quick 에서 즉시 MISSING. 종전에는 deep 의
				// manifestHash 재계산 실패로만 드러나 deep 주기(기본 24h)까지 침묵했다. 서명 검증 뒤에
				// 두는 이유 : 변조 의심(보안 신호)이 자원 부재(운영 신호)보다 먼저 노출되어야 한다.
				if (!resourceBodyExists(expectedPath, resource.getMarkerLayout())) {
					drifts.add(buildDrift(
							resource, DriftKind.MISSING, expectedPath.toString(),
							null, now, "마커는 있으나 본체 파일 부재 — 파일명 변경 또는 삭제 가능성"
					));
					continue;
				}
				// HF4-5 — 원본이 완전 정상(마커 존재·파싱·서명·본체)임을 확인한 이 지점에서만 중복 사본을
				// 보고한다. 원본에 자체 드리프트가 있으면 그 신호가 우선(위 continue 들)이고, 원본 소실 시엔
				// 4b 의 PATH_DRIFT 분류가 유효하다 — 판정 순서가 곧 D1 결정. deep 의 HASH_MISMATCH 와는
				// 독립 신호라 동시 보고될 수 있다 (TRASH_MARKER_STALE 선례).
				addDuplicateDrifts(resource, expectedPath, diskMarkers.get(key), drifts, now);
				if (deep) {
					Optional<String> recomputed = scannersByType.get(resource.getResourceType())
							.recomputeManifestHash(resource);
					if (recomputed.isEmpty()) {
						// (B-2) Optional.empty 는 본체 자원이 사라졌거나 재계산이 실패한 신호.
						// 마커는 있지만 본체가 없는 상태 — MISSING 으로 노출해야 운영자가 인지한다.
						drifts.add(buildDrift(
								resource, DriftKind.MISSING, expectedPath.toString(),
								null, now, "deep scan : manifestHash 재계산 실패 — 본체 자원 부재 또는 IO 오류"
						));
					} else if (!markerService.verifyManifestHash(content, recomputed.get())) {
						// S6-3-4 — 수용의 판단 재료를 감지 시점에 스냅샷 : 현재 지문(observedHash — 실행 시
						// 재대조·외부 체크섬 대조용 전문)과 정본 인정 시각(마커가 마지막으로 서명된 때).
						// 파일 수정 시각(mtime)은 위조 가능해 표시하지 않는다 (CP1 반려 확정).
						drifts.add(Drift.builder()
								.resourceType(resource.getResourceType())
								.resourceId(resource.getResourceId())
								.displayName(resource.displayName())
								.kind(DriftKind.HASH_MISMATCH)
								.oldPath(expectedPath.toString())
								.newPath(null)
								.detectedAt(now)
								.observedHash(recomputed.get())
								.detail("내용 지문 불일치 — 변조 또는 의도된 교체. 정본 인정(마커 서명) "
										+ KST_MINUTE.format(content.createdAt())
										+ " · 등록 지문 " + content.manifestHash()
										+ " · 현재 지문 " + recomputed.get())
								.build());
					}
				}
				continue;
			}

			// 4b) 다른 위치에서 (resourceType, resourceId) 매칭 마커 발견? → PATH_DRIFT
			// 주의 (B-1) : SIDECAR 의 경우 마커만 옮겨지고 본체 파일이 함께 이동되지 않았다면
			// 자동 적용 시 DB 의 path 가 존재하지 않는 파일을 가리키게 된다. 본체 부재 시 PATH_DRIFT
			// 로 분류하지 않고 MISSING 으로 떨어뜨려 운영자 검토를 강제한다.
			MarkerHit hit = firstHit(diskMarkers.get(key));
			if (hit != null) {
				matchedMarkers.add(key);
				if (!resourceBodyExists(hit.resourcePath(), hit.layout())) {
					drifts.add(buildDrift(
							resource, DriftKind.MISSING, expectedPath.toString(),
							null, now, "다른 위치에 " + hit.layout() + " 마커는 있으나 본체가 부재 — 마커만 이동 가능성 (의심 경로 : "
									+ hit.resourcePath() + ")"
					));
					continue;
				}
				drifts.add(buildDrift(
						resource, DriftKind.PATH_DRIFT, expectedPath.toString(),
						hit.resourcePath().toString(), now, null
				));
				continue;
			}

			// 4c) 어디에도 마커 없음 → MISSING
			drifts.add(buildDrift(
					resource, DriftKind.MISSING, expectedPath.toString(),
					null, now, "DB 경로와 검색 범위 모두에서 마커를 찾지 못함"
			));
		}

		// (5) ORPHAN — 디스크 마커 중 DB 인벤토리에 매칭 안 된 것.
		// S6-2-2 — soft-deleted 매칭 마커는 종전의 침묵 제외(D20) 대신 ESCAPE 로 분류한다:
		// 발견 위치가 원위치(본체 포함)면 "삭제 자원 복귀", 아니면 "삭제 자원 위치 이탈".
		Set<MarkerKey> escapeReported = new HashSet<>();
		for (Map.Entry<MarkerKey, List<MarkerHit>> e : diskMarkers.entrySet()) {
			MarkerKey key = e.getKey();
			if (matchedMarkers.contains(key)) continue;
			// HF4-5 — ORPHAN/ESCAPE 는 종전대로 첫 발견 hit 만 사용 (다중 사본 소비 확대는 scope 밖 — plan §8).
			MarkerHit hit = firstHit(e.getValue());
			if (hit == null) continue;
			Markable deleted = deletedByKey.get(key);
			if (deleted != null) {
				escapeReported.add(key);
				Drift escape = classifyEscape(deleted, hit, now);
				if (escape != null) drifts.add(escape);
				continue;
			}
			drifts.add(Drift.builder()
							   .resourceType(key.resourceType())
							   .resourceId(key.resourceId())
							   // R9-5 — ORPHAN 은 DB 매칭 자원(Markable)이 없어 마커 본체 파일명이 실명 fallback.
							   .displayName(hit.resourcePath().getFileName() != null
									   ? hit.resourcePath().getFileName().toString() : null)
							   .kind(DriftKind.ORPHAN)
							   .oldPath(hit.resourcePath().toString())
							   .newPath(null)
							   .detectedAt(now)
							   .detail("DB 에 매칭되는 자원 없음")
							   .build());
		}

		// (5.5a) S6-2-3 — soft-deleted 전수 대조 완성. 삭제 자원의 다섯 상태(정상/복귀/이탈/소실/유령)를
		// 한 패스에서 판정한다. 종전의 별도 ghost 패스(findGhostMarkables 루프)는 여기로 흡수 —
		// SPI 자체는 휴지통 화면(TrashController)이 계속 사용하므로 유지.
		for (Map.Entry<MarkerKey, Markable> e : deletedByKey.entrySet()) {
			Markable deleted = e.getValue();
			if (!(deleted instanceof LifecycleEntity lifecycle)) continue;
			String trashedPath = lifecycle.getTrashedPath();
			boolean trashAlive = trashedPath != null && Files.exists(Path.of(trashedPath));

			// 잔여 마커 — 독립 신호 (다른 판정·escapeReported 와 무관하게 동시 보고 가능).
			// 휴지통은 수색(walk) 범위 밖이라, 기록이 가리키는 정확한 위치만 직접 들여다본다.
			if (trashAlive) {
				Path staleMarker = markerService.resolveMarkerFile(
						Path.of(trashedPath), deleted.getMarkerLayout());
				if (staleMarker != null && Files.exists(staleMarker)) {
					drifts.add(buildDrift(
							deleted, DriftKind.TRASH_MARKER_STALE,
							trashedPath, null, now,
							"휴지통 실물 옆에 삭제 시 정리됐어야 할 마커 잔존"
					));
				}
			}

			if (escapeReported.contains(e.getKey())) continue; // 마커 기반 ESCAPE 로 이미 보고됨
			boolean bodyAtOriginal = resourceBodyExists(deleted.getResourcePath(), deleted.getMarkerLayout());

			if (trashedPath == null) {
				if (bodyAtOriginal) {
					drifts.add(buildDrift(
							deleted, DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL,
							deleted.getResourcePath().toString(), deleted.getResourcePath().toString(), now,
							"휴지통 기록이 없던 자원이 원위치에 출현 — 외부 복귀로 판단"
					));
				} else if (lifecycle.getTrashedAt() == null) {
					// 유령 기록 — 휴지통 기록도 실물도 원위치 파일도 전부 없음 (GhostEvaluator 정의 등가).
					drifts.add(buildDrift(
							deleted, DriftKind.GHOST_DB_ROW,
							deleted.getResourcePath().toString(), null, now,
							"DB row 만 남은 ghost — FS 자원도 trash 도 없음. drift apply = DB row hard-delete."
					));
				}
				continue;
			}
			if (trashAlive) {
				// 정상 휴지통 보관(원위치 비어 있음) 또는 점유(원위치에 파일) — 둘 다 drift 아님.
				// 점유 파일의 진위는 복원 시점 게이트(RestorePathOccupiedException)가 판정.
				continue;
			}
			if (bodyAtOriginal) {
				drifts.add(buildDrift(
						deleted, DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL,
						trashedPath, deleted.getResourcePath().toString(), now,
						"휴지통 파일이 없고 자원이 원위치에 복귀 — 외부 복귀로 판단"
				));
			} else {
				// 휴지통 소실 — 기록은 있는데 실물이 어디에도 없음. 복구 불능 확정.
				drifts.add(buildDrift(
						deleted, DriftKind.TRASH_LOST,
						trashedPath, null, now,
						"휴지통 파일이 사라짐 — 외부 정리 의심. 복구 불가, 적용 시 기록 정리 + 감사 기록"
				));
			}
		}

		// (6) DriftReport 영속화
		backgroundJobService.startStage(jobId, ReconciliationStage.PERSISTING);
		long durationMs = Duration.between(start, Instant.now()).toMillis();
		DriftReport report = DriftReport.builder()
				.scannedAt(start)
				.scanDurationMs(durationMs)
				.deep(deep)
				.totalChecked(activeInventory.size())
				.build();
		for (Drift d : drifts) {
			report.addDrift(d);
		}
		report.recordFailedScanRoots(failedScanRoots);
		DriftReport saved = driftReportRepository.save(report);

		// (7) FIFO prune (D15)
		pruneOldReports();

		// (8) 무인 자동 적용 (옵트인, D13 → S6-2-1 디스패치 통합) — mode==AUTO 이고 auto-apply.kinds 에
		// 포함된 kind 만. kind 별 if-else + 개별 boolean 키의 증식을 전략 bean 디스패치 + CSV 1키로 치환.
		// 해결돼도 drift 는 보고서에 남긴다 (기록 보존 — 보고서에서의 제거는 수동 apply 전용).
		if (!isResolutionEnabled()) {
			// 전역 OFF — 자동 적용 건너뜀.
		} else {
			Set<DriftKind> enabledKinds = autoApplyKinds();
			for (Drift d : saved.getDrifts()) {
				if (!d.getKind().isAutoApplicable() || !enabledKinds.contains(d.getKind())) continue;
				DriftResolution resolution = resolutions.get(d.getKind());
				if (resolution == null) continue; // 해결 미구현 AUTO kind — 스캔을 죽이지 않고 skip
				try {
					resolution.resolve(d, scannersByType.get(d.getResourceType()));
				} catch (RuntimeException ex) {
					log.warn(
							"[reconciliation] 자동 적용 실패. driftId={}, kind={}, msg={}",
							d.getId(), d.getKind(), ex.getMessage()
					);
				}
			}
		}

		return saved;
	}

	private Set<Path> computeScanRoots(List<Markable> active) {
		Set<Path> roots = new HashSet<>();
		for (Markable m : active) {
			Path parent = m.getResourcePath().getParent();
			if (parent != null) roots.add(parent);
		}
		// soft-deleted 자원의 부모는 넣지 않는다 — 복귀 감지는 walk 가 아니라 entity 별 존재 검사(5.5a)로 하고,
		// 이탈 감지는 active 트리(위 roots) 안에서 발견되는 마커가 대상이라 범위 확장이 불필요.
		// extra-roots — 명시 설정
		if (extraRootsCsv != null && !extraRootsCsv.isBlank()) {
			for (String r : extraRootsCsv.split(",")) {
				String trimmed = r.trim();
				if (!trimmed.isEmpty()) roots.add(Path.of(trimmed));
			}
		}
		return roots;
	}

	private static final int WALK_MAX_DEPTH = 8;

	/**
	 * S6-3-4 — 카드 detail 의 사람용 시각 표기 (KST, 분 단위).
	 */
	private static final java.time.format.DateTimeFormatter KST_MINUTE =
			java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
					.withZone(java.time.ZoneId.of("Asia/Seoul"));

	private Map<MarkerKey, List<MarkerHit>> collectDiskMarkers(Set<Path> scanRoots, List<String> failedRoots) {
		Map<MarkerKey, List<MarkerHit>> result = new HashMap<>();
		// MK3 — trashRoot 미설정 (test mock 환경 등) 시 walk skip 비활성. null 이면 모든 walk 결과 통과.
		Path trashRootPath = (trashRoot != null && !trashRoot.isBlank())
				? Path.of(trashRoot).toAbsolutePath().normalize()
				: null;
		for (Path root : scanRoots) {
			if (!Files.isDirectory(root)) continue;
			// (B-3) walk 깊이 제한이 적중하면 운영자가 인지할 수 있도록 boundary 마커 카운트를 함께 본다.
			int[] boundaryHits = {0};
			try (Stream<Path> walker = Files.walk(root, WALK_MAX_DEPTH)) {
				walker.filter(Files::isRegularFile)
						// MK3 — trash 디렉토리 안 마커는 walk 결과에서 명시 제외 (active 인벤토리와 별개 lifecycle).
						.filter(p -> trashRootPath == null
								|| !p.toAbsolutePath().normalize().startsWith(trashRootPath))
						.filter(p -> p.getFileName().toString().endsWith(".provision.json"))
						.peek(p -> {
							if (root.relativize(p).getNameCount() >= WALK_MAX_DEPTH) boundaryHits[0]++;
						})
						.forEach(markerFile -> {
							MarkerHit hit = parseMarkerHit(markerFile);
							if (hit != null) {
								MarkerKey key = new MarkerKey(hit.resourceType(), hit.resourceId());
								// HF4-5 — 같은 key 의 발견을 전부 보존한다 (종전 putIfAbsent 는 중복 사본 침묵).
								// 단, 중첩 scan root 가 같은 마커를 재방문하는 경우는 정규화 경로 dedupe 로
								// 걸러 동일 위치의 이중 보고를 막는다 (종전엔 putIfAbsent 가 우연히 막던 것).
								List<MarkerHit> hits = result.computeIfAbsent(key, k -> new ArrayList<>());
								Path normalized = hit.resourcePath().toAbsolutePath().normalize();
								boolean alreadySeen = hits.stream().anyMatch(
										h -> h.resourcePath().toAbsolutePath().normalize().equals(normalized));
								if (!alreadySeen) hits.add(hit);
							}
						});
			} catch (IOException e) {
				log.warn("[reconciliation] scan root walk 실패. root={}, msg={}", root, e.getMessage());
				failedRoots.add(root + " : " + e.getMessage());
			}
			if (boundaryHits[0] > 0) {
				log.warn(
						"[reconciliation] scan root '{}' 에서 walk 최대 깊이({}) 경계에 위치한 마커가 {}건. "
								+ "더 깊은 트리에 가려진 마커가 있을 수 있다.", root, WALK_MAX_DEPTH, boundaryHits[0]
				);
			}
		}
		return result;
	}

	/**
	 * 마커 파일 경로로부터 자원 위치 + (resourceType, resourceId) 추론.
	 */
	private MarkerHit parseMarkerHit(Path markerFile) {
		String filename = markerFile.getFileName().toString();
		Path resourcePath;
		MarkerLayout layout;
		if (filename.equals(".provision.json")) {
			// IN_TREE — 자원 = 부모 디렉토리
			resourcePath = markerFile.getParent();
			layout = MarkerLayout.IN_TREE;
		} else {
			// SIDECAR — 자원 = 같은 디렉토리의 base 파일 (확장자에서 .provision.json 제거)
			String base = filename.substring(0, filename.length() - ".provision.json".length());
			resourcePath = markerFile.resolveSibling(base);
			layout = MarkerLayout.SIDECAR;
		}
		try {
			MarkerContent content = markerService.read(resourcePath, layout);
			ResourceType type;
			try {
				type = ResourceType.valueOf(content.resourceType());
			} catch (IllegalArgumentException e) {
				// (D-19) 외부 시스템이 만든 .provision.json 일 수도, 신/구 버전 차이일 수도 있으나
				// 운영자에게 가시화되지 않으면 영원히 묻힌다. WARN 으로 끌어올린다.
				log.warn(
						"[reconciliation] 알 수 없는 resourceType : {} (path={}) — 외부 자원 또는 신규 버전?",
						content.resourceType(), markerFile
				);
				return null;
			}
			return new MarkerHit(type, content.resourceId(), resourcePath, layout);
		} catch (MarkerMissingException e) {
			return null;
		} catch (RuntimeException e) {
			// (D-19) 변조/깨진 마커 — 운영자 인지가 필요하므로 WARN.
			log.warn("[reconciliation] 마커 파싱 실패. path={}, msg={}", markerFile, e.getMessage());
			return null;
		}
	}

	/**
	 * S6-1 — layout 별 본체 존재 술어. 4a(본체 부재의 quick 조기 감지)와 4b(마커만 이동 시
	 * PATH_DRIFT → MISSING 강등)가 같은 판정을 공유한다. HF4-5 — 판정 본체는 {@link MarkerLayout}
	 * 다형 메서드로 승격 (DuplicateResolveService 와 SSOT 공유) — 본 메서드는 기존 호출부 유지용 위임.
	 */
	private static boolean resourceBodyExists(Path resourcePath, MarkerLayout layout) {
		return layout.resourceBodyExists(resourcePath);
	}

	/**
	 * HF4-5 — 원본이 완전 정상일 때(4a 검사 전부 통과 지점)만 호출되는 중복 사본 보고.
	 * 사본 경로당 drift 1행 (plan D2) — oldPath=원본(DB 경로), newPath=그 사본. 본체 없는
	 * 마커만 사본은 보고하지 않는다 ("복제본"=실체 있는 사본 의미 유지, plan §8 알려진 한계 1).
	 */
	private void addDuplicateDrifts(
			Markable resource, Path expectedPath, List<MarkerHit> hits, List<Drift> drifts, Instant now
	) {
		if (hits == null) return;
		Path original = expectedPath.toAbsolutePath().normalize();
		for (MarkerHit hit : hits) {
			Path found = hit.resourcePath().toAbsolutePath().normalize();
			if (found.equals(original)) continue;
			if (!resourceBodyExists(hit.resourcePath(), hit.layout())) continue;
			drifts.add(buildDrift(
					resource, DriftKind.RESOURCE_DUPLICATED, expectedPath.toString(),
					hit.resourcePath().toString(), now,
					"원본 정상 상태에서 동일 신원의 사본 발견 — 방치 시 원본 유실 후 '경로 이동됨'으로 오인될 수 있음"
			));
		}
	}

	/**
	 * HF4-5 — 수집 List 화 이후에도 "첫 발견 우선" 소비(4b/ORPHAN/ESCAPE)를 종전과 동일하게 유지하는 헬퍼.
	 */
	private static MarkerHit firstHit(List<MarkerHit> hits) {
		return (hits == null || hits.isEmpty()) ? null : hits.get(0);
	}

	/**
	 * S6-2-2 — 삭제 자원의 마커가 active 트리에서 발견됐을 때의 분류.
	 * 발견 위치가 원위치이고 본체도 있으면 "복귀"(자동 복원 가능), 그 외는 "이탈"(사용자 확인 후 회수).
	 * 원위치에도 파일이 있는 모호 상태는 이탈로 분류하고 detail 에 병기 — 어느 쪽이 진짜인지
	 * 시스템이 판정하지 않고 복원 시점 게이트(manifestHash 검증)에 맡긴다.
	 */
	private Drift classifyEscape(Markable deleted, MarkerHit hit, Instant now) {
		Path found = hit.resourcePath().toAbsolutePath().normalize();
		Path expected = deleted.getResourcePath().toAbsolutePath().normalize();
		boolean bodyAtFound = resourceBodyExists(hit.resourcePath(), hit.layout());
		boolean bodyAtOriginal = resourceBodyExists(deleted.getResourcePath(), deleted.getMarkerLayout());
		String trashedPath = (deleted instanceof LifecycleEntity lifecycle) ? lifecycle.getTrashedPath() : null;
		boolean trashCopyAlive = trashedPath != null && Files.exists(Path.of(trashedPath));
		String oldPath = expectedTrashPath(deleted);
		if (found.equals(expected) && bodyAtOriginal) {
			if (trashCopyAlive) {
				// 점유 상태(원O·trashO) — 마커까지 복귀했어도 drift 로 보고하지 않는다(5.5a 와 동일 결정).
				// 보고하면 [적용]이 항상 409(RestorePathOccupied)로 끝나는 버튼이 노출된다.
				// 그 파일의 진위·처리는 복원 시점 게이트가 SSOT (적대적 검증 반영).
				return null;
			}
			return buildDrift(deleted, DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL,
					oldPath, deleted.getResourcePath().toString(), now,
					"삭제 자원의 마커와 본체가 원래 위치에서 발견 — 외부 복귀로 판단");
		}
		String detail = "삭제 자원이 다른 위치에서 발견"
				+ (bodyAtFound ? "" : " — 마커만 발견(본체 부재), 회수 전 파일 확인 필요")
				+ (bodyAtOriginal && !found.equals(expected) ? " — 원위치에도 파일 존재 (진위는 복원 시점 검증)" : "")
				+ (trashCopyAlive ? " — 휴지통에 기존 사본 존재 (회수하려면 먼저 정리 필요)" : "");
		return buildDrift(deleted, DriftKind.SOFTDEL_ESCAPE_TO_OTHER,
				oldPath, hit.resourcePath().toString(), now, detail);
	}

	/**
	 * S6-2-2 — ESCAPE drift 의 oldPath(기대 위치). 휴지통 기록이 없으면 DB 원위치가 유일 앵커
	 * ({@code drift.old_path} not-null 제약).
	 */
	private static String expectedTrashPath(Markable deleted) {
		if (deleted instanceof LifecycleEntity lifecycle && lifecycle.getTrashedPath() != null) {
			return lifecycle.getTrashedPath();
		}
		return deleted.getResourcePath().toString();
	}

	private Drift buildDrift(
			Markable resource, DriftKind kind, String oldPath, String newPath,
			Instant detectedAt, String detail
	) {
		return Drift.builder()
				.resourceType(resource.getResourceType())
				.resourceId(resource.getResourceId())
				// R9-5 — 스캔 시점 실명 스냅샷. 인벤토리를 이미 들고 있어 추가 조회 0.
				.displayName(resource.displayName())
				.kind(kind)
				.oldPath(oldPath)
				.newPath(newPath)
				.detectedAt(detectedAt)
				.detail(detail)
				.build();
	}

	private void pruneOldReports() {
		long total = driftReportRepository.count();
		long over = total - retentionCount;
		if (over <= 0) return;
		Pageable oldest = PageRequest.of(0, (int) over);
		Page<DriftReport> toDelete = driftReportRepository.findAllByOrderByScannedAtAsc(oldest);
		driftReportRepository.deleteAll(toDelete.getContent());
	}

	// ==== 조회 API =====================================================

	@Transactional(readOnly = true)
	public Optional<DriftReportResponse> latestReport() {
		return driftReportRepository.findFirstByOrderByScannedAtDesc()
				.map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public Page<DriftReportResponse> history(Pageable pageable) {
		return driftReportRepository.findAllBy(pageable).map(this::toResponse);
	}

	// ==== 액션 =========================================================

	@Transactional
	public void apply(Long driftId) {
		apply(driftId, false);
	}

	/**
	 * MK3-2 (DCM3-2.4) — 강제 적용 오버로드. {@code forced=true} 면 mode 가드 + 전역 OFF
	 * 옵션을 우회한다 (해결 로직 미등록 kind 의 널가드는 우회 불가). softDelete reject 의 saga "위치 정정 후 삭제" 흐름이 사용자 명시 액션이므로
	 * 글로벌 설정과 무관하게 진행되어야 함.
	 */
	@Transactional
	public void apply(Long driftId, boolean forced) {
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new DriftNotFoundException(driftId));
		if (!forced) {
			// S6-2-1 — 허용 종류는 DriftKind.isManuallyResolvable() 이 SSOT (템플릿 버튼 노출과 동일 소스).
			// 전역 OFF 옵션은 UI 가 disabled+tooltip 으로 1차 차단하므로,
			// 이 가드는 direct POST / stale 화면 안전망으로만 발동한다.
			if (!drift.getKind().isManuallyResolvable()) {
				throw DriftResolutionNotAllowedException.notApplicable(drift.getKind());
			}
			if (!isResolutionEnabled()) {
				log.warn("[reconciliation] resolution-enabled 전역 OFF — drift {} 거절", driftId);
				throw DriftResolutionNotAllowedException.globalOff();
			}
		} else {
			log.info("[reconciliation] forced apply — driftId={}, kind={}", driftId, drift.getKind());
		}
		MarkableScanner scanner = scannerFor(drift.getResourceType());
		// S6-2-1 — kind 별 해결은 DriftResolution 전략 bean 디스패치. 널가드는 forced 우회 블록 밖 —
		// forced 는 mode 가드만 우회하는 것이지 해결 로직이 없는 kind 까지 통과시키지 않는다.
		DriftResolution resolution = resolutions.get(drift.getKind());
		if (resolution == null) {
			throw DriftResolutionNotAllowedException.notApplicable(drift.getKind());
		}
		resolution.resolve(drift, scanner);
		drift.getReport().removeDrift(drift);
	}

	/**
	 * MK3-2 (DCM3-2.4) — 단일 자원 스캔. softDelete reject 의 saga 진입점에서 호출.
	 *
	 * <p>전체 인벤토리 스캔과 달리 단일 (resourceType, resourceId) 의 drift 만 분류해 in-memory
	 * 결과로 반환. 트랜잭션 일관성을 위해 영속화하지 않음 (saga 의 일부라 호출자가 후속 액션 결정).</p>
	 *
	 * <p>분류 방식 :</p>
	 * <ol>
	 *   <li>scanner 의 {@link MarkableScanner#findActiveMarkableById} 로 active markable 조회</li>
	 *   <li>DB.path 위치 마커 존재하면 → 정상 (drift 없음, empty 반환)</li>
	 *   <li>DB.path 위치 마커 부재 → scan roots 를 walk 하면서 (resourceType, resourceId) 매칭 마커 검색</li>
	 *   <li>발견 시 PATH_DRIFT, 미발견 시 MISSING</li>
	 * </ol>
	 */
	@Transactional(readOnly = true)
	public List<Drift> scanForResource(ResourceType type, Long resourceId) {
		MarkableScanner scanner = scannerFor(type);
		Optional<Markable> markableOpt = scanner.findActiveMarkableById(resourceId);
		if (markableOpt.isEmpty()) {
			return List.of();
		}
		Markable resource = markableOpt.get();
		Path expectedPath = resource.getResourcePath();
		Path expectedMarker = markerService.resolveMarkerFile(expectedPath, resource.getMarkerLayout());
		Instant now = Instant.now();

		if (Files.exists(expectedMarker)) {
			return List.of(); // 정상 — drift 없음
		}

		// 마커 부재 → scan roots 에서 (resourceType, resourceId) 매칭 검색 (PATH_DRIFT 후보)
		Set<Path> roots = computeScanRootsForResource(resource);
		Path trashRootPath = (trashRoot != null && !trashRoot.isBlank())
				? Path.of(trashRoot).toAbsolutePath().normalize()
				: null;
		MarkerKey targetKey = new MarkerKey(type, resourceId);
		for (Path root : roots) {
			if (!Files.isDirectory(root)) continue;
			try (Stream<Path> walker = Files.walk(root, WALK_MAX_DEPTH)) {
				Optional<MarkerHit> hit = walker
						.filter(Files::isRegularFile)
						.filter(p -> trashRootPath == null
								|| !p.toAbsolutePath().normalize().startsWith(trashRootPath))
						.filter(p -> p.getFileName().toString().endsWith(".provision.json"))
						.map(this::parseMarkerHit)
						.filter(java.util.Objects::nonNull)
						.filter(h -> new MarkerKey(h.resourceType(), h.resourceId()).equals(targetKey))
						.findFirst();
				if (hit.isPresent()) {
					Path newPath = hit.get().resourcePath();
					return List.of(buildDrift(
							resource, DriftKind.PATH_DRIFT,
							expectedPath.toString(), newPath.toString(), now,
							"단일 자원 스캔 — 다른 위치에서 (type, id) 매칭 마커 발견"
					));
				}
			} catch (IOException e) {
				log.warn("[reconciliation.scanForResource] walk 실패. root={}, msg={}", root, e.getMessage());
			}
		}
		return List.of(buildDrift(
				resource, DriftKind.MISSING, expectedPath.toString(), null, now,
				"단일 자원 스캔 — DB.path + 어디에도 매칭 마커 없음"
		));
	}

	/**
	 * MK3-2 — 단일 자원에 대한 scan roots 계산. 자원 path.parent + extra-roots.
	 */
	private Set<Path> computeScanRootsForResource(Markable resource) {
		Set<Path> roots = new HashSet<>();
		Path parent = resource.getResourcePath().getParent();
		if (parent != null) roots.add(parent);
		if (extraRootsCsv != null && !extraRootsCsv.isBlank()) {
			for (String r : extraRootsCsv.split(",")) {
				String trimmed = r.trim();
				if (!trimmed.isEmpty()) roots.add(Path.of(trimmed));
			}
		}
		return roots;
	}

	/**
	 * MK3-2 (DCM3-2.4) — saga 흐름에서 호출. {@link #scanForResource} 결과의 단일 drift 를 in-memory
	 * 영속화 후 forced apply. driftRepository 를 거쳐 영속화하여 apply() 가 driftId 로 처리 가능.
	 */
	@Transactional
	public void persistAndForcedApply(Drift drift) {
		// 본 메서드는 saga 의 (3) 단계 — 분류된 drift 를 일시 영속화 후 forced apply.
		DriftReport tempReport = DriftReport.builder()
				.scannedAt(Instant.now())
				.scanDurationMs(0L)
				.deep(false)
				.totalChecked(1)
				.build();
		tempReport.addDrift(drift);
		DriftReport saved = driftReportRepository.save(tempReport);
		Drift persisted = saved.getDrifts().iterator().next();
		apply(persisted.getId(), true);
	}

	/**
	 * R9-2 → S6-2-1 개명 — 시스템 해결 전면 활성 여부. 서버 가드({@link #apply(Long, boolean)})와 페이지 뷰모델
	 * (버튼 disabled+tooltip)이 이 한 메서드를 공유하는 SSOT — 두 곳에 조건을 복붙하면 drift 가 생긴다
	 * ({@code childEnableBlockReason()} 선례). {@code Boolean} wrapper 라 null(=미주입) 은 활성으로 본다.
	 * <p>허용 종류 판단은 {@link DriftKind#isManuallyResolvable()} — 이 메서드는 전역 축만 담당.</p>
	 */
	public boolean isResolutionEnabled() {
		return !Boolean.FALSE.equals(resolutionEnabled);
	}

	/**
	 * S6-2-1 — 무인 자동 적용 허용 kind 집합. 파싱은 스캔 시점 — 무효 kind 명은 IllegalArgumentException
	 * 으로 시끄럽게 실패시켜 job 실패 UI(R9-1)로 표면화한다 (설정 오타의 침묵 무시 금지).
	 */
	private Set<DriftKind> autoApplyKinds() {
		if (autoApplyKindsCsv == null || autoApplyKindsCsv.isBlank()) {
			return Set.of();
		}
		Set<DriftKind> kinds = EnumSet.noneOf(DriftKind.class);
		for (String token : autoApplyKindsCsv.split(",")) {
			String trimmed = token.trim();
			if (trimmed.isEmpty()) continue;
			try {
				kinds.add(DriftKind.valueOf(trimmed));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(
						"reconciliation.auto-apply.kinds 에 알 수 없는 DriftKind : '" + trimmed + "'", e);
			}
		}
		return kinds;
	}

	@Transactional
	public void dismiss(Long driftId) {
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new DriftNotFoundException(driftId));
		drift.getReport().removeDrift(drift);
	}

	private MarkableScanner scannerFor(ResourceType type) {
		return scanners.stream()
				.filter(s -> s.supportedType() == type)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No MarkableScanner for type: " + type));
	}

	// ==== 매핑 ========================================================

	private DriftReportResponse toResponse(DriftReport r) {
		List<DriftResponse> drifts = r.getDrifts().stream()
				.sorted(Comparator.comparing(Drift::getDetectedAt))
				.map(d -> new DriftResponse(
						d.getId(), d.getResourceType(), d.getResourceId(), d.getDisplayName(),
						d.getKind(), d.getOldPath(), d.getNewPath(), d.getDetectedAt(), d.getDetail()
				))
				.collect(Collectors.toList());
		return new DriftReportResponse(
				r.getId(), r.getScannedAt(),
				formatDuration(r.getScanDuration()), r.isDeep(), r.getTotalChecked(),
				// HF4-4 — 탐지 스냅샷(구행 0 은 엔티티 SSOT 가 미해결 수로 대체) · 미해결 잔수 병기
				r.getDetectedDriftCountForDisplay(),
				r.getDriftCount(), r.getFailedScanRootList(), drifts
		);
	}

	/**
	 * R9-2 — ISO-8601 원문(PT0.45S)이 화면에 노출되던 것을 사람이 읽는 문구로. 초 단위 미만은 소수 둘째 자리.
	 * 분 단위는 초를 반올림하되 60초로 올라가면 분으로 이월("1분 60초" 방지 — R9 최종 리뷰).
	 */
	private static String formatDuration(Duration d) {
		long minutes = d.toMinutes();
		double seconds = (d.toMillis() % 60_000) / 1000.0;
		if (minutes > 0) {
			long rounded = Math.round(seconds);
			if (rounded == 60) {
				minutes++;
				rounded = 0;
			}
			return minutes + "분 " + rounded + "초";
		}
		return String.format("%.2f초", seconds);
	}

	// ==== 내부 키 ======================================================


	private record MarkerKey(
			ResourceType resourceType,
			Long resourceId
	) {

	}


	private record MarkerHit(
			ResourceType resourceType,
			Long resourceId,
			Path resourcePath,
			MarkerLayout layout
	) {

	}
}
