package com.example.serverprovision.maintenance.reconciliation.service.recheck;

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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * S6-3-3 — [다시 점검] 확인 축의 단위 검증.
 * 해소=카드 제거만 / 잔존=카드 불변 / ORPHAN 거짓 해소 방지 / direct POST 안전망.
 */
class DriftRecheckTest {

    private final MarkableScanner scanner = mock(MarkableScanner.class);
    private final DriftRepository driftRepository = mock(DriftRepository.class);
    private final ProvisionMarkerService markerService = withSecret();

    private static ProvisionMarkerService withSecret() {
        ProvisionMarkerService s = new ProvisionMarkerService();
        ReflectionTestUtils.setField(s, "secret", "test-secret");
        return s;
    }

    private DriftRecheckService service() {
        given(scanner.supportedType()).willReturn(ResourceType.OS_ISO);
        return new DriftRecheckService(
                List.of(scanner),
                List.of(new MissingRecheck(markerService), new SignatureInvalidRecheck(markerService),
                        new OrphanRecheck(markerService)),
                driftRepository);
    }

    private Drift driftInReport(Long id, DriftKind kind, String oldPath) {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(10).deep(false).totalChecked(1).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(kind)
                .oldPath(oldPath).newPath(null).detectedAt(Instant.now()).build();
        report.addDrift(drift);
        ReflectionTestUtils.setField(drift, "id", id);
        given(driftRepository.findById(id)).willReturn(Optional.of(drift));
        return drift;
    }

    private Markable activeIso(Path path) {
        Markable m = mock(Markable.class);
        given(m.getResourcePath()).willReturn(path);
        given(m.getMarkerLayout()).willReturn(MarkerLayout.SIDECAR);
        return m;
    }

    private void writeValidMarker(Path resourcePath, Long id) {
        MarkerContent unsigned = new MarkerContent(
                ResourceType.OS_ISO.name(), id, Map.of(), Instant.now(), "h", null);
        markerService.write(resourcePath, MarkerLayout.SIDECAR,
                unsigned.withSignature(markerService.computeSignature(unsigned)));
    }

    @Test
    @DisplayName("자원 소실 해소 : 파일·마커를 복원해 두면 recheck 가 카드를 제거")
    void missing_resolvedAfterRestore(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso");
        Files.writeString(iso, "body");
        writeValidMarker(iso, 42L);
        Markable resource = activeIso(iso);
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));
        Drift drift = driftInReport(1L, DriftKind.MISSING, iso.toString());

        boolean resolved = service().recheck(1L);

        assertThat(resolved).isTrue();
        assertThat(drift.getReport().getDrifts()).isEmpty(); // 카드 제거
    }

    @Test
    @DisplayName("자원 소실 잔존 : 여전히 본체가 없으면 카드 불변 (필드 갱신도 없음 — 불변 설계)")
    void missing_stillBroken(@TempDir Path tmp) throws Exception {
        Path iso = tmp.resolve("dvd.iso"); // 본체 없음
        writeValidMarker(iso, 42L);
        Markable resource = activeIso(iso);
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));
        Drift drift = driftInReport(2L, DriftKind.MISSING, iso.toString());

        boolean resolved = service().recheck(2L);

        assertThat(resolved).isFalse();
        assertThat(drift.getReport().getDrifts()).containsExactly(drift); // 카드 유지
        assertThat(drift.getKind()).isEqualTo(DriftKind.MISSING);          // 재분류 없음
    }

    @Test
    @DisplayName("미등록 마커 : 마커가 그대로 주인 없이 남아 있으면 해소 아님 — 거짓 해소 방지")
    void orphan_notFalselyResolved(@TempDir Path tmp) throws Exception {
        Path stray = tmp.resolve("ghost.iso");
        Files.writeString(stray, "body");
        writeValidMarker(stray, 42L);
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.empty());
        given(scanner.findTrashedById(42L)).willReturn(Optional.empty());
        driftInReport(3L, DriftKind.ORPHAN, stray.toString());

        assertThat(service().recheck(3L)).isFalse();
    }

    @Test
    @DisplayName("미등록 마커 해소 : 그 사이 재등록(주인 생김) 또는 마커 정리 → 카드 제거")
    void orphan_resolvedWhenOwnerAppearsOrMarkerGone(@TempDir Path tmp) throws Exception {
        // (a) 주인 생김
        Path stray = tmp.resolve("ghost.iso");
        Files.writeString(stray, "body");
        writeValidMarker(stray, 42L);
        Markable owner = mock(Markable.class);
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.of(owner));
        driftInReport(4L, DriftKind.ORPHAN, stray.toString());
        assertThat(service().recheck(4L)).isTrue();

        // (b) 마커가 정리됨
        given(scanner.findActiveMarkableById(42L)).willReturn(Optional.empty());
        given(scanner.findTrashedById(42L)).willReturn(Optional.empty());
        driftInReport(5L, DriftKind.ORPHAN, tmp.resolve("cleaned.iso").toString()); // 마커 없음
        assertThat(service().recheck(5L)).isTrue();
    }

    @Test
    @DisplayName("안전망 : recheck 미지원 종류(direct POST) → 409")
    void rejectsNonRecheckableKind(@TempDir Path tmp) {
        driftInReport(6L, DriftKind.PATH_DRIFT, tmp.resolve("x.iso").toString());

        assertThatThrownBy(() -> service().recheck(6L))
                .isInstanceOf(DriftResolutionNotAllowedException.class);
    }
}
