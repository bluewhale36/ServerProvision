package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.trash.ResourceKey;
import com.example.serverprovision.provisioning.usage.ResourceUsageLevel;
import com.example.serverprovision.provisioning.usage.ResourceUsageQuery;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanCoverage;
import com.example.serverprovision.maintenance.reconciliation.vo.ScanPopulation;
import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.*;
import com.example.serverprovision.global.marker.exception.MarkerMissingException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftOriginResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftTimelineEntry;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftTimelineResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftReportResponse;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftHandling;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftObservation;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftHandlingAction;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.enums.SnoozeWindow;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftTimelineKind;
import com.example.serverprovision.maintenance.reconciliation.enums.ScanDepth;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftReportNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftSnoozeNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.exception.ReconciliationAlreadyRunningException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftHandlingRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftObservationRepository;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.DriftResolution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
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
 * <p><b>언제 점검할지는 이 클래스가 정하지 않는다</b>(MK4-3-2). 기동 직후 1 회와 주기 도래 판정은
 * {@link ReconciliationScheduler} 가 맡고, 여기는 {@link #triggerScan(ScanDepth)} 라는 문 하나만 연다.
 * 수동 점검도 같은 문으로 들어온다. 트리거가 세 곳에 흩어져 있던 동안 두 주기 스케줄이 같은 순간에
 * 겹쳐 정밀 점검이 버려지던 문제가 오래 드러나지 않았다.</p>
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
			DriftHandlingRepository driftHandlingRepository,
			DriftObservationRepository driftObservationRepository,
			ReconciliationSettingsService settingsService,
			ResourceUsageQuery resourceUsageQuery,
			List<DriftResolution> resolutions,
			@Lazy PathReconciliationService self
	) {
		this.scanners = scanners;
		this.markerService = markerService;
		this.backgroundJobService = backgroundJobService;
		this.driftReportRepository = driftReportRepository;
		this.driftRepository = driftRepository;
		this.driftHandlingRepository = driftHandlingRepository;
		this.driftObservationRepository = driftObservationRepository;
		this.settingsService = settingsService;
		// provisioning 쪽 계약이라 이 방향으로만 의존한다 — 순환 없음(그쪽은 maintenance 를 모른다).
		this.resourceUsageQuery = resourceUsageQuery;
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
	private final DriftHandlingRepository driftHandlingRepository;
	/** MK4-4-2 — 이력 화면이 "이 드리프트가 어느 회차들에 보였는가" 를 되짚는다. */
	private final DriftObservationRepository driftObservationRepository;

	/**
	 * MK4-2 — 자원이 지금 쓰이는 깊이를 묻는 곳. 계약을 provisioning 이 소유하고 여기서 호출한다
	 * (새로 생기는 의존은 {@code maintenance → provisioning} 하나뿐 — 실행 영역은 provisioning 이 대신 본다).
	 */
	private final ResourceUsageQuery resourceUsageQuery;
	private final Map<DriftKind, DriftResolution> resolutions;

	/**
	 * 자기 자신의 Spring proxy 참조. {@code @Async} / {@code @Transactional} 어노테이션은 프록시 경로로
	 * 진입해야 동작하는데 동일 클래스 내부 메서드 호출({@code this.runAsync(...)})은 프록시를 우회한다.
	 * 결과 : 비동기 실행이 안 되어 HTTP 요청 스레드가 deep scan 동안 블록되고, {@code performScan}
	 * 의 트랜잭션 경계도 사라져 DriftReport / Drift 영속화가 단일 트랜잭션 안에서 묶이지 못한다.
	 * {@code @Lazy} 로 자기 참조를 받아 외부 호출 형태로 진입시켜 양쪽 어노테이션을 모두 살린다.
	 */
	private final PathReconciliationService self;

	/**
	 * MK4-3-1 — 점검의 동작을 좌우하는 값들이 사는 곳. 종전에는 {@code @Value} 다섯이 이 클래스에 박혀
	 * 있어 바꾸려면 설정 파일을 고치고 애플리케이션을 다시 띄워야 했다. 이제 운영자가 화면에서 바꾸고,
	 * 여기서는 <b>필요한 순간마다 읽는다</b> — 그래야 저장이 다음 점검부터 바로 효과를 낸다.
	 *
	 * <p>노출 타입이 계약이라 이 클래스는 문자열을 보지 않는다({@code Set<DriftKind>} · {@code List<Path>}).
	 * 저장 형식이 바뀌어도 여기는 흔들리지 않는다.</p>
	 */
	private final ReconciliationSettingsService settingsService;

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

	/** 점검이 지금 돌고 있는가. 심박이 헛돌지 않게 미리 묻는다 — 예외를 흐름 제어로 쓰지 않는다. */
	public boolean isScanRunning() {
		return running.get();
	}

	/**
	 * 점검을 여는 단 하나의 문. 수동 · 기동 직후 · 주기 도래가 모두 여기로 들어온다.
	 * BackgroundJob 등록 후 비동기 실행한다.
	 *
	 * @return BackgroundJob 의 jobId
	 */
	public String triggerScan(ScanDepth depth) {
		if (!running.compareAndSet(false, true)) {
			throw new ReconciliationAlreadyRunningException();
		}
		boolean deep = depth.isDeep();
		String jobId = backgroundJobService.register(
				JobType.PATH_RECONCILIATION,
				depth.getJobTitle(),
				depth.getJobDetail(),
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
					"[reconciliation] 스캔 완료. deep={}, 점검 대상={}(활성 {} · 삭제 {} · 짝없는 마커 {}), drifts={}",
					deep, report.getPopulation().total(), report.getPopulation().getActiveCount(),
					report.getPopulation().getDeletedCount(),
					report.getPopulation().getUnmatchedMarkerCount(), report.getDetectedDriftCount()
			);
			// R9-1 — 완료 시점 결과 수치를 Job 에 탑재. 페이지가 bgjob:completed 토스트 문구에 사용.
			backgroundJobService.complete(jobId, Map.of(
					"driftCount", String.valueOf(report.getDetectedDriftCount())
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
				triggerScan(ScanDepth.QUICK);
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
								.firstDetectedAt(now)
								.lastObservedAt(now)
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
		// S6-2-2 — soft-deleted 매칭 마커는 종전의 침묵 제외(D20) 대신 ESCAPE 로 분류한다.
		// S11-1 — 판정 원칙 : 마커는 신원 증명이고 본체가 존재 증명이다. 본체 없는 마커는 어떤 자원 상태
		// 판정도 대표하지 못하므로 미아 마커(SOFTDEL_MARKER_STRAY) 병행 신호로만 보고하고, 뒤 블록(5.5a)의
		// 상태 판정을 침묵시키지 않는다. 종전에는 마커 한 장이 무조건 escapeReported 에 등록되어 소실 · 유령
		// 판정이 억제되고 거짓 SCAN_UNOBSERVED 해결 이력이 쌓였다(4b 의 본체 검사 B-1 과 같은 원칙 적용).
		// MK4-4-2 — 짝 없는 마커의 수를 여기서 센다. (4) 가 끝나 matchedMarkers 가 확정된 시점이라
		// "활성 자원과 이어지지도, 삭제 기록과 이어지지도 않은 마커" 가 정확히 이 집합이다. 아래 루프의
		// ORPHAN 분기에서 세지 않는 이유는 그 분기가 판정 로직이고 여기는 집계여서다 — 분기에 계산을
		// 얹으면 판정이 바뀔 때마다 집계가 함께 흔들린다.
		int unmatchedMarkerCount = (int) diskMarkers.keySet().stream()
				.filter(k -> !matchedMarkers.contains(k) && !deletedByKey.containsKey(k))
				.count();

		Set<MarkerKey> escapeReported = new HashSet<>();
		for (Map.Entry<MarkerKey, List<MarkerHit>> e : diskMarkers.entrySet()) {
			MarkerKey key = e.getKey();
			if (matchedMarkers.contains(key)) continue;
			// HF4-5 — ORPHAN/ESCAPE 는 종전대로 첫 발견 hit 만 사용 (다중 사본 소비 확대는 scope 밖 — plan §8).
			MarkerHit hit = firstHit(e.getValue());
			if (hit == null) continue;
			Markable deleted = deletedByKey.get(key);
			if (deleted != null) {
				if (!resourceBodyExists(hit.resourcePath(), hit.layout())) {
					drifts.add(buildDrift(
							deleted, DriftKind.SOFTDEL_MARKER_STRAY,
							expectedTrashPath(deleted), hit.resourcePath().toString(), now,
							"삭제 자원의 마커가 본체 없이 발견 — 자원 상태 판정은 별도 문제로 병행 보고"
					));
					continue;
				}
				EscapeVerdict verdict = classifyEscape(deleted, hit, now);
				if (verdict.replacesStateJudgment()) {
					escapeReported.add(key);
				}
				if (verdict.drift() != null) drifts.add(verdict.drift());
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
							   .firstDetectedAt(now)
								.lastObservedAt(now)
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

			// S11-1 — 본체 동반 ESCAPE 판정이 자원 상태를 대체한 경우만 건너뛴다. 미아 마커(본체 없는
			// 마커)는 위 escapeReported 에 등록되지 않으므로 아래 상태 판정이 병행 수행된다.
			if (escapeReported.contains(e.getKey())) continue;
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
				} else {
					// S11-1 — soft-delete 기록 불변식 위반(trashedAt 만 있고 trashedPath 부재). markTrashed 가
					// 둘을 함께 쓰므로 정상 경로에서는 나올 수 없는 상태다. 종전에는 이 칸이 침묵이었는데,
					// 실물 위치를 알 수 없다는 점에서 소실과 같으므로 보수적으로 TRASH_LOST 로 보고한다.
					// oldPath 앵커는 기록이 없어 DB 원위치가 유일하다 (drift.old_path not-null 제약).
					log.warn("[reconciliation] soft-delete 기록 불변식 위반 — trashedAt 존재 + trashedPath 부재. {}#{}",
							deleted.getResourceType(), deleted.getResourceId());
					drifts.add(buildDrift(
							deleted, DriftKind.TRASH_LOST,
							deleted.getResourcePath().toString(), null, now,
							"휴지통 기록 불변식 위반(이동 시각만 존재, 보관 경로 없음) — 실물 위치 불명, 소실로 보수 판정"
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

		// (6) DriftReport 영속화 + 문제 잇기
		backgroundJobService.startStage(jobId, ReconciliationStage.PERSISTING);
		long durationMs = Duration.between(start, Instant.now()).toMillis();
		DriftReport report = DriftReport.builder()
				.scannedAt(start)
				.scanDurationMs(durationMs)
				.deep(deep)
				// MK4-4-2 — 본 것을 전부 센다. 종전에는 활성 자원만 세면서 삭제 자원 · 짝 없는 마커에서
				// 나온 드리프트를 목록에 실어, 점검 대상보다 문제가 많아 보이는 화면이 됐다.
				.population(ScanPopulation.of(
						activeInventory.size(), deletedByKey.size(), unmatchedMarkerCount))
				.build();
		report.recordFailedScanRoots(failedScanRoots);
		// MK4-4-2 — 실패한 범위만이 아니라 뒤진 범위도 남긴다. 회차 상세가 "이 점검이 무엇을 했는가" 에
		// 답하려면 성공한 범위를 알아야 하는데 종전에는 어디에도 기록되지 않았다.
		report.recordScannedRoots(scanRoots.stream().map(Path::toString).sorted().toList());
		DriftReport saved = driftReportRepository.save(report);

		// MK4-1 — 이번 회차에 발견된 것들을 지속되는 문제에 잇는다. 같은 신원의 열린 문제가 이미
		// 있으면 그것을 쓰고(관측 갱신), 없으면 새로 만든다. 어느 쪽이든 관측 1건이 보고서에 쌓인다.
		List<Drift> observed = linkObservations(drifts, saved, now, deep);

		// MK4-1 — 이번 점검이 커버한 종류인데 더 이상 보이지 않는 문제는 해소로 닫는다.
		// 운영자가 파일을 직접 되돌려 놓은 경우가 대표적이다. 커버 범위를 종류가 스스로 알기 때문에
		// (DriftKind.coveredBy) 여기에 종류별 분기가 생기지 않는다 — 일반 점검이 내용 변경 문제를
		// 닫아 버리면 정밀 점검마다 되살아나기를 반복하게 된다.
		closeUnobserved(observed, now, deep);

		// MK4-1 — 두고 보기 만료분 복귀.
		reopenExpiredSnoozes(now);

		// (7) FIFO prune (D15)
		pruneOldReports();

		// (8) 무인 자동 적용 — 해결 등급이 AUTO 이고 운영 설정에서 켜 둔 종류만. 종류별 분기 대신
		// 전략 bean 디스패치로 처리하므로 종류가 늘어도 여기 줄이 늘지 않는다.
		if (!isResolutionEnabled()) {
			// 전역 OFF — 자동 적용 건너뜀.
		} else {
			Set<DriftKind> enabledKinds = autoApplyKinds();
			for (Drift d : observed) {
				if (!d.getKind().isAutoApplicable() || !enabledKinds.contains(d.getKind())) continue;
				DriftResolution resolution = resolutions.get(d.getKind());
				if (resolution == null) continue; // 해결 미구현 AUTO kind — 스캔을 죽이지 않고 skip
				try {
					String previousPath = d.getOldPath();
					String movedToPath = resolution.resolve(d, scannersByType.get(d.getResourceType()))
							.map(Path::toString)
							.orElse(d.getNewPath());
					// MK4-3-1 — 처리했으면 그 자리에서 닫는다.
					//
					// 종전에는 고쳐 놓고도 drift 를 열어 둔 채 두었다("보고서에 남긴다"). 드리프트가
					// 회차마다 새로 생기던 시절에는 그것이 곧 기록 보존이었지만, MK4-1 이 드리프트를
					// 지속되는 문제로 바꾼 뒤로는 <b>이미 고친 문제가 계속 조치 필요로 남는다</b>.
					// 상세의 'DB 기록' 도 갱신 전 경로를 보여 주어 실제 데이터와 어긋난다.
					// 다음 점검에서 미관측으로 닫히기는 하나, 그러면 원장에 "시스템이 처리했다" 가
					// 아니라 "그냥 사라졌다" 로 적힌다 — 감사 기록이 사실과 달라진다.
					// 수동 해결과 같은 방식으로 닫아 두 경로가 같은 원장을 남기게 한다.
					Instant handledAt = Instant.now();
					d.resolve(handledAt, DriftHandlingAction.AUTO_APPLY);
					driftHandlingRepository.save(DriftHandling.of(
							d, DriftHandlingAction.AUTO_APPLY, handledAt, previousPath, movedToPath, null));
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
		// 추가 점검 경로 — 운영 설정에서 온다. 서비스가 이미 Path 로 돌려주므로 여기서 파싱하지 않는다.
		roots.addAll(settingsService.extraScanRoots());
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
					resource, DriftKind.RESOURCE_REPLICA, expectedPath.toString(),
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
	 * S11-1 — 본체 동반 ESCAPE 판정의 결과. {@code replacesStateJudgment} 가 true 면 이 판정이
	 * 뒤 블록(5.5a)의 자원 상태 판정을 대체한다({@code escapeReported} 등록). 대체가 정당한 것은
	 * 두 경우뿐이다 — 원위치 복귀(뒤 블록과 같은 판정의 중복 방지)와, 원위치 본체가 없는 상태의
	 * 이탈(빈 휴지통 · 유령 기록을 탈출이 설명). 원위치에 본체가 있는 타위치 이탈은 복귀 판정과
	 * 병행 보고되어야 하므로 대체하지 않는다.
	 */
	private record EscapeVerdict(Drift drift, boolean replacesStateJudgment) {}

	/**
	 * S6-2-2 → S11-1 — 삭제 자원의 마커가 <b>본체와 함께</b> active 트리에서 발견됐을 때의 분류.
	 * 발견 위치가 원위치면 "복귀"(자동 복원 가능), 아니면 "이탈"(사용자 확인 후 회수).
	 * 본체 없는 마커는 이 메서드에 오지 않는다 — 호출부가 미아 마커(SOFTDEL_MARKER_STRAY)로
	 * 분리해 병행 보고한다(종전 catch-all 이 그 상태까지 이탈로 흡수하던 결함의 제거).
	 * 원위치에도 파일이 있는 모호 상태는 이탈 detail 에 병기 — 어느 쪽이 진짜인지
	 * 시스템이 판정하지 않고 복원 시점 게이트(manifestHash 검증)에 맡긴다.
	 */
	private EscapeVerdict classifyEscape(Markable deleted, MarkerHit hit, Instant now) {
		Path found = hit.resourcePath().toAbsolutePath().normalize();
		Path expected = deleted.getResourcePath().toAbsolutePath().normalize();
		boolean bodyAtOriginal = resourceBodyExists(deleted.getResourcePath(), deleted.getMarkerLayout());
		String trashedPath = (deleted instanceof LifecycleEntity lifecycle) ? lifecycle.getTrashedPath() : null;
		boolean trashCopyAlive = trashedPath != null && Files.exists(Path.of(trashedPath));
		String oldPath = expectedTrashPath(deleted);
		if (found.equals(expected)) {
			// 발견 위치 = 원위치이고 본체 실재(호출부 보장) — bodyAtOriginal 과 같은 사실이다.
			if (trashCopyAlive) {
				// 점유 상태(원O·trashO) — 마커까지 복귀했어도 drift 로 보고하지 않는다(5.5a 와 동일 결정).
				// 보고하면 [적용]이 항상 409(RestorePathOccupied)로 끝나는 버튼이 노출된다.
				// 그 파일의 진위·처리는 복원 시점 게이트가 SSOT (적대적 검증 반영). 5.5a 도 이 상태를
				// 보고하지 않으므로 대체 등록은 무해하다.
				return new EscapeVerdict(null, true);
			}
			return new EscapeVerdict(buildDrift(deleted, DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL,
					oldPath, deleted.getResourcePath().toString(), now,
					"삭제 자원의 마커와 본체가 원래 위치에서 발견 — 외부 복귀로 판단"), true);
		}
		String detail = "삭제 자원의 본체와 마커가 다른 위치에서 발견"
				+ (bodyAtOriginal ? " — 원위치에도 파일 존재 (진위는 복원 시점 검증)" : "")
				+ (trashCopyAlive ? " — 정본이 휴지통에 보관되어 있음. 회수 대신 발견물 정리, 또는 휴지통 정리 후 회수 중 택일 필요" : "");
		return new EscapeVerdict(buildDrift(deleted, DriftKind.SOFTDEL_ESCAPE_TO_OTHER,
				oldPath, hit.resourcePath().toString(), now, detail), !bodyAtOriginal);
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

	/**
	 * MK4-1 — 이번 회차에 발견된 후보들을 지속되는 문제에 잇는다.
	 *
	 * <p>후보는 아직 저장되지 않은 값이다. 같은 신원(자원 종류 · 자원 번호 · 종류)의 닫히지 않은
	 * 문제가 이미 있으면 그 문제가 이번에도 보인 것이므로 관측만 갱신하고, 없으면 새 문제로 저장한다.
	 * 어느 쪽이든 회차별 사실은 관측 1건으로 남는다.</p>
	 *
	 * @return 이번 회차에 관측된 문제들(영속 상태). 무인 자동 적용이 이 목록을 쓴다.
	 */
	private List<Drift> linkObservations(List<Drift> candidates, DriftReport report, Instant now, boolean deep) {
		List<Drift> observed = new ArrayList<>();
		for (Drift candidate : candidates) {
			Drift problem = driftRepository
					.findFirstByResourceTypeAndResourceIdAndKindAndStatusNot(
							candidate.getResourceType(), candidate.getResourceId(),
							candidate.getKind(), DriftStatus.RESOLVED)
					.orElse(null);
			if (problem == null) {
				problem = driftRepository.save(candidate);
			} else {
				problem.observe(now, candidate.getOldPath(), candidate.getNewPath(),
						candidate.getDetail(), candidate.getObservedHash(), candidate.getDisplayName(), deep);
			}
			report.addObservation(DriftObservation.builder()
					.drift(problem)
					.report(report)
					.observedAt(now)
					.oldPath(candidate.getOldPath())
					.newPath(candidate.getNewPath())
					.detail(candidate.getDetail())
					.observedHash(candidate.getObservedHash())
					.build());
			observed.add(problem);
		}
		driftReportRepository.save(report);
		return observed;
	}

	/**
	 * MK4-1 — 이번 점검이 커버한 종류인데 관측되지 않은 문제를 해소로 닫는다.
	 *
	 * <p>커버 판정을 종류가 스스로 하므로({@code DriftKind.coveredBy}) 여기에 종류별 분기가 없다.
	 * 이 규칙이 없으면 일반 점검이 내용 변경 문제를 "안 보였다" 는 이유로 닫고, 정밀 점검이 다시
	 * 열기를 반복한다.</p>
	 *
	 * <p>S11-2 — 재분류 승계. 같은 자원의 문제가 닫히는 회차에 새 종류의 문제가 함께 열렸다면 이는
	 * 같은 사건이 다른 이름으로 이어진 것이다 — 후임이 전임을 {@code Drift.predecessor} 로 가리키고,
	 * 전임은 {@code SCAN_UNOBSERVED} 대신 {@code SUPERSEDED}(재분류로 이어짐)로 닫아 이력이 사실을
	 * 말하게 한다. 같은 신원의 열린 문제는 {@code linkObservations} 가 재사용하므로 신규 문제의 종류는
	 * 닫히는 전임들과 구조적으로 다르다("다른 종류" 조건이 별도 검사 없이 성립).</p>
	 */
	private void closeUnobserved(List<Drift> observed, Instant now, boolean deep) {
		Set<Long> observedIds = observed.stream().map(Drift::getId).collect(Collectors.toSet());
		// S11-2 — 이번 회차 신규 문제(잠재 후임)를 자원 신원별로 묶는다.
		Map<MarkerKey, List<Drift>> successorsByResource = observed.stream()
				.filter(d -> now.equals(d.getFirstDetectedAt()))
				.collect(Collectors.groupingBy(d -> new MarkerKey(d.getResourceType(), d.getResourceId())));
		// S11-2 — 닫힘 후보를 자원별로 모아 전임 채택을 결정적으로 한다(D-2 — 처리 순서 비의존).
		Map<MarkerKey, List<Drift>> closingByResource = new LinkedHashMap<>();
		for (Drift open : driftRepository.findByStatusNot(DriftStatus.RESOLVED)) {
			if (observedIds.contains(open.getId())) continue;
			if (!open.getKind().coveredBy(deep)) continue;
			closingByResource
					.computeIfAbsent(new MarkerKey(open.getResourceType(), open.getResourceId()),
							k -> new ArrayList<>())
					.add(open);
		}
		for (Map.Entry<MarkerKey, List<Drift>> e : closingByResource.entrySet()) {
			List<Drift> closing = e.getValue();
			List<Drift> successors = successorsByResource.getOrDefault(e.getKey(), List.of());
			// D-2 — 전임은 lastObservedAt 최신, 동률이면 식별자 오름차순(= 먼저 열린 전임, Q1 확정).
			// 같은 회차에 함께 닫히는 전임들은 직전 회차에 함께 관측된 경우가 흔해 1차 키 동률이 잦다.
			Drift predecessor = successors.isEmpty() ? null : closing.stream()
					.min(Comparator.comparing(Drift::getLastObservedAt).reversed()
							.thenComparing(Drift::getId, Comparator.nullsLast(Comparator.naturalOrder())))
					.orElse(null);
			for (Drift open : closing) {
				DriftHandlingAction action;
				if (open == predecessor) {
					// D-1 — 후임 다수(fan-out)는 전원이 같은 전임을 가리킨다. 링크는 최초 1회 고정.
					successors.forEach(s -> s.linkPredecessor(open));
					action = DriftHandlingAction.SUPERSEDED;
				} else {
					action = DriftHandlingAction.SCAN_UNOBSERVED;
				}
				open.resolve(now, action);
				driftHandlingRepository.save(DriftHandling.of(open, action, now, null, null, null));
			}
		}
	}

	/**
	 * MK4-1 — 두고 보기 기간이 지난 문제를 다시 연다. 조건형('다음 정밀 점검까지')은 시각이 아니라
	 * 관측 사건으로 풀리므로 {@code Drift.observe} 가 처리한다.
	 */
	private void reopenExpiredSnoozes(Instant now) {
		for (Drift snoozed : driftRepository.findByStatusNot(DriftStatus.RESOLVED)) {
			if (!snoozed.isSnoozeExpired(now)) continue;
			snoozed.reopen();
			driftHandlingRepository.save(DriftHandling.of(
					snoozed, DriftHandlingAction.UNSNOOZE, now, null, null, "보관 기간 만료"));
		}
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
				.firstDetectedAt(detectedAt)
				.lastObservedAt(detectedAt)
				.detail(detail)
				.build();
	}

	private void pruneOldReports() {
		long total = driftReportRepository.count();
		long over = total - settingsService.reportRetentionCount();
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

	/**
	 * MK4-4-2 — 회차 하나. 이력 상세 화면이 쓴다.
	 *
	 * <p>목록이 이미 실어 온 값을 화면이 골라 쓰게 하지 않고 다시 조회하는 이유는, 상세가 목록의
	 * 페이지에 실린 회차만 열 수 있게 되는 것을 피하기 위해서다 — 2 페이지의 회차를 열었다가
	 * 돌아오면 그 회차가 사라지는 식의 결합이 생긴다.</p>
	 */
	@Transactional(readOnly = true)
	public DriftReportResponse report(Long reportId) {
		return driftReportRepository.findById(reportId)
				.map(this::toResponse)
				.orElseThrow(() -> new DriftReportNotFoundException(reportId));
	}

	/**
	 * MK4-4-2 — 문제 하나. 드리프트 상세 화면이 쓴다. 사용 깊이까지 실어 배지 근거를 갖춘다.
	 */
	@Transactional(readOnly = true)
	public DriftResponse drift(Long driftId) {
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new DriftNotFoundException(driftId));
		return toDriftResponse(drift, usageFor(drift, usageOf(List.of(drift))),
				predecessorsOf(List.of(drift)));
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
			// MK4-1 — 이미 닫힌 문제는 다시 해결할 것이 없다. 화면이 버튼을 비활성으로 1차 차단하고
			// 이 가드는 direct POST · stale 화면에서만 발동한다 — 차단 사유의 단일 소스는
			// Drift.resolveBlockReason() 이며 화면의 tooltip 도 그 값을 그대로 쓴다.
			String blockReason = drift.resolveBlockReason();
			if (blockReason != null) {
				throw DriftResolutionNotAllowedException.of(blockReason);
			}
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
		String previousPath = drift.getOldPath();
		// MK4-1 — 옮겨 둔 위치를 아는 것은 전략뿐이다(격리 구역 · 휴지통 경로는 실행 중에 계산된다).
		// 옮긴 것이 없다고 답하면 drift 가 들고 있던 새 경로가 그 자리를 대신한다 — 경로 이동됨처럼
		// 도착지가 이미 적혀 있는 종류가 그렇다. 종류별 분기는 어디에도 생기지 않는다.
		String movedToPath = resolution.resolve(drift, scanner)
				.map(Path::toString)
				.orElse(drift.getNewPath());
		// MK4-1 — 보고서에서 떼어내는 대신 문제를 닫는다. 지난 보고서의 관측은 그대로 남는다.
		Instant handledAt = Instant.now();
		drift.resolve(handledAt, DriftHandlingAction.APPLY);
		driftHandlingRepository.save(DriftHandling.of(
				drift, DriftHandlingAction.APPLY, handledAt, previousPath, movedToPath, null));
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
		roots.addAll(settingsService.extraScanRoots());
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
				// saga 의 일시 보고서 — 자원 하나만 보고 만든 것이라 활성 1 건이 전부다.
				.population(ScanPopulation.of(1, 0, 0))
				.build();
		Drift persisted = driftRepository.save(drift);
		tempReport.addObservation(DriftObservation.builder()
				.drift(persisted)
				.report(tempReport)
				.observedAt(persisted.getFirstDetectedAt())
				.oldPath(persisted.getOldPath())
				.newPath(persisted.getNewPath())
				.detail(persisted.getDetail())
				.observedHash(persisted.getObservedHash())
				.build());
		driftReportRepository.save(tempReport);
		apply(persisted.getId(), true);
	}

	/**
	 * R9-2 → S6-2-1 개명 — 시스템 해결 전면 활성 여부. 서버 가드({@link #apply(Long, boolean)})와 페이지 뷰모델
	 * (버튼 disabled+tooltip)이 이 한 메서드를 공유하는 SSOT — 두 곳에 조건을 복붙하면 drift 가 생긴다
	 * ({@code childEnableBlockReason()} 선례). {@code Boolean} wrapper 라 null(=미주입) 은 활성으로 본다.
	 * <p>허용 종류 판단은 {@link DriftKind#isManuallyResolvable()} — 이 메서드는 전역 축만 담당.</p>
	 */
	/**
	 * MK4-1 — 지금 목록에 떠야 하는 문제의 수. 종전에는 보고서의 자식 수를 셌는데 그 값은 해소할
	 * 때마다 줄어 지난 기록을 왜곡했다. 미해결 수는 회차가 아니라 현재 상태의 값이다.
	 */
	@Transactional(readOnly = true)
	public long openDriftCount() {
		Instant now = Instant.now();
		return driftRepository.findByStatusNot(DriftStatus.RESOLVED).stream()
				.filter(d -> d.isListed(now))
				.count();
	}

	/**
	 * MK4-1 — 지금 목록에 떠야 하는 문제들. 화면이 회차와 무관하게 "현재 남은 것" 을 보여준다.
	 */
	@Transactional(readOnly = true)
	public List<DriftResponse> openDrifts() {
		Instant now = Instant.now();
		List<Drift> open = driftRepository.findByStatusNot(DriftStatus.RESOLVED).stream()
				.filter(d -> d.isListed(now))
				.toList();
		// MK4-2 — 종전에는 최초 발견 순이었다. 이제 급한 순이며, 최초 발견은 동률을 깨는 마지막 기준이다.
		Map<ResourceKey, ResourceUsageLevel> usage = usageOf(open);
		// MK4-4-2 — 계보의 직전 한 마디를 함께 싣는다. 전임은 대개 이미 닫혀 위 조회에 없으므로
		// 식별자를 모아 한 번 더 읽는다(행마다 지연 로딩을 타면 조회가 행 수만큼 늘어난다).
		Map<Long, Drift> predecessors = predecessorsOf(open);
		return open.stream()
				.map(d -> toDriftResponse(d, usageFor(d, usage), predecessors))
				.sorted(Comparator.comparing(DriftResponse::priority))
				.collect(Collectors.toList());
	}

	/**
	 * MK4-2 — 이번 점검이 무엇까지 보았는가. 일반 점검은 파일 내용을 보지 않으므로, 내용에 관한
	 * 문제가 목록에서 빠진 것이 해결됐기 때문인지 보지 않았기 때문인지를 화면이 구분할 수 있어야 한다.
	 */
	@Transactional(readOnly = true)
	public ScanCoverage scanCoverage() {
		Instant lastDeep = driftReportRepository.findFirstByDeepTrueOrderByScannedAtDesc()
				.map(DriftReport::getScannedAt)
				.orElse(null);
		boolean latestWasDeep = driftReportRepository.findFirstByOrderByScannedAtDesc()
				.map(DriftReport::isDeep)
				.orElse(false);
		return new ScanCoverage(latestWasDeep, lastDeep);
	}

	public boolean isResolutionEnabled() {
		return settingsService.isResolutionEnabled();
	}

	/**
	 * MK4-3-1 — 무인 자동 적용을 맡길 종류. 운영 설정에서 매 점검마다 읽으므로 저장이 다음 점검부터
	 * 곧바로 효과를 낸다.
	 *
	 * <p>종전에는 설정 문자열을 여기서 파싱하며 알 수 없는 이름에 예외를 던져 점검을 실패시켰다.
	 * 이제 이름을 고르는 것은 화면이고 저장 시점에 이미 검증되므로, 읽는 쪽은 알아본 종류만 받는다.
	 * 코드에서 사라진 종류가 설정에 남은 경우는 설정 화면이 "알 수 없는 항목" 으로 드러낸다 —
	 * 점검을 죽이는 대신 고칠 자리에서 보이게 하는 편이 낫다.</p>
	 */
	private Set<DriftKind> autoApplyKinds() {
		return settingsService.autoApplyKinds();
	}

	/**
	 * MK4-1 — 종전 '보고 닫기' 를 대신하는 '지금은 두고 보기'.
	 *
	 * <p>종전에는 보고서에서 행을 떼어냈고 다음 점검이 같은 문제를 다시 만들어 착시를 낳았다.
	 * 이제는 기간(또는 조건)과 사유를 받아 그동안만 목록에서 내린다 — 운영자가 알면서 미룬 사실이
	 * 기록으로 남아, 나중에 왜 방치됐는지 설명이 된다.</p>
	 */
	@Transactional
	public void snooze(Long driftId, SnoozeWindow window, String reason) {
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new DriftNotFoundException(driftId));
		// UI 가 버튼 비활성으로 1차 차단하므로 이 가드는 direct POST · stale 화면에서만 발동한다.
		String blockReason = drift.snoozeBlockReason();
		if (blockReason != null) {
			throw DriftSnoozeNotAllowedException.of(blockReason);
		}
		Instant now = Instant.now();
		drift.snooze(window, reason, now);
		driftHandlingRepository.save(DriftHandling.of(
				drift, DriftHandlingAction.SNOOZE, now, null, null, reason));
	}

	private MarkableScanner scannerFor(ResourceType type) {
		return scanners.stream()
				.filter(s -> s.supportedType() == type)
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("No MarkableScanner for type: " + type));
	}

	// ==== 매핑 ========================================================

	/**
	 * MK4-1 — 문제 하나를 화면 값으로. 최신 스냅샷 · 수명 · 상태를 함께 싣는다.
	 */
	static DriftResponse toDriftResponse(Drift d) {
		return toDriftResponse(d, null, Map.of());
	}

	/**
	 * MK4-2 — 사용 깊이를 함께 실어 매핑한다. {@code usage} 가 {@code null} 이면 계산하지 않은 응답이며,
	 * 화면은 사용 중 배지를 띄우지 않는다.
	 *
	 * <p>MK4-4-2 — 계보의 직전 한 마디를 함께 싣는다. {@code loadedPredecessors} 는 미리 한 번에
	 * 읽어 둔 전임들이다 — 여기서 지연 로딩 연관을 그대로 타면 목록의 행 수만큼 조회가 늘어난다
	 * (사용 깊이를 일괄로 읽는 {@code usageOf} 와 같은 이유).</p>
	 */
	static DriftResponse toDriftResponse(Drift d, ResourceUsageLevel usage,
			Map<Long, Drift> loadedPredecessors) {
		return new DriftResponse(
				d.getId(), d.getResourceType(), d.getResourceId(), d.getDisplayName(),
				d.getKind(), d.getOldPath(), d.getNewPath(),
				d.getFirstDetectedAt(), d.getLastObservedAt(), d.getObservationCount(),
				d.getStatus(), d.getSnoozeUntil(), d.getSnoozeReason(),
				// 화면의 버튼 비활성 사유와 서버 가드가 같은 도메인 메서드를 본다 (UI 1차 차단의 단일 소스).
				d.snoozeBlockReason(), d.resolveBlockReason(), d.getDetail(), usage,
				originOf(d, loadedPredecessors));
	}

	/**
	 * MK4-4-2 — 전임 한 마디를 응답 값으로. 미리 읽어 둔 것에 없으면 비운다 — 없는 것을 여기서
	 * 조회하면 일괄로 읽은 의미가 사라진다.
	 */
	private static DriftOriginResponse originOf(Drift d, Map<Long, Drift> loadedPredecessors) {
		Long predecessorId = predecessorIdOf(d);
		if (predecessorId == null) return null;
		Drift predecessor = loadedPredecessors.get(predecessorId);
		if (predecessor == null) return null;
		return new DriftOriginResponse(predecessor.getId(), predecessor.getKind(),
				predecessor.getFirstDetectedAt(), predecessor.getResolvedAt());
	}

	/**
	 * 지연 로딩 연관에서 <b>식별자만</b> 꺼낸다. 식별자는 프록시가 이미 들고 있어 이 접근만으로는
	 * 조회가 일어나지 않는다 — 종류나 시각을 읽는 순간 비로소 조회된다.
	 */
	private static Long predecessorIdOf(Drift d) {
		Drift predecessor = d.getPredecessor();
		return predecessor == null ? null : predecessor.getId();
	}

	/**
	 * MK4-4-2 — 여러 문제의 전임을 한 번에 읽는다. 전임은 대개 이미 닫힌 문제라 목록 조회 결과
	 * 안에 없으므로, 식별자를 모아 별도로 한 번 읽는다.
	 */
	private Map<Long, Drift> predecessorsOf(Collection<Drift> drifts) {
		Set<Long> ids = drifts.stream()
				.map(PathReconciliationService::predecessorIdOf)
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		if (ids.isEmpty()) return Map.of();
		return driftRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Drift::getId, d -> d));
	}


	/** 사슬이 길어져도 화면이 감당할 만한 깊이. 넘으면 거기서 끊고 더 있다는 사실만 알린다. */
	private static final int MAX_LINEAGE_DEPTH = 20;

	/** 이력 화면이 한 번에 보이는 줄 수. 나머지는 감추되 몇 건인지는 밝힌다. */
	private static final int TIMELINE_PAGE_SIZE = 20;

	/**
	 * MK4-4-2 — 이 드리프트에 무슨 일이 있었나. 관측 · 처리 · 이어짐 · 이어 줌을 <b>한 시간축</b>에
	 * 놓는다.
	 *
	 * <p>넷을 나눠 늘어놓지 않는 이유는 실제로 번갈아 일어나기 때문이다. 계보를 별도 구획으로
	 * 두었더니 두 구획이 서로 무슨 관계인지 알 수 없다는 지적을 받았고, 시간축이 하나면 그 관계가
	 * 자리로 드러난다.</p>
	 *
	 * <p><b>뒤로 이어 준 것까지 싣는다.</b> 계보 링크가 후임 → 전임 단방향이라 닫힌 드리프트를
	 * 열면 과거만 보였고, 그것이 정말 끝난 것인지 뒤에 더 생긴 문제가 방치된 것인지 구분할 방법이
	 * 없었다(사용자 지적). 이제 사슬을 양쪽으로 펼쳐 놓고, 아직 열려 있는 자리는 그래프의 색이
	 * 드러낸다.</p>
	 *
	 * <p>관측은 점검마다 쌓이므로 최근 것만 싣고 감춘 수를 함께 돌려준다. <b>사슬은 감추지
	 * 않는다</b> — 어디서 시작해 어디로 이어졌는지는 건수와 무관하게 알아야 하는 사실이다.</p>
	 */
	@Transactional(readOnly = true)
	public DriftTimelineResponse timelineOf(Long driftId) {
		Drift drift = driftRepository.findById(driftId)
				.orElseThrow(() -> new DriftNotFoundException(driftId));
		boolean selfResolved = drift.getStatus() == DriftStatus.RESOLVED;

		List<DriftTimelineEntry> own = new ArrayList<>();
		for (DriftObservation observation :
				driftObservationRepository.findByDriftWithReportOrderByObservedAtDesc(drift)) {
			DriftReport report = observation.getReport();
			own.add(new DriftTimelineEntry(
					observation.getObservedAt(), DriftTimelineKind.OBSERVATION,
					report.isDeep() ? "정밀 점검에서 관측" : "일반 점검에서 관측",
					report.getId(), null, false, selfResolved,
					observation.getOldPath(), observation.getNewPath()));
		}
		for (DriftHandling handling : driftHandlingRepository.findByDriftOrderByHandledAtDesc(drift)) {
			own.add(new DriftTimelineEntry(
					handling.getHandledAt(), DriftTimelineKind.HANDLING,
					// 문구의 단일 소스는 DriftHandlingAction 이다 — 여기서 조립하면 같은 처리가
					// 화면마다 다르게 불리게 된다.
					handling.getAction().getLabel(),
					null, null, false, selfResolved,
					handling.getPreviousPath(), handling.getMovedToPath()));
		}
		// 최근 것이 위로. 같은 시각이면 처리가 관측보다 뒤이므로 회차 없는 쪽을 앞에 둔다.
		own.sort(Comparator.comparing(DriftTimelineEntry::at).reversed()
				.thenComparing(e -> e.reportId() == null ? 0 : 1));
		// 사슬 안에서 "지금 여기" 를 가리키는 표식은 하나여야 한다. 자기 줄 전체를 그렇게 두었더니
		// 관측이 쌓인 드리프트에서 화면이 온통 마름모가 되어 표식이 아무것도 가리키지 않게 됐다.
		if (!own.isEmpty()) {
			own.set(0, own.get(0).asCurrent());
		}

		List<DriftTimelineEntry> entries = new ArrayList<>(successorEntries(drift));
		entries.addAll(own.stream().limit(TIMELINE_PAGE_SIZE).toList());
		int hidden = own.size() - Math.min(own.size(), TIMELINE_PAGE_SIZE);
		entries.addAll(predecessorEntries(drift));
		return new DriftTimelineResponse(List.copyOf(entries), hidden);
	}

	/**
	 * 과거 쪽 사슬 — 이 드리프트가 이어받은 것들. 앞선 드리프트가 <b>닫힌 때</b>가 곧 이 드리프트로
	 * 이어진 때다. 가까운 전임부터 담으므로 시간 역순 목록에 그대로 이어 붙는다.
	 */
	private List<DriftTimelineEntry> predecessorEntries(Drift drift) {
		List<DriftTimelineEntry> chain = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		seen.add(drift.getId());
		for (Drift cur = drift.getPredecessor();
			 cur != null && seen.add(cur.getId()) && chain.size() < MAX_LINEAGE_DEPTH;
			 cur = cur.getPredecessor()) {
			chain.add(successionEntry(cur, DriftTimelineKind.SUCCESSION,
					"전임 · " + cur.getKind().getLabel(),
					cur.getResolvedAt() != null ? cur.getResolvedAt() : cur.getLastObservedAt()));
		}
		return chain;
	}

	/**
	 * 이후 쪽 사슬 — 이 드리프트가 닫히며 이어 준 것들. 후임이 <b>처음 감지된 때</b>가 이어 준 때다.
	 *
	 * <p>하나가 닫히며 여러 종류가 함께 드러날 수 있어(fan-out) 너비 우선으로 훑는다. 최근 것이
	 * 위로 오도록 시간 역순으로 정렬해 돌려준다.</p>
	 */
	private List<DriftTimelineEntry> successorEntries(Drift drift) {
		List<DriftTimelineEntry> chain = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		seen.add(drift.getId());
		Deque<Drift> queue = new ArrayDeque<>(driftRepository.findByPredecessor(drift));
		while (!queue.isEmpty() && chain.size() < MAX_LINEAGE_DEPTH) {
			Drift cur = queue.poll();
			if (!seen.add(cur.getId())) continue;
			chain.add(successionEntry(cur, DriftTimelineKind.SUCCEEDED_BY,
					"후임 · " + cur.getKind().getLabel(),
					cur.getFirstDetectedAt()));
			queue.addAll(driftRepository.findByPredecessor(cur));
		}
		chain.sort(Comparator.comparing(DriftTimelineEntry::at).reversed());
		return chain;
	}

	/**
	 * 사슬의 한 마디를 시간축의 줄로. 색은 그 드리프트가 닫혔는지에서 온다.
	 *
	 * <p><b>경로를 싣지 않는다.</b> 사슬은 같은 자원을 두고 이어지므로 상대의 경로가 이 드리프트의
	 * 것과 거의 같아 되풀이일 뿐이고, 그것을 지워야 경로 열의 뜻이 "이 드리프트가 그때 어디에
	 * 있었나" 하나로 정리된다. 종전에는 한 열이 세 가지(그때 본 위치 · 옮긴 자취 · 상대의 대상
	 * 파일)를 겸해, 어느 줄의 경로가 무엇을 말하는지 읽는 사람이 매번 되짚어야 했다.</p>
	 *
	 * <p>문구를 「종류」 만으로 쓰지 않는 이유는 사슬에 같은 종류가 두 번 나올 수 있어서다 —
	 * 실제로 자원 중복 존재가 사슬의 앞뒤에 모두 있어 어미로만 갈리던 상태였다. 방향을 앞세우고
	 * 번호를 옆에 두면 이름이 같아도 갈린다.</p>
	 */
	private static DriftTimelineEntry successionEntry(
			Drift other, DriftTimelineKind kind, String label, Instant at) {
		return new DriftTimelineEntry(
				at, kind, label, null, other.getId(),
				false, other.getStatus() == DriftStatus.RESOLVED,
				null, null);
	}

	/** MK4-2 — 드리프트들이 가리키는 자원의 사용 깊이를 한 번에 조회한다(자원마다 부르면 N+1). */
	private Map<ResourceKey, ResourceUsageLevel> usageOf(Collection<Drift> drifts) {
		Set<ResourceKey> keys = drifts.stream()
				.filter(d -> d.getResourceId() != null)
				.map(d -> new ResourceKey(d.getResourceType(), d.getResourceId()))
				.collect(Collectors.toSet());
		return resourceUsageQuery.levelsOf(keys);
	}

	private static ResourceUsageLevel usageFor(Drift d, Map<ResourceKey, ResourceUsageLevel> usage) {
		if (d.getResourceId() == null) return ResourceUsageLevel.NONE;
		return usage.getOrDefault(
				new ResourceKey(d.getResourceType(), d.getResourceId()), ResourceUsageLevel.NONE);
	}

	private DriftReportResponse toResponse(DriftReport r) {
		// MK4-1 — 보고서가 담는 것은 관측이다. 화면에 보여줄 값은 문제 쪽(최신 스냅샷 · 수명 · 상태)에서
		// 가져오고, 회차 순서만 관측 시각으로 잡는다.
		List<Drift> observed = r.getObservations().stream()
				.sorted(Comparator.comparing(DriftObservation::getObservedAt))
				.map(DriftObservation::getDrift)
				.collect(Collectors.toList());
		// MK4-2 — 목록의 순서는 급한 순이다. 정렬 기준은 DriftPriority 한 곳에만 있다(사전식).
		Map<ResourceKey, ResourceUsageLevel> usage = usageOf(observed);
		Map<Long, Drift> predecessors = predecessorsOf(observed);
		List<DriftResponse> drifts = observed.stream()
				.map(d -> toDriftResponse(d, usageFor(d, usage), predecessors))
				.sorted(Comparator.comparing(DriftResponse::priority))
				.collect(Collectors.toList());
		return new DriftReportResponse(
				r.getId(), r.getScannedAt(),
				formatDuration(r.getScanDuration()), r.isDeep(), r.getPopulation(),
				// MK4-1 — 탐지 수는 그 회차의 사실로 고정. 미해결 수는 보고서가 아니라 현재 열린 문제에서 센다.
				r.getDetectedDriftCount(),
				r.getScannedRootList(), r.getFailedScanRootList(), drifts
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
