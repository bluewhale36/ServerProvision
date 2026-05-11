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
import com.example.serverprovision.maintenance.reconciliation.exception.DriftAutoApplyNotAllowedException;
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
        given(isoScanner.findSoftDeletedResourceIds()).willReturn(Set.of());

        // self proxy 자리는 단위 테스트 범위 외 (async/proxy 경로는 통합 테스트에서 검증). null 주입.
        service = new PathReconciliationService(
                List.of(isoScanner), markerService, backgroundJobService,
                driftReportRepository, driftRepository, null);
        ReflectionTestUtils.setField(service, "startupEnabled", true);
        ReflectionTestUtils.setField(service, "retentionCount", 100);
        ReflectionTestUtils.setField(service, "autoApplyPathDrift", false);
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
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.HASH_MISMATCH));
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
    @DisplayName("D20 : soft-deleted 자원의 마커는 ORPHAN 분류 제외")
    void scan_softDeletedNotOrphan(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("deleted.iso");
        Files.writeString(iso, "x");
        writeMarker(iso, MarkerLayout.SIDECAR, 77L, "hash");
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findSoftDeletedResourceIds()).willReturn(Set.of(77L));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).isEmpty();
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
    @DisplayName("apply : PATH_DRIFT 외 종류 → DriftAutoApplyNotAllowedException")
    void apply_nonPathDrift_throws() {
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.SIGNATURE_INVALID)
                .oldPath("/x").detectedAt(Instant.now()).build();
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        assertThatThrownBy(() -> service.apply(1L))
                .isInstanceOf(DriftAutoApplyNotAllowedException.class);
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
    @DisplayName("MK3-1 : ghost markable 1건 → GHOST_DB_ROW drift 발생 + auto-apply OFF default 라 자동 정리 안 됨")
    void scan_ghostDriftReported(@TempDir Path tmp) {
        Markable ghost = isoAt(99L, tmp.resolve("removed.iso")); // 파일도 마커도 없음
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findGhostMarkables()).willReturn(List.of(ghost));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDrifts()).singleElement()
                .satisfies(d -> {
                    assertThat(d.getKind()).isEqualTo(DriftKind.GHOST_DB_ROW);
                    assertThat(d.getResourceId()).isEqualTo(99L);
                    assertThat(d.getNewPath()).isNull();
                });
        // auto-apply-ghost-row default false → applyGhostClear 호출되지 않음
        verify(isoScanner, never()).applyGhostClear(99L);
    }

    @Test
    @DisplayName("MK3-1 : auto-apply-ghost-row=true 시 scan 직후 applyGhostClear 자동 호출")
    void scan_ghostAutoApplied(@TempDir Path tmp) {
        ReflectionTestUtils.setField(service, "autoApplyGhostRow", true);
        Markable ghost = isoAt(99L, tmp.resolve("removed.iso"));
        given(isoScanner.findActiveMarkables()).willReturn(List.of());
        given(isoScanner.findGhostMarkables()).willReturn(List.of(ghost));

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
}
