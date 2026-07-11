package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.GhostDbRowClearResolution;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.PathDriftResolution;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.ReconciliationAlreadyRunningException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftReportRepository;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MK1 PathReconciliationService 단위 테스트.
 * 스캔 알고리즘의 5 가지 drift 분류 (PATH_DRIFT / MISSING / ORPHAN / SIGNATURE_INVALID / HASH_MISMATCH) +
 * happy 케이스 + apply / dismiss / 동시 실행 차단 + soft-delete ORPHAN 제외 (D20).
 */
class PathReconciliationServiceTest {

    private ProvisionMarkerService markerService;
    private BackgroundJobService backgroundJobService;
    private DriftReportRepository driftReportRepository;
    private DriftRepository driftRepository;

    private MarkableScanner isoScanner;
    private PathReconciliationService service;

    @BeforeEach
    void setUp() {
        markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "test-secret");

        backgroundJobService = mock(BackgroundJobService.class);
        given(backgroundJobService.register(any(), anyString(), anyString(), org.mockito.ArgumentMatchers.<List<String>>any())).willReturn("job-1");

        driftReportRepository = mock(DriftReportRepository.class);
        given(driftReportRepository.save(any(DriftReport.class))).willAnswer(inv -> inv.getArgument(0));
        given(driftReportRepository.count()).willReturn(0L);

        driftRepository = mock(DriftRepository.class);

        isoScanner = mock(MarkableScanner.class);
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);

        // self proxy 자리는 단위 테스트 범위 외 (async/proxy 경로는 통합 테스트에서 검증). null 주입.
        service = new PathReconciliationService(
                List.of(isoScanner), markerService, backgroundJobService,
                driftReportRepository, driftRepository,
                List.of(new PathDriftResolution(), new GhostDbRowClearResolution()), null);
        ReflectionTestUtils.setField(service, "startupEnabled", true);
        ReflectionTestUtils.setField(service, "retentionCount", 100);
        ReflectionTestUtils.setField(service, "autoApplyKindsCsv", "");
        ReflectionTestUtils.setField(service, "extraRootsCsv", "");
    }

    private Markable isoAt(Long id, Path path) {
        Markable m = mock(Markable.class);
        given(m.getResourceId()).willReturn(id);
        given(m.getResourceType()).willReturn(ResourceType.OS_ISO);
        given(m.getResourcePath()).willReturn(path);
        given(m.getMarkerLayout()).willReturn(MarkerLayout.SIDECAR);
        return m;
    }

    private void writeMarker(Path resourcePath, MarkerLayout layout, Long id, String hash) {
        MarkerContent unsigned = new MarkerContent(
                ResourceType.OS_ISO.name(), id, Map.of(), Instant.now(), hash, null);
        String sig = markerService.computeSignature(unsigned);
        markerService.write(resourcePath, layout, unsigned.withSignature(sig));
    }

    @Test
    @DisplayName("happy : 모든 자원이 정상이면 drift 0건")
    void scan_happy(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "fake-iso");
        writeMarker(iso, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable m = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDriftCount()).isZero();
        assertThat(saved.getTotalChecked()).isEqualTo(1);
    }

    @Test
    @DisplayName("PATH_DRIFT : DB path 에 마커 없고 다른 위치에서 (type,id) 매칭 마커 발견")
    void scan_pathDrift(@TempDir Path tmp) throws Exception {
        Path oldIso = tmp.resolve("old/dvd.iso");
        Path newIso = tmp.resolve("new/dvd.iso");
        Files.createDirectories(newIso.getParent());
        Files.writeString(newIso, "fake-iso");
        writeMarker(newIso, MarkerLayout.SIDECAR, 42L, "hash-abc");

        // DB 가 아는 path 는 oldIso (마커 없음), 하지만 같은 (OS_ISO, 42L) 마커가 newIso 옆에 있음
        Markable iso = isoAt(42L, oldIso);
        // scan root union 에 newIso.parent 도 포함되도록 oldIso.parent 가 같은 tmp 하위라야 함
        Files.createDirectories(oldIso.getParent());
        given(isoScanner.findActiveMarkables()).willReturn(List.of(iso));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> {
                    assertThat(d.getKind()).isEqualTo(DriftKind.PATH_DRIFT);
                    assertThat(d.getResourceId()).isEqualTo(42L);
                    assertThat(d.getOldPath()).isEqualTo(oldIso.toString());
                    assertThat(d.getNewPath()).isEqualTo(newIso.toString());
                });
    }

    @Test
    @DisplayName("MISSING : DB path 에도 다른 어디에도 마커 없음")
    void scan_missing(@TempDir Path tmp) {
        Path iso = tmp.resolve("dvd.iso"); // 파일도 마커도 만들지 않음
        Markable m = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.MISSING));
    }

    @Test
    @DisplayName("S6-1 quick scan : 마커 정상 + 본체 파일 부재 → deep 대기 없이 MISSING (본체 검사 조기화)")
    void scan_bodyMissingDetectedOnQuickScan(@TempDir Path tmp) {
        Path iso = tmp.resolve("dvd.iso");
        // 본체는 만들지 않고 정상 서명 마커만 — 파일명 단독 변경(마커 잔존) 상황 재현
        writeMarker(iso, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable m = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> {
                    assertThat(d.getKind()).isEqualTo(DriftKind.MISSING);
                    assertThat(d.getDetail()).contains("본체 파일 부재");
                });
    }

    @Test
    @DisplayName("S6-1 : 마커 변조 + 본체 부재가 겹치면 SIGNATURE_INVALID 우선 (보안 신호가 운영 신호에 안 가려짐)")
    void scan_signatureInvalidTakesPrecedenceOverBodyMissing(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso"); // 본체 없음
        Path sidecar = tmp.resolve("dvd.iso.provision.json");
        Files.writeString(sidecar, "{\"resourceType\":\"OS_ISO\",\"resourceId\":42,\"attributes\":{},\"createdAt\":\"2026-04-25T00:00:00Z\",\"manifestHash\":\"x\",\"signature\":\"BAD_SIGNATURE\"}");
        Markable m = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.SIGNATURE_INVALID));
    }

    @Test
    @DisplayName("SIGNATURE_INVALID : 마커는 있지만 서명 깨짐")
    void scan_signatureInvalid(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "fake-iso");
        // 가짜 marker 파일 (잘못된 서명)
        Path sidecar = tmp.resolve("dvd.iso.provision.json");
        Files.writeString(sidecar, "{\"resourceType\":\"OS_ISO\",\"resourceId\":42,\"attributes\":{},\"createdAt\":\"2026-04-25T00:00:00Z\",\"manifestHash\":\"x\",\"signature\":\"BAD_SIGNATURE\"}");
        Markable m = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.SIGNATURE_INVALID));
    }

    @Test
    @DisplayName("HASH_MISMATCH : deep scan 시 manifestHash 재계산 결과 불일치")
    void scan_hashMismatch(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "fake-iso");
        writeMarker(iso, MarkerLayout.SIDECAR, 42L, "stored-hash");

        Markable m = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        given(isoScanner.recomputeManifestHash(m)).willReturn(Optional.of("DIFFERENT_HASH"));

        ReflectionTestUtils.invokeMethod(service, "performScan", true, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> {
                    assertThat(d.getKind()).isEqualTo(DriftKind.HASH_MISMATCH);
                    // S6-3-4 — 수용 판단 재료 스냅샷: 실행 시 대조용 지문 + 카드의 대조 재료(정본 인정 시각·지문 전문)
                    assertThat(d.getObservedHash()).isEqualTo("DIFFERENT_HASH");
                    assertThat(d.getDetail()).contains("정본 인정").contains("등록 지문").contains("현재 지문 DIFFERENT_HASH");
                });
    }

    @Test
    @DisplayName("ORPHAN : 디스크 마커 발견됐으나 active inventory 에 없음")
    void scan_orphan(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("ghost.iso");
        Files.writeString(iso, "ghost");
        writeMarker(iso, MarkerLayout.SIDECAR, 99L, "hash");
        // active inventory 비움 — 마커만 잔재
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> {
                    assertThat(d.getKind()).isEqualTo(DriftKind.ORPHAN);
                    assertThat(d.getResourceId()).isEqualTo(99L);
                });
    }

    @Test
    @DisplayName("D20 → S6-2-2 : soft-deleted 자원의 마커는 ORPHAN 이 아니라 ESCAPE 로 분류 (침묵 제외의 소멸)")
    void scan_softDeletedNotOrphan(@TempDir Path tmp) throws Exception {
        // 종전에는 soft-deleted ID 매칭 마커를 조용히 건너뛰었다(완전 침묵). 이제 원위치에서
        // 마커+본체가 발견된 이 상태는 "삭제 자원 복귀"로 분류된다 — ORPHAN 오탐 방지는 그대로 유지.
        Path iso = tmp.resolve("deleted.iso");
        Files.writeString(iso, "x");
        writeMarker(iso, MarkerLayout.SIDECAR, 77L, "hash");
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(
                new DeletedIso(77L, iso, tmp.resolve("trash/gone.iso").toString())));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isNotEqualTo(DriftKind.ORPHAN);
            assertThat(d.getKind()).isEqualTo(DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL);
            assertThat(d.getResourceId()).isEqualTo(77L);
        });
    }

    @Test
    @DisplayName("동시 실행 차단 : 이미 RUNNING 시 새 스캔 거절")
    void triggerScan_alreadyRunning() {
        AtomicBoolean running = (AtomicBoolean) ReflectionTestUtils.getField(service, "running");
        running.set(true);

        assertThatThrownBy(() -> service.triggerScan(false))
                .isInstanceOf(ReconciliationAlreadyRunningException.class);
        verify(backgroundJobService, never()).register(any(), any(), any(), org.mockito.ArgumentMatchers.<List<String>>any());
    }

    @Test
    @DisplayName("apply(PATH_DRIFT) : scanner.applyDriftedPath 호출 + 보고서에서 drift 제거")
    void apply_pathDrift_success(@TempDir Path tmp) {
        Path newPath = tmp.resolve("new.iso");
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(100).deep(false).totalChecked(1).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.PATH_DRIFT)
                .oldPath("/old").newPath(newPath.toString()).detectedAt(Instant.now()).build();
        report.addDrift(drift);
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        service.apply(1L);

        verify(isoScanner, times(1)).applyDriftedPath(42L, newPath);
        assertThat(report.getDrifts()).isEmpty();
    }

    @Test
    @DisplayName("apply : 시스템 해결 불가 종류 (mode=NONE) → 409 예외 (manuallyResolvable SSOT)")
    void apply_nonPathDrift_throws() {
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.SIGNATURE_INVALID)
                .oldPath("/x").detectedAt(Instant.now()).build();
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        assertThatThrownBy(() -> service.apply(1L))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("마커 서명 불일치");
    }

    @Test
    @DisplayName("S6-2-1 apply : 마스터(resolution-enabled) OFF → 허용 종류(PATH_DRIFT)도 409 (globalOff 안전망)")
    void apply_globalOff_rejectsEvenApplicableKind() {
        ReflectionTestUtils.setField(service, "resolutionEnabled", Boolean.FALSE);
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.PATH_DRIFT)
                .oldPath("/x").newPath("/y").detectedAt(Instant.now()).build();
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        assertThatThrownBy(() -> service.apply(1L))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("reconciliation.resolution-enabled");
    }

    @Test
    @DisplayName("S6-2-1 isResolutionEnabled : FALSE 일 때만 false — null(미주입)/TRUE 는 true (서버 가드·뷰모델 공유 SSOT)")
    void isResolutionEnabled_nullMeansEnabled() {
        assertThat(service.isResolutionEnabled()).isTrue(); // 미주입(null)
        ReflectionTestUtils.setField(service, "resolutionEnabled", Boolean.TRUE);
        assertThat(service.isResolutionEnabled()).isTrue();
        ReflectionTestUtils.setField(service, "resolutionEnabled", Boolean.FALSE);
        assertThat(service.isResolutionEnabled()).isFalse();
    }

    @Test
    @DisplayName("apply : 존재하지 않는 driftId → DriftNotFoundException")
    void apply_notFound() {
        given(driftRepository.findById(999L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.apply(999L))
                .isInstanceOf(DriftNotFoundException.class);
    }

    @Test
    @DisplayName("dismiss : 보고서에서 drift 제거")
    void dismiss_removesFromReport() {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(50).deep(false).totalChecked(1).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.MISSING)
                .oldPath("/x").detectedAt(Instant.now()).build();
        report.addDrift(drift);
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        service.dismiss(1L);

        assertThat(report.getDrifts()).isEmpty();
    }

    private DriftReport captureSavedReport() {
        var captor = org.mockito.ArgumentCaptor.forClass(DriftReport.class);
        verify(driftReportRepository).save(captor.capture());
        return captor.getValue();
    }

    // ==== MK3-1 — Ghost row drift 시나리오 =================================

    @Test
    @DisplayName("MK3-1 → S6-2-3 : ghost row → GHOST_DB_ROW drift (전수 대조 패스로 흡수 후에도 동일 판정)")
    void scan_ghostDriftReported(@TempDir Path tmp) {
        // 유령 정의 : 삭제 표시 + 휴지통 기록 없음 + 실물 없음 — DeletedIso(trashedPath=null, 파일 미생성)
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(99L, tmp.resolve("removed.iso"), null)));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> {
                    assertThat(d.getKind()).isEqualTo(DriftKind.GHOST_DB_ROW);
                    assertThat(d.getResourceId()).isEqualTo(99L);
                    assertThat(d.getNewPath()).isNull();
                });
        // auto-apply.kinds default 빈 → applyGhostClear 호출되지 않음
        verify(isoScanner, never()).applyGhostClear(99L);
    }

    @Test
    @DisplayName("S6-2-1 : auto-apply.kinds=GHOST_DB_ROW 시 scan 직후 applyGhostClear 자동 호출")
    void scan_ghostAutoApplied(@TempDir Path tmp) {
        ReflectionTestUtils.setField(service, "autoApplyKindsCsv", "GHOST_DB_ROW");
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(99L, tmp.resolve("removed.iso"), null)));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        verify(isoScanner, times(1)).applyGhostClear(99L);
    }

    @Test
    @DisplayName("MK3-1 : apply(GHOST_DB_ROW) → scanner.applyGhostClear 호출 + drift 제거")
    void apply_ghostRow_success() {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(50).deep(false).totalChecked(0).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(99L).kind(DriftKind.GHOST_DB_ROW)
                .oldPath("/missing").newPath(null).detectedAt(Instant.now()).build();
        report.addDrift(drift);
        ReflectionTestUtils.setField(drift, "id", 7L);
        given(driftRepository.findById(7L)).willReturn(Optional.of(drift));

        service.apply(7L);

        verify(isoScanner, times(1)).applyGhostClear(99L);
        assertThat(report.getDrifts()).isEmpty();
    }

    // ==== R9-5 — 자원 실명 스냅샷 ====================================================

    @Test
    @DisplayName("R9-5 : drift 에 Markable.displayName() 이 스냅샷된다")
    void scan_snapshotsDisplayName(@TempDir Path tmp) {
        // 마커가 어디에도 없는 자원 → MISSING drift. displayName 스텁이 그대로 기록되어야 한다.
        Path iso = tmp.resolve("dvd.iso");
        Markable m = isoAt(42L, iso);
        given(m.displayName()).willReturn("Rocky Linux 9.6 dvd.iso");
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).hasSize(1);
        Drift drift = saved.getDrifts().iterator().next();
        assertThat(drift.getKind()).isEqualTo(DriftKind.MISSING);
        assertThat(drift.getDisplayName()).isEqualTo("Rocky Linux 9.6 dvd.iso");
    }

    @Test
    @DisplayName("R9-5 : ORPHAN 은 Markable 이 없어 마커 본체 파일명이 실명 fallback")
    void scan_orphanFallsBackToFilename(@TempDir Path tmp) throws Exception {
        // DB 인벤토리는 비어 있고 디스크에만 마커 존재 → ORPHAN. 실명 = 본체 파일명.
        Path stray = tmp.resolve("stray.iso");
        Files.writeString(stray, "fake-iso");
        writeMarker(stray, MarkerLayout.SIDECAR, 99L, "hash-x");
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).hasSize(1);
        Drift drift = saved.getDrifts().iterator().next();
        assertThat(drift.getKind()).isEqualTo(DriftKind.ORPHAN);
        assertThat(drift.getDisplayName()).isEqualTo("stray.iso");
    }

    // ==== R9-1 — 완료 결과 metadata + stage 계측 ====================================

    @Test
    @DisplayName("R9-1 : performScan 이 CLASSIFYING → PERSISTING 순서로 stage 를 계측")
    void performScan_instrumentsStageBoundaries(@TempDir Path tmp) {
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(backgroundJobService);
        inOrder.verify(backgroundJobService).startStage("job-1", ReconciliationStage.CLASSIFYING);
        inOrder.verify(backgroundJobService).startStage("job-1", ReconciliationStage.PERSISTING);
    }

    @Test
    @DisplayName("R9-1 : runAsync 성공 시 driftCount 결과 metadata 와 함께 complete")
    void runAsync_completesWithDriftCountMetadata(@TempDir Path tmp) {
        ReflectionTestUtils.setField(service, "self", service);
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        service.runAsync("job-1", false);

        verify(backgroundJobService).complete("job-1", Map.of("driftCount", "0"));
    }

    @Test
    @DisplayName("R9-1 : runAsync 중 예외 → fail 호출 (complete 미호출)")
    void runAsync_failureMarksJobFailed(@TempDir Path tmp) {
        ReflectionTestUtils.setField(service, "self", service);
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());
        given(driftReportRepository.save(any(DriftReport.class))).willThrow(new IllegalStateException("DB down"));

        service.runAsync("job-1", false);

        verify(backgroundJobService).fail(org.mockito.ArgumentMatchers.eq("job-1"), anyString());
        verify(backgroundJobService, never()).complete(anyString(), org.mockito.ArgumentMatchers.<Map<String, String>>any());
    }

    @Test
    @DisplayName("R9-1 : 재발급 Job 은 ReissueStage 단일 단계로 등록 + 성공/실패 건수 metadata 로 complete")
    void reissue_usesReissueStageAndCompletesWithCounts() {
        ReflectionTestUtils.setField(service, "self", service);
        given(isoScanner.findActiveMarkables()).willReturn(List.of());

        service.triggerReissueAllSignatures();

        verify(backgroundJobService).register(
                org.mockito.ArgumentMatchers.eq(com.example.serverprovision.global.job.enums.JobType.MARKER_REISSUE),
                anyString(), anyString(),
                org.mockito.ArgumentMatchers.eq(List.of("마커 재서명"))
        );
        verify(backgroundJobService).startStage("job-1", ReissueStage.RESIGNING);
        verify(backgroundJobService).complete("job-1", Map.of(
                "reissueSucceeded", "0",
                "reissueFailed", "0"
        ));
    }

    // ==== S6-2-1 — 해결 디스패치 다형화 ==============================================

    @Test
    @DisplayName("S6-2-1 : forced apply 라도 해결 bean 미등록 kind 는 409 (map-miss 널가드는 우회 밖)")
    void apply_forced_missingResolution_throws() {
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.MISSING)
                .oldPath("/x").detectedAt(Instant.now()).build();
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        assertThatThrownBy(() -> service.apply(1L, true))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("자원 소실");
        // 종전 코드라면 Path.of(null) NPE 로 떨어지던 경로 — 디스패치 널가드가 409 로 정리
        verify(isoScanner, never()).applyDriftedPath(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("S6-2-1 : 스캔 자동 적용 — kinds 에 PATH_DRIFT 포함 시 resolve 실행 (drift 는 보고서에 기록 유지)")
    void scan_autoAppliesPathDrift_whenKindsIncluded(@TempDir Path tmp) throws Exception {
        Path oldIso = tmp.resolve("old/dvd.iso");
        Path newIso = tmp.resolve("new/dvd.iso");
        Files.createDirectories(oldIso.getParent());
        Files.createDirectories(newIso.getParent());
        Files.writeString(newIso, "fake-iso");
        writeMarker(newIso, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable iso = isoAt(42L, oldIso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(iso));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());
        ReflectionTestUtils.setField(service, "autoApplyKindsCsv", "PATH_DRIFT");

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        verify(isoScanner, times(1)).applyDriftedPath(42L, newIso);
        assertThat(captureSavedReport().getDrifts()).hasSize(1); // 기록 보존
    }

    @Test
    @DisplayName("S6-2-1 : 스캔 자동 적용 — kinds 빈 default 면 AUTO kind 도 무인 적용 없음 (수동 대기)")
    void scan_noAutoApply_whenKindsEmpty(@TempDir Path tmp) throws Exception {
        Path oldIso = tmp.resolve("old/dvd.iso");
        Path newIso = tmp.resolve("new/dvd.iso");
        Files.createDirectories(oldIso.getParent());
        Files.createDirectories(newIso.getParent());
        Files.writeString(newIso, "fake-iso");
        writeMarker(newIso, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable iso = isoAt(42L, oldIso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(iso));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        verify(isoScanner, never()).applyDriftedPath(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("S6-2-1 : auto-apply.kinds 에 무효 kind 명 → IllegalArgumentException (설정 오타의 시끄러운 실패)")
    void scan_invalidKindsCsv_failsLoudly(@TempDir Path tmp) {
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());
        ReflectionTestUtils.setField(service, "autoApplyKindsCsv", "PATH_DRIFTT");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PATH_DRIFTT");
    }

    // ==== S6-2-2 — SOFTDEL ESCAPE 분류 =============================================

    /** soft-deleted 분류 패스용 겸용 fixture — TrashLifecycleServiceTest.TestEntity 선례. */
    private static class DeletedIso extends com.example.serverprovision.global.entity.LifecycleEntity implements Markable {
        private final Long id;
        private final Path path;
        DeletedIso(Long id, Path path, String trashedPath) {
            this.id = id;
            this.path = path;
            softDelete();
            if (trashedPath != null) markTrashed(trashedPath);
        }
        @Override protected Long resourceId() { return id; }
        @Override protected com.example.serverprovision.global.entity.LifecycleEntity parentLifecycle() { return null; }
        @Override public Long getResourceId() { return id; }
        @Override public ResourceType getResourceType() { return ResourceType.OS_ISO; }
        @Override public Path getResourcePath() { return path; }
        @Override public MarkerLayout getMarkerLayout() { return MarkerLayout.SIDECAR; }
        @Override public String getManifestHash() { return "hash-abc"; }
        @Override public String getMarkerSignature() { return "sig"; }
        @Override public void reissueMarker(String h, String sg) { }
    }

    @Test
    @DisplayName("S6-2-2 복귀 경로① : 휴지통 파일 소실 + 원위치에 본체 복귀 → SOFTDEL_ESCAPE_TO_ORIGINAL")
    void scan_escapeToOriginal_trashGoneBodyBack(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("iso/dvd.iso");
        Files.createDirectories(orig.getParent());
        Files.writeString(orig, "body"); // 본체만 복귀 (마커는 삭제 때 정리된 상태)
        String trashed = tmp.resolve("trash/dvd_x.iso").toString(); // 존재하지 않음
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashed)));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL);
            assertThat(d.getOldPath()).isEqualTo(trashed);
            assertThat(d.getNewPath()).isEqualTo(orig.toString());
        });
    }

    @Test
    @DisplayName("S6-2-2 복귀 경로② : 휴지통 기록 없음 + 원위치 출현 → TO_ORIGINAL (유령으로 오인하지 않음)")
    void scan_escapeToOriginal_noTrashRecord(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("iso/dvd.iso");
        Files.createDirectories(orig.getParent());
        Files.writeString(orig, "body");
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, null)));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL);
            assertThat(d.getDetail()).contains("휴지통 기록");
        });
    }

    @Test
    @DisplayName("S6-2-2 이탈 : 삭제 자원의 마커+본체가 다른 폴더에서 발견 → TO_OTHER (종전 침묵 소멸)")
    void scan_escapeToOther_markerElsewhere(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("iso/dvd.iso"); // 원위치 — 비어 있음
        Path stray = tmp.resolve("backup/dvd.iso");
        Files.createDirectories(dbPath.getParent());
        Files.createDirectories(stray.getParent());
        Files.writeString(stray, "body");
        writeMarker(stray, MarkerLayout.SIDECAR, 42L, "hash-abc");
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(
                new DeletedIso(42L, dbPath, tmp.resolve("trash/gone.iso").toString())));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.SOFTDEL_ESCAPE_TO_OTHER);
            assertThat(d.getNewPath()).isEqualTo(stray.toString());
            assertThat(d.getDetail()).contains("다른 위치");
        });
    }

    @Test
    @DisplayName("S6-2-2 모호 : 다른 폴더 마커 + 원위치 파일 동시 → TO_OTHER 1건 + 병기 (복귀로 기울이지 않음)")
    void scan_escapeAmbiguous_bothLocations(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("iso/dvd.iso");
        Path stray = tmp.resolve("backup/dvd.iso");
        Files.createDirectories(dbPath.getParent());
        Files.createDirectories(stray.getParent());
        Files.writeString(dbPath, "body-at-original"); // 원위치에도 파일 (마커 없음)
        Files.writeString(stray, "body");
        writeMarker(stray, MarkerLayout.SIDECAR, 42L, "hash-abc");
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(
                new DeletedIso(42L, dbPath, tmp.resolve("trash/gone.iso").toString())));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.SOFTDEL_ESCAPE_TO_OTHER);
            assertThat(d.getDetail()).contains("원위치에도 파일 존재");
        });
    }

    @Test
    @DisplayName("S6-2-2 : 정상 휴지통 자원(휴지통 파일 생존)은 drift 0 — 점유·잔여마커·소실 상태는 S6-2-3 전까지 침묵 유지")
    void scan_normalTrashedResource_noDrift(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("iso/dvd.iso"); // 원위치 비어 있음
        Path trashed = tmp.resolve("trash/dvd_x.iso");
        Files.createDirectories(orig.getParent());
        Files.createDirectories(trashed.getParent());
        Files.writeString(trashed, "body"); // 휴지통 파일 생존
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashed.toString())));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        assertThat(captureSavedReport().getDrifts()).isEmpty();
    }

    @Test
    @DisplayName("S6-2-2 : 메타 자원 scanner 는 분류 패스에서 제외 — findTrashed 미호출, NPE·유령 오탐 없음")
    void scan_metaScannerExcluded(@TempDir Path tmp) {
        MarkableScanner metaScanner = mock(MarkableScanner.class);
        given(metaScanner.supportedType()).willReturn(ResourceType.OS_IMAGE);
        given(metaScanner.findActiveMarkables()).willReturn(List.of());
        PathReconciliationService svc = new PathReconciliationService(
                List.of(isoScanner, metaScanner), markerService, backgroundJobService,
                driftReportRepository, driftRepository,
                List.of(new PathDriftResolution(), new GhostDbRowClearResolution()), null);
        ReflectionTestUtils.setField(svc, "retentionCount", 100);
        ReflectionTestUtils.setField(svc, "extraRootsCsv", tmp.toString());
        given(isoScanner.findActiveMarkables()).willReturn(List.of());

        ReflectionTestUtils.invokeMethod(svc, "performScan", false, "job-1");

        verify(metaScanner, never()).findTrashed();
        assertThat(captureSavedReport().getDrifts()).isEmpty();
    }

    @Test
    @DisplayName("S6-2-2 : 점유 상태(원위치 마커+본체 복귀 + 휴지통 사본 생존)는 drift 미보고 — 적용이 항상 실패할 버튼을 숨김")
    void scan_occupiedWithMarker_notReported(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("iso/dvd.iso");
        Path trashed = tmp.resolve("trash/dvd_x.iso");
        Files.createDirectories(orig.getParent());
        Files.createDirectories(trashed.getParent());
        Files.writeString(orig, "body-back");
        writeMarker(orig, MarkerLayout.SIDECAR, 42L, "hash-abc"); // 마커까지 복귀
        Files.writeString(trashed, "trash-copy");                  // 휴지통 사본 생존
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashed.toString())));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        // 진위·처리는 복원 시점 게이트(RestorePathOccupiedException)가 SSOT — 점검은 침묵
        assertThat(captureSavedReport().getDrifts()).isEmpty();
    }

    /** IN_TREE 레이아웃 검증용 fixture — BIOS 번들 디렉토리. */
    private static class DeletedTree extends com.example.serverprovision.global.entity.LifecycleEntity implements Markable {
        private final Long id;
        private final Path path;
        DeletedTree(Long id, Path path, String trashedPath) {
            this.id = id;
            this.path = path;
            softDelete();
            if (trashedPath != null) markTrashed(trashedPath);
        }
        @Override protected Long resourceId() { return id; }
        @Override protected com.example.serverprovision.global.entity.LifecycleEntity parentLifecycle() { return null; }
        @Override public Long getResourceId() { return id; }
        @Override public ResourceType getResourceType() { return ResourceType.BIOS_BUNDLE; }
        @Override public Path getResourcePath() { return path; }
        @Override public MarkerLayout getMarkerLayout() { return MarkerLayout.IN_TREE; }
        @Override public String getManifestHash() { return "hash-tree"; }
        @Override public String getMarkerSignature() { return "sig"; }
        @Override public void reissueMarker(String h, String sg) { }
    }

    @Test
    @DisplayName("S6-2-2 IN_TREE : 삭제된 BIOS 트리가 원위치에 복귀(디렉토리 존재) → TO_ORIGINAL (isDirectory 술어 검증)")
    void scan_escapeToOriginal_inTreeResource(@TempDir Path tmp) throws Exception {
        MarkableScanner biosScanner = mock(MarkableScanner.class);
        given(biosScanner.supportedType()).willReturn(ResourceType.BIOS_BUNDLE);
        Path treeRoot = tmp.resolve("bios/R23");
        Files.createDirectories(treeRoot); // 트리 복귀 (마커 없이)
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findTrashed()).willReturn(List.of(
                new DeletedTree(7L, treeRoot, tmp.resolve("trash/R23_x").toString())));
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        PathReconciliationService svc = new PathReconciliationService(
                List.of(isoScanner, biosScanner), markerService, backgroundJobService,
                driftReportRepository, driftRepository,
                List.of(new PathDriftResolution(), new GhostDbRowClearResolution()), null);
        ReflectionTestUtils.setField(svc, "retentionCount", 100);
        ReflectionTestUtils.setField(svc, "extraRootsCsv", "");

        ReflectionTestUtils.invokeMethod(svc, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL);
            assertThat(d.getResourceType()).isEqualTo(ResourceType.BIOS_BUNDLE);
        });
    }

    // ==== S6-2-3 — TRASH 계열 =============================================

    @Test
    @DisplayName("S6-2-3 소실 : 휴지통 기록 有 + 실물 無 + 원위치 無 → TRASH_LOST (종전 침묵 소멸)")
    void scan_trashLost(@TempDir Path tmp) {
        Path orig = tmp.resolve("iso/dvd.iso"); // 원위치 비어 있음
        String trashed = tmp.resolve("trash/dvd_x.iso").toString(); // 실물 부재
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashed)));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.TRASH_LOST);
            assertThat(d.getOldPath()).isEqualTo(trashed);
            assertThat(d.getNewPath()).isNull();
        });
    }

    @Test
    @DisplayName("S6-2-3 잔여 마커 : 휴지통 실물 옆 마커 잔존 → TRASH_MARKER_STALE (수색 확대 없이 정위치 확인)")
    void scan_trashMarkerStale(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("iso/dvd.iso");
        Path trashed = tmp.resolve("trash/dvd_x.iso");
        Files.createDirectories(trashed.getParent());
        Files.writeString(trashed, "body");
        Files.writeString(tmp.resolve("trash/dvd_x.iso.provision.json"), "{}"); // 잔여 마커
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, orig, trashed.toString())));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.TRASH_MARKER_STALE);
            assertThat(d.getOldPath()).isEqualTo(trashed.toString());
        });
    }

    @Test
    @DisplayName("S6-2-3 독립 신호 : 잔여 마커 + 위치 이탈이 같은 자원에서 동시 보고")
    void scan_staleAndEscapeReportedTogether(@TempDir Path tmp) throws Exception {
        Path dbPath = tmp.resolve("iso/dvd.iso");
        Path stray = tmp.resolve("backup/dvd.iso");
        Path trashed = tmp.resolve("trash/dvd_x.iso");
        Files.createDirectories(dbPath.getParent());
        Files.createDirectories(stray.getParent());
        Files.createDirectories(trashed.getParent());
        Files.writeString(stray, "body");
        writeMarker(stray, MarkerLayout.SIDECAR, 42L, "hash-abc"); // 타 위치 마커+본체 (이탈)
        Files.writeString(trashed, "trash-copy");                   // 휴지통 실물 생존
        Files.writeString(tmp.resolve("trash/dvd_x.iso.provision.json"), "{}"); // + 잔여 마커
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findTrashed()).willReturn(List.of(new DeletedIso(42L, dbPath, trashed.toString())));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.resolve("backup").toString() + "," + tmp.resolve("iso"));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).extracting(Drift::getKind).containsExactlyInAnyOrder(
                DriftKind.SOFTDEL_ESCAPE_TO_OTHER, DriftKind.TRASH_MARKER_STALE);
    }

    @Test
    @DisplayName("S6-2-3 IN_TREE 잔여 마커 : 휴지통 트리(디렉토리) 내부 마커 잔존 → TRASH_MARKER_STALE")
    void scan_trashMarkerStale_inTree(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("bios/R23");
        Path trashedTree = tmp.resolve("trash/R23_x");
        Files.createDirectories(trashedTree);
        Files.writeString(trashedTree.resolve("rom.bin"), "rom");
        Files.writeString(trashedTree.resolve(".provision.json"), "{}"); // 트리 내부 잔여 마커
        MarkableScanner biosScanner = mock(MarkableScanner.class);
        given(biosScanner.supportedType()).willReturn(ResourceType.BIOS_BUNDLE);
        given(biosScanner.findActiveMarkables()).willReturn(List.of());
        given(biosScanner.findTrashed()).willReturn(List.of(
                new DeletedTree(7L, orig, trashedTree.toString())));
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        PathReconciliationService svc = new PathReconciliationService(
                List.of(isoScanner, biosScanner), markerService, backgroundJobService,
                driftReportRepository, driftRepository,
                List.of(new PathDriftResolution(), new GhostDbRowClearResolution()), null);
        ReflectionTestUtils.setField(svc, "retentionCount", 100);
        ReflectionTestUtils.setField(svc, "extraRootsCsv", "");

        ReflectionTestUtils.invokeMethod(svc, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.TRASH_MARKER_STALE);
            assertThat(d.getResourceType()).isEqualTo(ResourceType.BIOS_BUNDLE);
        });
    }

    // ==== R9-6 — 재발급 실패 후속 점검 ==============================================

    @Test
    @DisplayName("R9-6 : 재발급 부분 실패 → 잠금 해제 후 자원 무결성 점검 자동 시작")
    void reissue_partialFailure_triggersFollowupScan(@TempDir Path tmp) throws Exception {
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());
        // 마커 없는 자원 1건 — performReissue 의 read 가 실패해 failures=1
        Markable broken = isoAt(42L, tmp.resolve("no-marker.iso"));
        given(isoScanner.findActiveMarkables()).willReturn(List.of(broken));

        service.runReissueAsync("job-1");

        // 재발급(MARKER_REISSUE) 등록에 이어 후속 점검(PATH_RECONCILIATION) job 이 등록된다
        verify(backgroundJobService).register(
                org.mockito.ArgumentMatchers.eq(com.example.serverprovision.global.job.enums.JobType.PATH_RECONCILIATION),
                anyString(), anyString(), org.mockito.ArgumentMatchers.<List<String>>any());
    }

    @Test
    @DisplayName("R9-6 : 전부 성공하면 후속 점검 없음 — 불필요한 점검을 만들지 않는다")
    void reissue_allSuccess_noFollowupScan(@TempDir Path tmp) throws Exception {
        ReflectionTestUtils.setField(service, "self", service);
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "fake-iso");
        writeMarker(iso, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable ok = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(ok));

        service.runReissueAsync("job-1");

        verify(backgroundJobService, never()).register(
                org.mockito.ArgumentMatchers.eq(com.example.serverprovision.global.job.enums.JobType.PATH_RECONCILIATION),
                anyString(), anyString(), org.mockito.ArgumentMatchers.<List<String>>any());
    }
}
