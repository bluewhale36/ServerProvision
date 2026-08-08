package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.job.service.BackgroundJobService;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.dto.response.DriftResponse;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftHandling;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftObservation;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftHandlingAction;
import com.example.serverprovision.maintenance.reconciliation.enums.DriftStatus;
import com.example.serverprovision.maintenance.reconciliation.enums.SnoozeWindow;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftSnoozeNotAllowedException;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.GhostDbRowClearResolution;
import com.example.serverprovision.maintenance.reconciliation.service.resolution.PathDriftResolution;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
import com.example.serverprovision.maintenance.reconciliation.exception.ReconciliationAlreadyRunningException;
import com.example.serverprovision.maintenance.reconciliation.repository.DriftHandlingRepository;
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
    private DriftHandlingRepository driftHandlingRepository;

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
        // MK4-1 — 신원이 처음 보이는 문제는 저장 결과가 그대로 이번 회차의 문제로 쓰인다.
        given(driftRepository.save(any(Drift.class))).willAnswer(inv -> inv.getArgument(0));
        driftHandlingRepository = mock(DriftHandlingRepository.class);

        isoScanner = mock(MarkableScanner.class);
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);

        // self proxy 자리는 단위 테스트 범위 외 (async/proxy 경로는 통합 테스트에서 검증). null 주입.
        service = new PathReconciliationService(
                List.of(isoScanner), markerService, backgroundJobService,
                driftReportRepository, driftRepository, driftHandlingRepository,
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

    /**
     * MK4-1 — 보고서가 담는 것이 관측으로 바뀌었다. 회차가 무엇을 봤는지를 묻는 단언은 그대로 두고,
     * 그 관측이 가리키는 문제만 꺼내 본다.
     */
    private static List<Drift> driftsOf(DriftReport report) {
        return report.getObservations().stream().map(DriftObservation::getDrift).toList();
    }

    /**
     * MK4-1 — 문제 하나를 한 회차에 얹는 픽스처. 스캔이 {@code linkObservations} 에서 만드는 것과 같은 모양.
     */
    private static DriftObservation observationOf(Drift drift) {
        return DriftObservation.builder()
                .drift(drift)
                .observedAt(drift.getFirstDetectedAt())
                .oldPath(drift.getOldPath())
                .newPath(drift.getNewPath())
                .detail(drift.getDetail())
                .observedHash(drift.getObservedHash())
                .build();
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
        assertThat(saved.getDetectedDriftCount()).isZero();
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
        assertThat(driftsOf(saved)).singleElement()
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
        assertThat(driftsOf(saved)).singleElement()
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
        assertThat(driftsOf(saved)).singleElement()
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
        assertThat(driftsOf(saved)).singleElement()
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
        assertThat(driftsOf(saved)).singleElement()
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
        assertThat(driftsOf(saved)).singleElement()
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
        assertThat(driftsOf(saved)).singleElement()
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
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
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
    @DisplayName("MK4-1 apply(PATH_DRIFT) : scanner.applyDriftedPath 호출 + 문제는 해결로 닫히고 지난 회차 기록은 남는다")
    void apply_pathDrift_success(@TempDir Path tmp) {
        Path newPath = tmp.resolve("new.iso");
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(100).deep(false).totalChecked(1).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.PATH_DRIFT)
                .oldPath("/old").newPath(newPath.toString()).firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        report.addObservation(observationOf(drift));
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        service.apply(1L);

        verify(isoScanner, times(1)).applyDriftedPath(42L, newPath);
        assertThat(drift.getStatus()).isEqualTo(DriftStatus.RESOLVED);
        assertThat(drift.getResolvedBy()).isEqualTo(DriftHandlingAction.APPLY);
        // 종전에는 여기서 보고서의 자식이 사라졌다. 그 물리 삭제가 지난 회차의 건수를 사후에 줄이던 원인이라,
        // 이제 회차 기록은 그대로 두고 문제만 닫는다.
        assertThat(driftsOf(report)).containsExactly(drift);
        assertThat(report.getDetectedDriftCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("MK4-1 apply : 처리 이력에 되돌리기 값(처리 전 경로 · 옮겨 둔 위치)이 함께 남는다")
    void apply_recordsHandlingHistory(@TempDir Path tmp) {
        Path newPath = tmp.resolve("new.iso");
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.PATH_DRIFT)
                .oldPath("/old").newPath(newPath.toString())
                .firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        service.apply(1L);

        var captor = org.mockito.ArgumentCaptor.forClass(DriftHandling.class);
        verify(driftHandlingRepository).save(captor.capture());
        DriftHandling handling = captor.getValue();
        assertThat(handling.getAction()).isEqualTo(DriftHandlingAction.APPLY);
        assertThat(handling.isReversible()).isTrue();
        assertThat(handling.getPreviousPath()).isEqualTo("/old");
        assertThat(handling.getMovedToPath()).isEqualTo(newPath.toString());
    }

    @Test
    @DisplayName("apply : 시스템 해결 불가 종류 (mode=NONE) → 409 예외 (manuallyResolvable SSOT)")
    void apply_nonPathDrift_throws() {
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.SIGNATURE_INVALID)
                .oldPath("/x").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
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
                .oldPath("/x").newPath("/y").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
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
    @DisplayName("MK4-1 snooze : 기간과 사유를 받아 목록에서 내리되 기록은 남긴다 (종전 '보고 닫기' 대체)")
    void snooze_holdsWithReason() {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(50).deep(false).totalChecked(1).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.MISSING)
                .oldPath("/x").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        report.addObservation(observationOf(drift));
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        service.snooze(1L, SnoozeWindow.DAYS_7, "교체 부품 입고 대기");

        assertThat(drift.getStatus()).isEqualTo(DriftStatus.SNOOZED);
        assertThat(drift.getSnoozeReason()).isEqualTo("교체 부품 입고 대기");
        assertThat(drift.getSnoozeUntil()).isNotNull();
        assertThat(drift.isListed(Instant.now())).isFalse();
        // 목록에서 내려가도 회차 기록은 남는다 — 나중에 왜 방치됐는지 설명하는 근거가 된다.
        assertThat(driftsOf(report)).containsExactly(drift);
        verify(driftHandlingRepository).save(any(DriftHandling.class));
    }

    @Test
    @DisplayName("MK4-1 보관 : 이미 해결된 드리프트에 보관 요청 → 409 (direct POST 안전망)")
    void snooze_alreadyResolved_throws() {
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.MISSING)
                .oldPath("/x").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        drift.resolve(Instant.now(), DriftHandlingAction.APPLY);
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        assertThatThrownBy(() -> service.snooze(1L, SnoozeWindow.DAYS_7, "사유"))
                .isInstanceOf(DriftSnoozeNotAllowedException.class)
                .hasMessageContaining("이미 해결된");
    }

    @Test
    @DisplayName("MK4-1 apply : 이미 해결된 문제를 다시 해결 → 409 (지난 보고서를 열면 버튼이 눈앞에 있다)")
    void apply_alreadyResolved_throws() {
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.PATH_DRIFT)
                .oldPath("/old").newPath("/new").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        drift.resolve(Instant.now(), DriftHandlingAction.APPLY);
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        // 화면의 tooltip 과 서버의 거절 사유가 같은 문장이어야 한다 — 조건을 두 곳에 복붙하면 갈라진다.
        String viewReason = PathReconciliationService.toDriftResponse(drift).resolveBlockReason();
        assertThatThrownBy(() -> service.apply(1L))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessage(viewReason);
        verify(isoScanner, never()).applyDriftedPath(org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("MK4-1 : 보관 중인 드리프트는 해결을 막지 않는다 (미뤄 둔 것을 바로 처리하는 건 정상 흐름)")
    void snoozedProblem_isStillResolvable() {
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.PATH_DRIFT)
                .oldPath("/old").newPath("/new").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        drift.snooze(SnoozeWindow.DAYS_7, "사유", Instant.now());

        assertThat(drift.resolveBlockReason()).isNull();
        assertThat(PathReconciliationService.toDriftResponse(drift).resolveBlockReason()).isNull();
    }

    @Test
    @DisplayName("MK4-1 : 화면이 버튼을 막는 사유와 서버가 거절하는 사유가 같은 문장이다 (단일 SSOT)")
    void snoozeBlockReason_isSharedBetweenViewAndGuard() {
        Drift open = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.MISSING)
                .oldPath("/x").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        ReflectionTestUtils.setField(open, "id", 1L);
        // 열린 문제는 막을 이유가 없다 — 화면의 버튼도 살아 있어야 한다.
        assertThat(PathReconciliationService.toDriftResponse(open).snoozeBlockReason()).isNull();

        open.snooze(SnoozeWindow.DAYS_7, "사유", Instant.now());
        given(driftRepository.findById(1L)).willReturn(Optional.of(open));

        // 두 경로가 같은 도메인 메서드를 보므로 문장이 갈라질 자리가 없다. 조건을 양쪽에 복붙하면
        // 화면은 열어 두고 서버만 거절하는(또는 그 반대) 어긋남이 생긴다.
        String viewReason = PathReconciliationService.toDriftResponse(open).snoozeBlockReason();
        assertThatThrownBy(() -> service.snooze(1L, SnoozeWindow.DAYS_30, "또 미루기"))
                .isInstanceOf(DriftSnoozeNotAllowedException.class)
                .hasMessage(viewReason);
    }

    @Test
    @DisplayName("MK4-1 snooze : 존재하지 않는 driftId → DriftNotFoundException")
    void snooze_notFound() {
        given(driftRepository.findById(999L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.snooze(999L, SnoozeWindow.DAYS_7, "사유"))
                .isInstanceOf(DriftNotFoundException.class);
    }

    // ==== HF4-5 — RESOURCE_REPLICA 탐지 시나리오 ========================

    @Test
    @DisplayName("HF4-5 : 원본 정상 + 동일 신원 사본 → RESOURCE_REPLICA (원본=oldPath, 사본=newPath)")
    void scan_resourceDuplicated(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "fake-iso");
        writeMarker(iso, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Path copy = tmp.resolve("backup_dvd.iso");
        Files.writeString(copy, "fake-iso");
        writeMarker(copy, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable m = isoAt(42L, iso);
        given(m.displayName()).willReturn("Rocky dvd.iso");
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.RESOURCE_REPLICA);
            assertThat(d.getOldPath()).isEqualTo(iso.toString());
            assertThat(d.getNewPath()).isEqualTo(copy.toString());
            // R9-5 관례 — 스캔 시점 실명 스냅샷 동반
            assertThat(d.getDisplayName()).isEqualTo("Rocky dvd.iso");
        });
    }

    @Test
    @DisplayName("HF4-5 : 사본 2개 → 사본 경로당 1건씩 2건 (plan D2 — N지선다 아님)")
    void scan_resourceDuplicated_multipleCopies(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "fake-iso");
        writeMarker(iso, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Path copy1 = tmp.resolve("backup1_dvd.iso");
        Files.writeString(copy1, "fake-iso");
        writeMarker(copy1, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Path copy2 = tmp.resolve("backup2_dvd.iso");
        Files.writeString(copy2, "fake-iso");
        writeMarker(copy2, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable m = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(driftsOf(saved)).hasSize(2).allSatisfy(d -> {
            assertThat(d.getKind()).isEqualTo(DriftKind.RESOURCE_REPLICA);
            assertThat(d.getOldPath()).isEqualTo(iso.toString());
        });
        assertThat(driftsOf(saved)).extracting(Drift::getNewPath)
                .containsExactlyInAnyOrder(copy1.toString(), copy2.toString());
    }

    @Test
    @DisplayName("HF4-5 판정 순서 : 원본 소실 + 사본 존재 → 기존 PATH_DRIFT 유지, RESOURCE_REPLICA 미발동 (plan D1)")
    void scan_originalLost_staysPathDrift(@TempDir Path tmp) throws Exception {
        Path oldIso = tmp.resolve("old/dvd.iso"); // DB 경로 — 파일도 마커도 없음
        Files.createDirectories(oldIso.getParent());
        Path copy = tmp.resolve("new/dvd.iso");
        Files.createDirectories(copy.getParent());
        Files.writeString(copy, "fake-iso");
        writeMarker(copy, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable m = isoAt(42L, oldIso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));
        ReflectionTestUtils.setField(service, "extraRootsCsv", tmp.toString());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(driftsOf(saved)).singleElement()
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.PATH_DRIFT));
    }

    @Test
    @DisplayName("HF4-5 판정 순서 : 원본 본체 부재(자체 드리프트) → MISSING 우선, 중복 미보고 (완전 정상 조건)")
    void scan_unhealthyOriginal_suppressesDuplicate(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        writeMarker(iso, MarkerLayout.SIDECAR, 42L, "hash-abc"); // 마커만 — 본체 없음
        Path copy = tmp.resolve("backup_dvd.iso");
        Files.writeString(copy, "fake-iso");
        writeMarker(copy, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable m = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(driftsOf(saved)).singleElement()
                .satisfies(d -> assertThat(d.getKind()).isEqualTo(DriftKind.MISSING));
    }

    @Test
    @DisplayName("HF4-5 : 마커만 복사된 사본(본체 없음)은 미보고 — 알려진 한계의 의도 고정 (plan §8)")
    void scan_markerOnlyCopy_ignored(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "fake-iso");
        writeMarker(iso, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Path copy = tmp.resolve("backup_dvd.iso");
        writeMarker(copy, MarkerLayout.SIDECAR, 42L, "hash-abc"); // 사본 마커만 — 본체 없음
        Markable m = isoAt(42L, iso);
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDetectedDriftCount()).isZero();
    }

    @Test
    @DisplayName("HF4-5 : RESOURCE_REPLICA 는 표준 [적용] 불가 (mode=NONE — 택일 전용 endpoint 로만 해소)")
    void apply_rejectsResourceDuplicated() {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(0).deep(false).totalChecked(1).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L)
                .kind(DriftKind.RESOURCE_REPLICA)
                .oldPath("/opt/dvd.iso").newPath("/opt/backup_dvd.iso")
                .firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        report.addObservation(observationOf(drift));
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        assertThatThrownBy(() -> service.apply(1L))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("자원 중복 존재");
        assertThat(drift.getStatus()).isEqualTo(DriftStatus.OPEN); // 거절됐으니 문제는 그대로 열려 있다
    }

    /**
     * MK4-1 — 스캔은 보고서를 두 번 저장한다. 먼저 회차 메타데이터를 저장해 식별자를 얻고, 관측을
     * 모두 얹은 뒤 한 번 더 저장한다. 마지막 저장분이 그 회차의 완성본이다.
     */
    private DriftReport captureSavedReport() {
        var captor = org.mockito.ArgumentCaptor.forClass(DriftReport.class);
        verify(driftReportRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
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
        assertThat(driftsOf(saved)).singleElement()
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
    @DisplayName("MK3-1 → MK4-1 : apply(GHOST_DB_ROW) → scanner.applyGhostClear 호출 + 문제는 해결로 닫힘")
    void apply_ghostRow_success() {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(50).deep(false).totalChecked(0).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(99L).kind(DriftKind.GHOST_DB_ROW)
                .oldPath("/missing").newPath(null).firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        report.addObservation(observationOf(drift));
        ReflectionTestUtils.setField(drift, "id", 7L);
        given(driftRepository.findById(7L)).willReturn(Optional.of(drift));

        service.apply(7L);

        verify(isoScanner, times(1)).applyGhostClear(99L);
        assertThat(drift.getStatus()).isEqualTo(DriftStatus.RESOLVED);
        assertThat(driftsOf(report)).containsExactly(drift);
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
        assertThat(driftsOf(saved)).hasSize(1);
        Drift drift = driftsOf(saved).iterator().next();
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
        assertThat(driftsOf(saved)).hasSize(1);
        Drift drift = driftsOf(saved).iterator().next();
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
                .oldPath("/x").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
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
        assertThat(driftsOf(captureSavedReport())).hasSize(1); // 기록 보존
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
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
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
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
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
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
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
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
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

        assertThat(driftsOf(captureSavedReport())).isEmpty();
    }

    @Test
    @DisplayName("S6-2-2 : 메타 자원 scanner 는 분류 패스에서 제외 — findTrashed 미호출, NPE·유령 오탐 없음")
    void scan_metaScannerExcluded(@TempDir Path tmp) {
        MarkableScanner metaScanner = mock(MarkableScanner.class);
        given(metaScanner.supportedType()).willReturn(ResourceType.OS_IMAGE);
        given(metaScanner.findActiveMarkables()).willReturn(List.of());
        PathReconciliationService svc = new PathReconciliationService(
                List.of(isoScanner, metaScanner), markerService, backgroundJobService,
                driftReportRepository, driftRepository, driftHandlingRepository,
                List.of(new PathDriftResolution(), new GhostDbRowClearResolution()), null);
        ReflectionTestUtils.setField(svc, "retentionCount", 100);
        ReflectionTestUtils.setField(svc, "extraRootsCsv", tmp.toString());
        given(isoScanner.findActiveMarkables()).willReturn(List.of());

        ReflectionTestUtils.invokeMethod(svc, "performScan", false, "job-1");

        verify(metaScanner, never()).findTrashed();
        assertThat(driftsOf(captureSavedReport())).isEmpty();
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
        assertThat(driftsOf(captureSavedReport())).isEmpty();
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
                driftReportRepository, driftRepository, driftHandlingRepository,
                List.of(new PathDriftResolution(), new GhostDbRowClearResolution()), null);
        ReflectionTestUtils.setField(svc, "retentionCount", 100);
        ReflectionTestUtils.setField(svc, "extraRootsCsv", "");

        ReflectionTestUtils.invokeMethod(svc, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
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
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
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
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
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
        assertThat(driftsOf(saved)).extracting(Drift::getKind).containsExactlyInAnyOrder(
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
                driftReportRepository, driftRepository, driftHandlingRepository,
                List.of(new PathDriftResolution(), new GhostDbRowClearResolution()), null);
        ReflectionTestUtils.setField(svc, "retentionCount", 100);
        ReflectionTestUtils.setField(svc, "extraRootsCsv", "");

        ReflectionTestUtils.invokeMethod(svc, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(driftsOf(saved)).singleElement().satisfies(d -> {
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

    // ==== HF4-4 → MK4-1 — 회차 기록의 불변성 =========================================

    @Test
    @DisplayName("HF4-4 → MK4-1 : 스캔 저장 시 탐지 건수가 스냅샷되고, 해결 후에도 회차 기록이 줄지 않는다")
    void scan_snapshotsDetectedDriftCount(@TempDir Path tmp) {
        // 마커가 어디에도 없는 자원 1건 → MISSING drift 1건 탐지
        Markable m = isoAt(42L, tmp.resolve("dvd.iso"));
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDetectedDriftCount()).isEqualTo(1);
        assertThat(saved.getObservations()).hasSize(1);

        // 해결 재현 — 문제만 닫힌다. 종전에는 이 시점에 보고서의 자식이 물리 삭제되어 지난 회차의
        // 건수가 사후에 줄었고, 스냅샷 컬럼이 그 왜곡을 가리는 유일한 방벽이었다.
        Drift problem = driftsOf(saved).getFirst();
        problem.resolve(Instant.now(), DriftHandlingAction.APPLY);
        assertThat(saved.getObservations()).hasSize(1);
        assertThat(saved.getDetectedDriftCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("MK4-1 : 응답 매핑 — 해결한 문제도 그 회차의 관측으로 남아 탐지 수와 목록이 어긋나지 않는다")
    void latestReport_keepsResolvedObservations() {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(100).deep(false).totalChecked(5).build();
        Drift d1 = Drift.builder().resourceType(ResourceType.OS_ISO).resourceId(1L).kind(DriftKind.MISSING)
                .oldPath("/a").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        Drift d2 = Drift.builder().resourceType(ResourceType.OS_ISO).resourceId(2L).kind(DriftKind.MISSING)
                .oldPath("/b").firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        report.addObservation(observationOf(d1));
        report.addObservation(observationOf(d2));
        d1.resolve(Instant.now(), DriftHandlingAction.APPLY); // 1건 해결 재현
        given(driftReportRepository.findFirstByOrderByScannedAtDesc()).willReturn(Optional.of(report));

        var response = service.latestReport().orElseThrow();

        assertThat(response.detectedDriftCount()).isEqualTo(2);
        assertThat(response.drifts()).hasSize(2);
        // 해결분은 상태로 구분된다 — 목록에서 사라져 건수만 맞지 않게 되던 종전과 다르다.
        assertThat(response.drifts()).extracting(DriftResponse::status)
                .containsExactlyInAnyOrder(DriftStatus.RESOLVED, DriftStatus.OPEN);
    }

    @Test
    @DisplayName("HF4-4 → MK4-1 : saga 임시 보고서(persistAndForcedApply)도 관측 1건을 남긴다")
    void persistAndForcedApply_snapshotsDetectedCount(@TempDir Path tmp) {
        Path newPath = tmp.resolve("new.iso");
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.PATH_DRIFT)
                .oldPath("/old").newPath(newPath.toString()).firstDetectedAt(Instant.now()).lastObservedAt(Instant.now()).build();
        // mock save 는 실 영속화가 아니라 drift id 가 null — forced apply 의 findById 를 그대로 매칭
        given(driftRepository.findById(any())).willReturn(Optional.of(drift));

        service.persistAndForcedApply(drift);

        DriftReport saved = captureSavedReport();
        assertThat(saved.getDetectedDriftCount()).isEqualTo(1);
        assertThat(driftsOf(saved)).containsExactly(drift);
        assertThat(drift.getStatus()).isEqualTo(DriftStatus.RESOLVED);
    }

    // ==== MK4-1 — 문제와 관측의 분리 =================================================

    /**
     * 같은 신원의 열린 문제가 이미 있는 상황을 재현한다. 저장소가 그것을 찾아 돌려주게 해서,
     * 스캔이 새 문제를 만드는 대신 기존 문제에 관측을 잇는지 본다.
     */
    private Drift givenOpenProblem(DriftKind kind, Long resourceId, Instant firstDetectedAt) {
        Drift existing = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(resourceId).kind(kind)
                .oldPath("/처음/본/경로").firstDetectedAt(firstDetectedAt).lastObservedAt(firstDetectedAt)
                .build();
        ReflectionTestUtils.setField(existing, "id", 100L);
        given(driftRepository.findFirstByResourceTypeAndResourceIdAndKindAndStatusNot(
                ResourceType.OS_ISO, resourceId, kind, DriftStatus.RESOLVED))
                .willReturn(Optional.of(existing));
        given(driftRepository.findByStatusNot(DriftStatus.RESOLVED)).willReturn(List.of(existing));
        return existing;
    }

    @Test
    @DisplayName("MK4-1 : 같은 문제를 두 번째로 보면 새 문제를 만들지 않고 관측만 늘린다")
    void scan_sameIdentityTwice_becomesOneProblemTwoObservations(@TempDir Path tmp) {
        Instant firstDetectedAt = Instant.now().minusSeconds(3600);
        Drift existing = givenOpenProblem(DriftKind.MISSING, 42L, firstDetectedAt);
        Markable m = isoAt(42L, tmp.resolve("dvd.iso")); // 마커도 본체도 없음 → MISSING
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        // 새 문제를 저장하지 않았다 — 이것이 "회차마다 무관한 행이 생기던" 종전과의 차이다.
        verify(driftRepository, never()).save(any(Drift.class));
        assertThat(existing.getObservationCount()).isEqualTo(2);
        assertThat(existing.getFirstDetectedAt()).isEqualTo(firstDetectedAt); // 처음 본 시각은 불변
        assertThat(existing.getLastObservedAt()).isAfter(firstDetectedAt);
        assertThat(driftsOf(captureSavedReport())).containsExactly(existing);
    }

    @Test
    @DisplayName("MK4-1 : 처음 보는 신원은 새 문제가 되고 처음 본 시각 = 이번 점검 시각")
    void scan_newIdentity_createsProblem(@TempDir Path tmp) {
        Instant before = Instant.now();
        Markable m = isoAt(42L, tmp.resolve("dvd.iso"));
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        Drift created = driftsOf(captureSavedReport()).getFirst();
        assertThat(created.getKind()).isEqualTo(DriftKind.MISSING);
        assertThat(created.getObservationCount()).isEqualTo(1);
        assertThat(created.getFirstDetectedAt()).isAfterOrEqualTo(before);
        assertThat(created.getFirstDetectedAt()).isEqualTo(created.getLastObservedAt());
        assertThat(created.getStatus()).isEqualTo(DriftStatus.OPEN);
    }

    @Test
    @DisplayName("MK4-1 : 이번 점검이 커버한 종류인데 안 보이면 자동 해소 (운영자가 직접 되돌린 경우)")
    void scan_unobservedCoveredKind_autoResolves(@TempDir Path tmp) {
        Drift existing = givenOpenProblem(DriftKind.MISSING, 42L, Instant.now().minusSeconds(3600));
        given(isoScanner.findActiveMarkables()).willReturn(List.of()); // 이번엔 아무것도 안 보임

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        assertThat(existing.getStatus()).isEqualTo(DriftStatus.RESOLVED);
        assertThat(existing.getResolvedBy()).isEqualTo(DriftHandlingAction.SCAN_UNOBSERVED);
    }

    @Test
    @DisplayName("MK4-1 : 일반 점검은 내용 변경 문제를 자동 해소하지 않는다 (커버하지 않는 종류라 '안 보인' 것이 아니다)")
    void quickScan_doesNotResolveHashMismatch(@TempDir Path tmp) {
        Drift existing = givenOpenProblem(DriftKind.HASH_MISMATCH, 42L, Instant.now().minusSeconds(3600));
        given(isoScanner.findActiveMarkables()).willReturn(List.of());

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1"); // deep=false

        // 일반 점검은 내용 지문을 재계산하지 않으므로 이 문제를 볼 수 없다. 여기서 닫아 버리면
        // 정밀 점검마다 되살아나기를 반복해 목록이 요동친다.
        assertThat(existing.getStatus()).isEqualTo(DriftStatus.OPEN);
    }

    @Test
    @DisplayName("MK4-1 : 정밀 점검은 내용 변경 문제도 커버하므로 안 보이면 자동 해소한다")
    void deepScan_resolvesUnobservedHashMismatch(@TempDir Path tmp) {
        Drift existing = givenOpenProblem(DriftKind.HASH_MISMATCH, 42L, Instant.now().minusSeconds(3600));
        given(isoScanner.findActiveMarkables()).willReturn(List.of());

        ReflectionTestUtils.invokeMethod(service, "performScan", true, "job-1"); // deep=true

        assertThat(existing.getStatus()).isEqualTo(DriftStatus.RESOLVED);
        assertThat(existing.getResolvedBy()).isEqualTo(DriftHandlingAction.SCAN_UNOBSERVED);
    }

    @Test
    @DisplayName("MK4-1 : 보관 기간이 지났고 문제가 여전히 보이면 점검이 다시 연다")
    void scan_reopensExpiredSnooze(@TempDir Path tmp) {
        Drift existing = givenOpenProblem(DriftKind.MISSING, 42L, Instant.now().minusSeconds(3600));
        existing.snooze(SnoozeWindow.DAYS_7, "부품 입고 대기", Instant.now().minus(java.time.Duration.ofDays(8)));
        assertThat(existing.getStatus()).isEqualTo(DriftStatus.SNOOZED);
        Markable m = isoAt(42L, tmp.resolve("dvd.iso")); // 마커도 본체도 없음 → 이번에도 MISSING
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        assertThat(existing.getStatus()).isEqualTo(DriftStatus.OPEN);
        assertThat(existing.getSnoozeUntil()).isNull();
    }

    @Test
    @DisplayName("MK4-1 : 두고 보던 문제가 그 사이 사라졌으면 다시 열지 않고 해결로 닫는다")
    void scan_snoozedButGone_resolvesInsteadOfReopening(@TempDir Path tmp) {
        Drift existing = givenOpenProblem(DriftKind.MISSING, 42L, Instant.now().minusSeconds(3600));
        existing.snooze(SnoozeWindow.DAYS_7, "부품 입고 대기", Instant.now().minus(java.time.Duration.ofDays(8)));
        given(isoScanner.findActiveMarkables()).willReturn(List.of()); // 그 사이 운영자가 정리

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        // 만료됐다고 무조건 되살리면 이미 없어진 문제를 목록에 올리게 된다. 자동 해소가 먼저다.
        assertThat(existing.getStatus()).isEqualTo(DriftStatus.RESOLVED);
        assertThat(existing.getResolvedBy()).isEqualTo(DriftHandlingAction.SCAN_UNOBSERVED);
    }

    @Test
    @DisplayName("MK4-1 : 조건형 보관('다음 정밀 점검까지')은 정밀 점검이 관측하는 순간 풀린다")
    void deepScan_releasesConditionalSnooze(@TempDir Path tmp) {
        Drift existing = givenOpenProblem(DriftKind.MISSING, 42L, Instant.now().minusSeconds(3600));
        existing.snooze(SnoozeWindow.UNTIL_NEXT_DEEP_SCAN, "다음 정밀 점검에서 다시 보자", Instant.now());
        assertThat(existing.getSnoozeUntil()).isNull(); // 시각이 아니라 사건으로 풀린다
        Markable m = isoAt(42L, tmp.resolve("dvd.iso"));
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", true, "job-1");

        assertThat(existing.getStatus()).isEqualTo(DriftStatus.OPEN);
    }

    @Test
    @DisplayName("MK4-1 : 해결된 문제가 다시 발견되면 그 행을 되살리지 않고 새 문제로 만든다")
    void scan_rediscoveredAfterResolve_createsNewProblem(@TempDir Path tmp) {
        // 저장소는 RESOLVED 를 제외하고 찾으므로 해결분은 조회에 걸리지 않는다 — 그 계약의 확인.
        given(driftRepository.findFirstByResourceTypeAndResourceIdAndKindAndStatusNot(
                any(), any(), any(), org.mockito.ArgumentMatchers.eq(DriftStatus.RESOLVED)))
                .willReturn(Optional.empty());
        Markable m = isoAt(42L, tmp.resolve("dvd.iso"));
        given(isoScanner.findActiveMarkables()).willReturn(List.of(m));

        ReflectionTestUtils.invokeMethod(service, "performScan", false, "job-1");

        verify(driftRepository, times(1)).save(any(Drift.class));
        assertThat(driftsOf(captureSavedReport()).getFirst().getObservationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("MK4-1 : 재관측은 최신 스냅샷(경로 · 상세)을 덮어쓰되 처음 본 시각은 지킨다")
    void scan_observationUpdatesLatestSnapshot(@TempDir Path tmp) throws Exception {
        Instant firstDetectedAt = Instant.now().minusSeconds(3600);
        Drift existing = givenOpenProblem(DriftKind.PATH_DRIFT, 42L, firstDetectedAt);
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

        assertThat(existing.getOldPath()).isEqualTo(oldIso.toString());
        assertThat(existing.getNewPath()).isEqualTo(newIso.toString());
        assertThat(existing.getFirstDetectedAt()).isEqualTo(firstDetectedAt);
        // 회차별 사실은 관측에 따로 남는다 — 나중에 이동 경로를 되짚을 수 있다.
        assertThat(captureSavedReport().getObservations()).singleElement()
                .satisfies(o -> {
                    assertThat(o.getOldPath()).isEqualTo(oldIso.toString());
                    assertThat(o.getNewPath()).isEqualTo(newIso.toString());
                });
    }
}
