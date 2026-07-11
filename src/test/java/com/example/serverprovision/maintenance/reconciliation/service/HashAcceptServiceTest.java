package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.exception.TypedNameMismatchException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * S6-3-4 — 내용 변경 수용의 단위 검증.
 * 승인 의식(자원명)·스냅샷 대조(재변경 차단)·정본 갱신·감사까지의 계약을 고정한다.
 */
class HashAcceptServiceTest {

    private MarkableScanner scanner;
    private BackgroundJobService backgroundJobService;
    private DriftRepository driftRepository;
    private ProvisionMarkerService markerService;
    private PathReconciliationService reconciliationService;
    private HashAcceptService service;

    @BeforeEach
    void setUp() {
        scanner = mock(MarkableScanner.class);
        given(scanner.supportedType()).willReturn(ResourceType.OS_ISO);
        backgroundJobService = mock(BackgroundJobService.class);
        given(backgroundJobService.register(any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.<List<String>>any())).willReturn("job-1");
        driftRepository = mock(DriftRepository.class);
        markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "test-secret");
        reconciliationService = mock(PathReconciliationService.class);
        given(reconciliationService.isResolutionEnabled()).willReturn(true);
        service = new HashAcceptService(
                List.of(scanner), markerService, backgroundJobService, driftRepository,
                reconciliationService, null);
        ReflectionTestUtils.setField(service, "self", service);
    }

    private Drift hashDriftInReport(Long id, String observedHash) {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(10).deep(true).totalChecked(1).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.HASH_MISMATCH)
                .oldPath("/iso/dvd.iso").newPath(null).detectedAt(Instant.now())
                .observedHash(observedHash)
                .build();
        report.addDrift(drift);
        ReflectionTestUtils.setField(drift, "id", id);
        given(driftRepository.findById(id)).willReturn(Optional.of(drift));
        return drift;
    }

    private Markable activeIso(Path path, String displayName) {
        Markable m = mock(Markable.class);
        given(m.getResourceId()).willReturn(42L);
        given(m.getResourceType()).willReturn(ResourceType.OS_ISO);
        given(m.getResourcePath()).willReturn(path);
        given(m.getMarkerLayout()).willReturn(MarkerLayout.SIDECAR);
        given(m.displayName()).willReturn(displayName);
        return m;
    }

    private void writeMarker(Path resourcePath, String hash) {
        MarkerContent unsigned = new MarkerContent(
                ResourceType.OS_ISO.name(), 42L, Map.of("k", "v"), Instant.now(), hash, null);
        markerService.write(resourcePath, MarkerLayout.SIDECAR,
                unsigned.withSignature(markerService.computeSignature(unsigned)));
    }

    @Test
    @DisplayName("수용 성공 : 자원명 확인 → 재계산=스냅샷 일치 → 마커 지문·서명 갱신 + 카드 제거 + 완료")
    void acceptsCurrentContent(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "new-content");
        writeMarker(iso, "hash-old"); // 등록 지문은 옛 값
        Drift drift = hashDriftInReport(1L, "hash-new");
        Markable resource = activeIso(iso, "Rocky Linux 9.6 dvd.iso");
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));
        given(scanner.recomputeManifestHash(resource)).willReturn(Optional.of("hash-new"));

        String jobId = service.triggerAccept(1L, "Rocky Linux 9.6 dvd.iso");

        assertThat(jobId).isEqualTo("job-1");
        MarkerContent after = markerService.read(iso, MarkerLayout.SIDECAR);
        assertThat(after.manifestHash()).isEqualTo("hash-new");                     // 정본 갱신
        assertThat(markerService.verifySignature(after)).isTrue();                  // 재서명 유효
        assertThat(after.attributes()).containsEntry("k", "v");                     // 부속 정보 보존
        assertThat(drift.getReport().getDrifts()).isEmpty();                        // 카드 제거
        verify(resource).reissueMarker("hash-new", after.signature());              // DB 반영
        verify(backgroundJobService).complete("job-1",
                Map.of("acceptedResource", "Rocky Linux 9.6 dvd.iso"));
    }

    @Test
    @DisplayName("거절 : 자원명 불일치 → 작업 시작 없이 예외 (승인 의식)")
    void rejectsWrongTypedName(@TempDir Path tmp) {
        hashDriftInReport(2L, "hash-new");
        Markable resource = activeIso(tmp.resolve("dvd.iso"), "Rocky Linux 9.6 dvd.iso");
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));

        assertThatThrownBy(() -> service.triggerAccept(2L, "엉뚱한 이름"))
                .isInstanceOf(TypedNameMismatchException.class);
        verify(backgroundJobService, never()).register(any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.<List<String>>any());
    }

    @Test
    @DisplayName("실패 처리 : 실행 시점 재계산이 감지 스냅샷과 다름 → 정본 갱신 없이 작업 실패 (재변경 차단)")
    void failsWhenContentChangedAgain(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "changed-again");
        writeMarker(iso, "hash-old");
        Drift drift = hashDriftInReport(3L, "hash-new"); // 사용자가 확인한 스냅샷
        Markable resource = activeIso(iso, "Rocky Linux 9.6 dvd.iso");
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));
        given(scanner.recomputeManifestHash(resource)).willReturn(Optional.of("hash-third")); // 또 바뀜

        service.triggerAccept(3L, "Rocky Linux 9.6 dvd.iso");

        verify(backgroundJobService).fail(org.mockito.ArgumentMatchers.eq("job-1"), anyString());
        verify(backgroundJobService, never()).complete(anyString(),
                org.mockito.ArgumentMatchers.<Map<String, String>>any());
        assertThat(markerService.read(iso, MarkerLayout.SIDECAR).manifestHash()).isEqualTo("hash-old"); // 정본 불변
        assertThat(drift.getReport().getDrifts()).containsExactly(drift);                                 // 카드 유지
    }

    @Test
    @DisplayName("거절 : 스냅샷 없는 구형 카드 / 잘못된 종류 → 409 (재점검 유도·안전망)")
    void rejectsStaleOrWrongKind(@TempDir Path tmp) {
        // 스냅샷 없음
        hashDriftInReport(4L, null);
        assertThatThrownBy(() -> service.triggerAccept(4L, "x"))
                .isInstanceOf(DriftResolutionNotAllowedException.class);

        // 다른 종류 (direct POST 안전망)
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(10).deep(false).totalChecked(1).build();
        Drift missing = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.MISSING)
                .oldPath("/x").detectedAt(Instant.now()).build();
        report.addDrift(missing);
        ReflectionTestUtils.setField(missing, "id", 5L);
        given(driftRepository.findById(5L)).willReturn(Optional.of(missing));
        assertThatThrownBy(() -> service.triggerAccept(5L, "x"))
                .isInstanceOf(DriftResolutionNotAllowedException.class);
    }

    @Test
    @DisplayName("거절 : 전면 차단 마스터(resolution-enabled) OFF → 수용도 동결 (관찰 모드 일관성)")
    void rejectsWhenMasterOff(@TempDir Path tmp) {
        given(reconciliationService.isResolutionEnabled()).willReturn(false);
        assertThatThrownBy(() -> service.triggerAccept(9L, "x"))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("resolution-enabled");
    }

    @Test
    @DisplayName("거절 : 같은 자원의 수용이 진행 중이면 다른 카드로도 시작 불가 (자원 단위 중복 차단)")
    void rejectsWhenSameResourceInFlight(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "new-content");
        hashDriftInReport(10L, "hash-new");
        Markable resource = activeIso(iso, "Rocky Linux 9.6 dvd.iso");
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));
        @SuppressWarnings("unchecked")
        java.util.Set<com.example.serverprovision.global.trash.ResourceKey> inFlight =
                (java.util.Set<com.example.serverprovision.global.trash.ResourceKey>)
                        ReflectionTestUtils.getField(service, "inFlight");
        inFlight.add(new com.example.serverprovision.global.trash.ResourceKey(ResourceType.OS_ISO, 42L));

        assertThatThrownBy(() -> service.triggerAccept(10L, "Rocky Linux 9.6 dvd.iso"))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("진행 중");
        verify(backgroundJobService, never()).register(any(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.<List<String>>any());
    }

    @Test
    @DisplayName("경합 안전 순서 : DB 변이 flush(충돌 표면화)가 파일 쓰기보다 먼저 — 충돌 시 마커 불변 + 감사 소실 없음")
    void dbConflictSurfacesBeforeFileWrite(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "new-content");
        writeMarker(iso, "hash-old");
        hashDriftInReport(11L, "hash-new");
        Markable resource = activeIso(iso, "Rocky Linux 9.6 dvd.iso");
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));
        given(scanner.recomputeManifestHash(resource)).willReturn(Optional.of("hash-new"));
        // 동시 dismiss/보고서 정리와의 낙관적 잠금 충돌을 flush 시점 예외로 재현
        org.mockito.Mockito.doThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException("Drift", 11L))
                .when(driftRepository).flush();

        service.triggerAccept(11L, "Rocky Linux 9.6 dvd.iso");

        verify(backgroundJobService).fail(org.mockito.ArgumentMatchers.eq("job-1"), anyString());
        // 파일은 정본화되지 않음 — 종전 순서라면 이미 새 지문으로 덮여 감사 없는 정본화가 됐을 지점
        assertThat(markerService.read(iso, MarkerLayout.SIDECAR).manifestHash()).isEqualTo("hash-old");
    }
}
