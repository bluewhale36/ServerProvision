package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.enums.JobType;
import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
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
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    public PathReconciliationService(List<MarkableScanner> scanners,
                                     ProvisionMarkerService markerService,
                                     BackgroundJobService backgroundJobService,
                                     DriftReportRepository driftReportRepository,
                                     DriftRepository driftRepository,
                                     @Lazy PathReconciliationService self) {
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

    @Value("${reconciliation.auto-apply-path-drift:false}")
    private boolean autoApplyPathDrift;

    @Value("${reconciliation.scan.extra-roots:}")
    private String extraRootsCsv;

    /** 동시 실행 차단. 스캔 시작 시 true 로, 종료 시 false 로. */
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

    @Scheduled(fixedRateString = "${reconciliation.scan.interval-ms:3600000}",
               initialDelayString = "${reconciliation.scan.interval-ms:3600000}")
    public void scheduledQuickScan() {
        if (running.get()) {
            log.debug("[reconciliation] quick 주기 스캔 skip (이전 스캔 RUNNING)");
            return;
        }
        try {
            triggerScan(false);
        } catch (ReconciliationAlreadyRunningException ignored) {}
    }

    @Scheduled(fixedRateString = "${reconciliation.scan.deep-interval-ms:86400000}",
               initialDelayString = "${reconciliation.scan.deep-interval-ms:86400000}")
    public void scheduledDeepScan() {
        if (running.get()) {
            log.debug("[reconciliation] deep 주기 스캔 skip (이전 스캔 RUNNING)");
            return;
        }
        try {
            triggerScan(true);
        } catch (ReconciliationAlreadyRunningException ignored) {}
    }

    /**
     * 수동/주기 공용 스캔 트리거. BackgroundJob 등록 후 비동기 실행.
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
                BackgroundJobService.stagesOf(ReconciliationStage.values()));
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
            log.info("[reconciliation] 스캔 완료. deep={}, totalChecked={}, drifts={}",
                    deep, report.getTotalChecked(), report.getDriftCount());
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
                BackgroundJobService.stagesOf(ReconciliationStage.values()));
        self.runReissueAsync(jobId);
        return jobId;
    }

    @Async
    public void runReissueAsync(String jobId) {
        try {
            backgroundJobService.startStage(jobId, ReconciliationStage.SCANNING);
            ReissueResult result = self.performReissue();
            log.warn("[AUDIT] 마커 서명 재발급 완료 — successCount={}, failedCount={}, failures={}",
                    result.successCount(), result.failures().size(), result.failures());
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
                    markerService.write(resource.getResourcePath(), resource.getMarkerLayout(),
                            unsigned.withSignature(newSig));
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

    /** 마커 재발급 결과 — successCount 건 갱신 + failures 는 사유 메시지 모음. */
    public record ReissueResult(int successCount, List<String> failures) {}

    // ==== 스캔 알고리즘 =================================================

    /** 트랜잭션 경계는 메서드 호출 단위 — 스캔 1회의 보고서 영속화는 한 트랜잭션. */
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
                    drifts.add(buildDrift(resource, DriftKind.SIGNATURE_INVALID, expectedPath.toString(),
                            null, now, "마커 파싱 실패 : " + e.getMessage()));
                    continue;
                }
                if (!markerService.verifySignature(content)) {
                    drifts.add(buildDrift(resource, DriftKind.SIGNATURE_INVALID, expectedPath.toString(),
                            null, now, "HMAC 서명 불일치 — 마커 변조 가능성"));
                    continue;
                }
                if (deep) {
                    Optional<String> recomputed = scannersByType.get(resource.getResourceType())
                            .recomputeManifestHash(resource);
                    if (recomputed.isEmpty()) {
                        // (B-2) Optional.empty 는 본체 자원이 사라졌거나 재계산이 실패한 신호.
                        // 마커는 있지만 본체가 없는 상태 — MISSING 으로 노출해야 운영자가 인지한다.
                        drifts.add(buildDrift(resource, DriftKind.MISSING, expectedPath.toString(),
                                null, now, "deep scan : manifestHash 재계산 실패 — 본체 자원 부재 또는 IO 오류"));
                    } else if (!markerService.verifyManifestHash(content, recomputed.get())) {
                        drifts.add(buildDrift(resource, DriftKind.HASH_MISMATCH, expectedPath.toString(),
                                null, now, "manifestHash 불일치 — 자원 내용 변조 가능성"));
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
                    drifts.add(buildDrift(resource, DriftKind.MISSING, expectedPath.toString(),
                            null, now, "다른 위치에 sidecar 마커는 있으나 본체 파일이 부재 — 마커만 이동 가능성 (의심 경로 : "
                                    + hit.resourcePath() + ")"));
                    continue;
                }
                if (hit.layout() == MarkerLayout.IN_TREE && !Files.isDirectory(hit.resourcePath())) {
                    drifts.add(buildDrift(resource, DriftKind.MISSING, expectedPath.toString(),
                            null, now, "다른 위치에 IN_TREE 마커는 있으나 트리 디렉토리가 부재 (의심 경로 : "
                                    + hit.resourcePath() + ")"));
                    continue;
                }
                drifts.add(buildDrift(resource, DriftKind.PATH_DRIFT, expectedPath.toString(),
                        hit.resourcePath().toString(), now, null));
                continue;
            }

            // 4c) 어디에도 마커 없음 → MISSING
            drifts.add(buildDrift(resource, DriftKind.MISSING, expectedPath.toString(),
                    null, now, "DB 경로와 검색 범위 모두에서 마커를 찾지 못함"));
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

        // (8) 자동 적용 (옵트인, D13) — PATH_DRIFT 만
        if (autoApplyPathDrift) {
            for (Drift d : saved.getDrifts()) {
                if (d.getKind() == DriftKind.PATH_DRIFT) {
                    try {
                        scannersByType.get(d.getResourceType())
                                .applyDriftedPath(d.getResourceId(), Path.of(d.getNewPath()));
                    } catch (RuntimeException ex) {
                        log.warn("[reconciliation] 자동 적용 실패. driftId={}, msg={}", d.getId(), ex.getMessage());
                    }
                }
            }
        }

        return saved;
    }

    private Set<Path> computeScanRoots(List<Markable> active,
                                       Map<ResourceType, Set<Long>> softDeletedByType,
                                       Map<ResourceType, MarkableScanner> scannersByType) {
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
        for (Path root : scanRoots) {
            if (!Files.isDirectory(root)) continue;
            // (B-3) walk 깊이 제한이 적중하면 운영자가 인지할 수 있도록 boundary 마커 카운트를 함께 본다.
            int[] boundaryHits = {0};
            try (Stream<Path> walker = Files.walk(root, WALK_MAX_DEPTH)) {
                walker.filter(Files::isRegularFile)
                      .filter(p -> p.getFileName().toString().endsWith(".provision.json"))
                      .peek(p -> { if (root.relativize(p).getNameCount() >= WALK_MAX_DEPTH) boundaryHits[0]++; })
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
                log.warn("[reconciliation] scan root '{}' 에서 walk 최대 깊이({}) 경계에 위치한 마커가 {}건. "
                        + "더 깊은 트리에 가려진 마커가 있을 수 있다.", root, WALK_MAX_DEPTH, boundaryHits[0]);
            }
        }
        return result;
    }

    /** 마커 파일 경로로부터 자원 위치 + (resourceType, resourceId) 추론. */
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
                log.warn("[reconciliation] 알 수 없는 resourceType : {} (path={}) — 외부 자원 또는 신규 버전?",
                        content.resourceType(), markerFile);
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

    private Drift buildDrift(Markable resource, DriftKind kind, String oldPath, String newPath,
                             Instant detectedAt, String detail) {
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
        Drift drift = driftRepository.findById(driftId)
                .orElseThrow(() -> new DriftNotFoundException(driftId));
        if (drift.getKind() != DriftKind.PATH_DRIFT) {
            throw new DriftAutoApplyNotAllowedException(drift.getKind());
        }
        MarkableScanner scanner = scannerFor(drift.getResourceType());
        scanner.applyDriftedPath(drift.getResourceId(), Path.of(drift.getNewPath()));
        // 적용 후 보고서에서 제거 — 동일 drift 가 다음 스캔까지 남아 중복 적용되지 않게
        drift.getReport().removeDrift(drift);
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
                .map(d -> new DriftResponse(d.getId(), d.getResourceType(), d.getResourceId(),
                        d.getKind(), d.getOldPath(), d.getNewPath(), d.getDetectedAt(), d.getDetail()))
                .collect(Collectors.toList());
        return new DriftReportResponse(r.getId(), r.getScannedAt(),
                r.getScanDuration().toString(), r.isDeep(), r.getTotalChecked(),
                r.getDriftCount(), r.getFailedScanRootList(), drifts);
    }

    // ==== 내부 키 ======================================================

    private record MarkerKey(ResourceType resourceType, Long resourceId) {}
    private record MarkerHit(ResourceType resourceType, Long resourceId, Path resourcePath, MarkerLayout layout) {}
}
