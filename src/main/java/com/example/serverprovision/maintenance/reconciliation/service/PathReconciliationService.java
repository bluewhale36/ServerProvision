package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.*;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftAutoApplyNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.ReconciliationAlreadyRunningException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
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
			@Lazy PathReconciliationService self
	) {
		this.scanners = scanners;
		this.markerService = markerService;
		this.backgroundJobService = backgroundJobService;
		this.driftReportRepository = driftReportRepository;
		this.driftRepository = driftRepository;
		this.self = self;
	}

	private final List<MarkableScanner> scanners;
	private final ProvisionMarkerService markerService;
	private final BackgroundJobService backgroundJobService;
	private final DriftReportRepository driftReportRepository;
	private final DriftRepository driftRepository;

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
	 * MK3-1 — GHOST_DB_ROW drift 자동 적용 (default OFF). 사용자 사후 검토 가능.
	 */
	@Value("${reconciliation.auto-apply-ghost-row:false}")
	private boolean autoApplyGhostRow;

	@Value("${reconciliation.auto-apply-path-drift:false}")
	private boolean autoApplyPathDrift;

	@Value("${reconciliation.scan.extra-roots:}")
	private String extraRootsCsv;

	/**
	 * MK3 — 자동 정합 전역 OFF 옵션 (DCN-NEW11). default true. false 시 자동 ON DriftKind 도 보고만.
	 * <p>Boolean wrapper 사용 — unit test mock 환경에서 @Value 미주입 시 null. {@code Boolean.FALSE.equals}
	 * 로 비교하면 null = 활성 (운영자 의도 default = 활성).</p>
	 */
	@Value("${reconciliation.auto-apply:true}")
	private Boolean autoApplyEnabled;

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
				deep ? "Deep 스캔" : "경로 점검",
				deep ? "manifestHash 재계산" : "마커 서명 검증",
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
			backgroundJobService.complete(jobId);
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
				BackgroundJobService.stagesOf(ReconciliationStage.values())
		);
		self.runReissueAsync(jobId);
		return jobId;
	}

	@Async
	public void runReissueAsync(String jobId) {
		try {
			backgroundJobService.startStage(jobId, ReconciliationStage.SCANNING);
			ReissueResult result = self.performReissue();
			log.warn(
					"[AUDIT] 마커 서명 재발급 완료 — successCount={}, failedCount={}, failures={}",
					result.successCount(), result.failures().size(), result.failures()
			);
			backgroundJobService.complete(jobId);
		} catch (RuntimeException e) {
			log.error("[reconciliation] 마커 재발급 실패", e);
			backgroundJobService.fail(jobId, "재발급 실패 : " + e.getMessage());
		} finally {
			running.set(false);
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

		// (1) 인벤토리 수집 — active + softDeleted ID Set 분리
		List<Markable> activeInventory = new ArrayList<>();
		Map<ResourceType, Set<Long>> softDeletedIdsByType = new HashMap<>();
		Map<ResourceType, MarkableScanner> scannersByType = new HashMap<>();
		for (MarkableScanner scanner : scanners) {
			scannersByType.put(scanner.supportedType(), scanner);
			activeInventory.addAll(scanner.findActiveMarkables());
			softDeletedIdsByType.put(scanner.supportedType(), scanner.findSoftDeletedResourceIds());
		}

		// (2) 스캔 루트 동적 산출 — active+softDeleted 자원의 path.parent union + extra-roots
		Set<Path> scanRoots = computeScanRoots(activeInventory, softDeletedIdsByType, scannersByType);

		// (3) 디스크에서 마커 모두 수집 (파일명 패턴 *.provision.json)
		// (권고6) 부분 실패 가시화 — walk IOException 등으로 일부 root 가 누락되면 failedScanRoots 에 누적
		List<String> failedScanRoots = new ArrayList<>();
		Map<MarkerKey, MarkerHit> diskMarkers = collectDiskMarkers(scanRoots, failedScanRoots);

		// (4) drift 분류
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
						drifts.add(buildDrift(
								resource, DriftKind.HASH_MISMATCH, expectedPath.toString(),
								null, now, "manifestHash 불일치 — 자원 내용 변조 가능성"
						));
					}
				}
				continue;
			}

			// 4b) 다른 위치에서 (resourceType, resourceId) 매칭 마커 발견? → PATH_DRIFT
			// 주의 (B-1) : SIDECAR 의 경우 마커만 옮겨지고 본체 파일이 함께 이동되지 않았다면
			// 자동 적용 시 DB 의 path 가 존재하지 않는 파일을 가리키게 된다. 본체 부재 시 PATH_DRIFT
			// 로 분류하지 않고 MISSING 으로 떨어뜨려 운영자 검토를 강제한다.
			MarkerHit hit = diskMarkers.get(key);
			if (hit != null) {
				matchedMarkers.add(key);
				if (hit.layout() == MarkerLayout.SIDECAR && !Files.isRegularFile(hit.resourcePath())) {
					drifts.add(buildDrift(
							resource, DriftKind.MISSING, expectedPath.toString(),
							null, now, "다른 위치에 sidecar 마커는 있으나 본체 파일이 부재 — 마커만 이동 가능성 (의심 경로 : "
									+ hit.resourcePath() + ")"
					));
					continue;
				}
				if (hit.layout() == MarkerLayout.IN_TREE && !Files.isDirectory(hit.resourcePath())) {
					drifts.add(buildDrift(
							resource, DriftKind.MISSING, expectedPath.toString(),
							null, now, "다른 위치에 IN_TREE 마커는 있으나 트리 디렉토리가 부재 (의심 경로 : "
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

		// (5) ORPHAN — 디스크 마커 중 DB 인벤토리에 매칭 안 된 것. softDeleted ID 와 매칭되면 제외 (D20)
		for (Map.Entry<MarkerKey, MarkerHit> e : diskMarkers.entrySet()) {
			MarkerKey key = e.getKey();
			if (matchedMarkers.contains(key)) continue;
			Set<Long> softIds = softDeletedIdsByType.getOrDefault(key.resourceType(), Set.of());
			if (softIds.contains(key.resourceId())) continue;
			drifts.add(Drift.builder()
							   .resourceType(key.resourceType())
							   .resourceId(key.resourceId())
							   .kind(DriftKind.ORPHAN)
							   .oldPath(e.getValue().resourcePath().toString())
							   .newPath(null)
							   .detectedAt(now)
							   .detail("DB 에 매칭되는 자원 없음")
							   .build());
		}

		// (5.5) MK3-1 — Ghost row 감지 패스. is_deleted=true AND trashed_path=null AND FS 부재 인 dead row.
		//       active 인벤토리에 없으므로 별도 패스. drift 의 oldPath = stale DB.path, newPath = null.
		for (MarkableScanner scanner : scanners) {
			for (Markable ghost : scanner.findGhostMarkables()) {
				drifts.add(buildDrift(
						ghost, DriftKind.GHOST_DB_ROW,
						ghost.getResourcePath().toString(), null, now,
						"DB row 만 남은 ghost — FS 자원도 trash 도 없음. drift apply = DB row hard-delete."
				));
			}
		}

		// (6) DriftReport 영속화
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

		// (8) 자동 적용 (옵트인, D13) — PATH_DRIFT / MK3-1 GHOST_DB_ROW.
		if (Boolean.FALSE.equals(autoApplyEnabled)) {
			// 전역 OFF — 자동 적용 건너뜀.
		} else {
			for (Drift d : saved.getDrifts()) {
				try {
					if (d.getKind() == DriftKind.PATH_DRIFT && autoApplyPathDrift) {
						scannersByType.get(d.getResourceType())
								.applyDriftedPath(d.getResourceId(), Path.of(d.getNewPath()));
					} else if (d.getKind() == DriftKind.GHOST_DB_ROW && autoApplyGhostRow) {
						scannersByType.get(d.getResourceType())
								.applyGhostClear(d.getResourceId());
					}
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

	private Set<Path> computeScanRoots(
			List<Markable> active,
			Map<ResourceType, Set<Long>> softDeletedByType,
			Map<ResourceType, MarkableScanner> scannersByType
	) {
		Set<Path> roots = new HashSet<>();
		for (Markable m : active) {
			Path parent = m.getResourcePath().getParent();
			if (parent != null) roots.add(parent);
		}
		// softDeleted 자원의 부모도 — 마커 보존 인지를 위해
		for (Map.Entry<ResourceType, Set<Long>> e : softDeletedByType.entrySet()) {
			// softDeleted 의 path 는 ID Set 만 알고 path 는 모름. 별도 fetch 불필요 — 마커 매칭 시 ORPHAN 제외만 하면 됨.
			// 즉 scan 범위에는 softDeleted 의 parent 를 넣지 않아도 active inventory 의 parent 와 겹칠 가능성 큼.
		}
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

	private Map<MarkerKey, MarkerHit> collectDiskMarkers(Set<Path> scanRoots, List<String> failedRoots) {
		Map<MarkerKey, MarkerHit> result = new HashMap<>();
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
								// 같은 key 가 여러 위치에서 발견될 수도 있다 — 첫 발견 우선
								result.putIfAbsent(key, hit);
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

	private Drift buildDrift(
			Markable resource, DriftKind kind, String oldPath, String newPath,
			Instant detectedAt, String detail
	) {
		return Drift.builder()
				.resourceType(resource.getResourceType())
				.resourceId(resource.getResourceId())
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
	 * MK3-2 (DCM3-2.4) — 강제 적용 오버로드. {@code forced=true} 면 자동 적용 가능 분기 검증 + 전역 OFF
	 * 옵션을 모두 우회한다. softDelete reject 의 saga "위치 정정 후 삭제" 흐름이 사용자 명시 액션이므로
	 * 글로벌 설정과 무관하게 진행되어야 함.
	 */
	@Transactional
	public void apply(Long driftId, boolean forced) {
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new DriftNotFoundException(driftId));
		if (!forced) {
			// MK3 — 자동 적용 허용 분기 4종 + MK3-1 GHOST_DB_ROW 1종.
			// 전역 OFF 옵션 (DCN-NEW11) 활성 시 자동 ON 분기도 거절 — 운영 환경 안전망.
			if (!isAutoApplicable(drift.getKind())) {
				throw new DriftAutoApplyNotAllowedException(drift.getKind());
			}
			if (Boolean.FALSE.equals(autoApplyEnabled)) {
				log.warn("[reconciliation] auto-apply 전역 OFF — drift {} 거절", driftId);
				throw new DriftAutoApplyNotAllowedException(drift.getKind());
			}
		} else {
			log.info("[reconciliation] forced apply — driftId={}, kind={}", driftId, drift.getKind());
		}
		MarkableScanner scanner = scannerFor(drift.getResourceType());
		// MK3-1 — GHOST_DB_ROW 는 newPath 가 null 이고 액션이 row hard-delete 라 별도 분기.
		if (drift.getKind() == DriftKind.GHOST_DB_ROW) {
			scanner.applyGhostClear(drift.getResourceId());
		} else {
			// PATH_DRIFT / RESOURCE_RENAMED 는 newPath 로 갱신. SOFTDEL_ESCAPE_TO_ORIGINAL / TRASH_MARKER_STALE 는
			// applyDriftedPath 와 다른 동작이 필요할 수 있으나 본 sub-slice 단계에서는 PATH_DRIFT 와 동일 호출 (newPath 기반).
			scanner.applyDriftedPath(drift.getResourceId(), Path.of(drift.getNewPath()));
		}
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
	 * MK3 — 자동 적용 가능 DriftKind 인지 판단. {@link DriftKind} 자체에 메서드 두는 대안도 있으나 본체 단계에서는
	 * service 안 명시 분기 — 다른 곳에서 같은 분기가 반복되면 enum method-per-constant 로 승격.
	 * <p>MK3-1 — GHOST_DB_ROW 추가 (drift apply = DB row hard-delete).</p>
	 */
	private boolean isAutoApplicable(DriftKind kind) {
		return kind == DriftKind.PATH_DRIFT
				|| kind == DriftKind.RESOURCE_RENAMED
				|| kind == DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL
				|| kind == DriftKind.TRASH_MARKER_STALE
				|| kind == DriftKind.GHOST_DB_ROW;
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
						d.getId(), d.getResourceType(), d.getResourceId(),
						d.getKind(), d.getOldPath(), d.getNewPath(), d.getDetectedAt(), d.getDetail()
				))
				.collect(Collectors.toList());
		return new DriftReportResponse(
				r.getId(), r.getScannedAt(),
				r.getScanDuration().toString(), r.isDeep(), r.getTotalChecked(),
				r.getDriftCount(), r.getFailedScanRootList(), drifts
		);
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
