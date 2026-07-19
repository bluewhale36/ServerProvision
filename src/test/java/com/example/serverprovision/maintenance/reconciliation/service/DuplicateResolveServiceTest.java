package com.example.serverprovision.maintenance.reconciliation.service;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.entity.DriftReport;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftNotFoundException;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * HF4-5 DuplicateResolveService 단위 테스트.
 * 택일 양 갈래(원본 유지 / 복제본 승격)의 파일·DB 효과 + 실행 직전 가드(stale / 승격 3종 / kind /
 * 비활성 / 전역 OFF) + 파일 삭제 실패의 롤백 경로.
 */
class DuplicateResolveServiceTest {

    private ProvisionMarkerService markerService;
    private DriftRepository driftRepository;
    private PathReconciliationService reconciliationService;
    private MarkableScanner isoScanner;
    private MarkableScanner subprogramScanner;
    private DuplicateResolveService service;

    @BeforeEach
    void setUp() {
        markerService = new ProvisionMarkerService();
        ReflectionTestUtils.setField(markerService, "secret", "test-secret");
        driftRepository = mock(DriftRepository.class);
        reconciliationService = mock(PathReconciliationService.class);
        given(reconciliationService.isResolutionEnabled()).willReturn(true);
        isoScanner = mock(MarkableScanner.class);
        given(isoScanner.supportedType()).willReturn(ResourceType.OS_ISO);
        subprogramScanner = mock(MarkableScanner.class);
        given(subprogramScanner.supportedType()).willReturn(ResourceType.SUBPROGRAM);
        service = new DuplicateResolveService(
                List.of(isoScanner, subprogramScanner), markerService, driftRepository, reconciliationService);
    }

    // ==== 헬퍼 ==========================================================

    private Markable resourceAt(ResourceType type, Long id, Path path, MarkerLayout layout, String hash) {
        Markable m = mock(Markable.class);
        given(m.getResourceId()).willReturn(id);
        given(m.getResourceType()).willReturn(type);
        given(m.getResourcePath()).willReturn(path);
        given(m.getMarkerLayout()).willReturn(layout);
        given(m.getManifestHash()).willReturn(hash);
        given(m.displayName()).willReturn(type.name() + "-" + id);
        return m;
    }

    private void writeMarker(ResourceType type, Path resourcePath, MarkerLayout layout, Long id, String hash) {
        MarkerContent unsigned = new MarkerContent(type.name(), id, Map.of(), Instant.now(), hash, null);
        markerService.write(resourcePath, layout, unsigned.withSignature(markerService.computeSignature(unsigned)));
    }

    private Drift duplicateDrift(ResourceType type, Long id, Path oldPath, Path newPath) {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(0).deep(false).totalChecked(1).build();
        Drift drift = Drift.builder()
                .resourceType(type).resourceId(id).displayName(type.name() + "-" + id)
                .kind(DriftKind.RESOURCE_DUPLICATED)
                .oldPath(oldPath.toString()).newPath(newPath.toString())
                .detectedAt(Instant.now()).build();
        report.addDrift(drift);
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));
        return drift;
    }

    /**
     * SIDECAR 원본+사본 표준 셋업 : 본체 파일 + 서명 마커 각 1쌍.
     */
    private Markable sidecarPair(Path orig, Path copy, String copyHash) throws Exception {
        Files.writeString(orig, "fake-iso");
        writeMarker(ResourceType.OS_ISO, orig, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Files.writeString(copy, "fake-iso");
        writeMarker(ResourceType.OS_ISO, copy, MarkerLayout.SIDECAR, 42L, copyHash);
        Markable resource = resourceAt(ResourceType.OS_ISO, 42L, orig, MarkerLayout.SIDECAR, "hash-abc");
        given(isoScanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));
        return resource;
    }

    private static Path sidecarMarkerOf(Path body) {
        return body.resolveSibling(body.getFileName() + ".provision.json");
    }

    // ==== 양 갈래 성공 ====================================================

    @Test
    @DisplayName("ORIGINAL 유지 : 복제본 본체+마커 삭제, 원본·DB 무변경, 카드 제거")
    void keepOriginal_deletesDuplicate(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("dvd.iso");
        Path copy = tmp.resolve("backup_dvd.iso");
        sidecarPair(orig, copy, "hash-abc");
        Drift drift = duplicateDrift(ResourceType.OS_ISO, 42L, orig, copy);

        service.resolve(1L, DuplicateSurvivor.ORIGINAL);

        assertThat(Files.exists(copy)).isFalse();
        assertThat(Files.exists(sidecarMarkerOf(copy))).isFalse();
        assertThat(Files.exists(orig)).isTrue();
        assertThat(Files.exists(sidecarMarkerOf(orig))).isTrue();
        verify(isoScanner, never()).applyDriftedPath(anyLong(), any());
        assertThat(drift.getReport().getDrifts()).isEmpty();
    }

    @Test
    @DisplayName("DUPLICATE 승격 : 재계산 지문 일치 → DB 경로 갱신(applyDriftedPath 재사용) + 원본 삭제 + 마커 재발급 없음 (plan D5)")
    void promoteDuplicate_updatesDbAndDeletesOriginal(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("dvd.iso");
        Path copy = tmp.resolve("backup_dvd.iso");
        Markable resource = sidecarPair(orig, copy, "hash-abc");
        Drift drift = duplicateDrift(ResourceType.OS_ISO, 42L, orig, copy);
        // 검수 반려 반영 — 승격 전 사본 실제 바이트 지문 재계산이 정본 기록과 일치하는 회귀 고정.
        given(isoScanner.recomputeManifestHash(any())).willReturn(Optional.of("hash-abc"));

        service.resolve(1L, DuplicateSurvivor.DUPLICATE);

        verify(isoScanner).applyDriftedPath(42L, copy.toAbsolutePath().normalize());
        assertThat(Files.exists(orig)).isFalse();
        assertThat(Files.exists(sidecarMarkerOf(orig))).isFalse();
        assertThat(Files.exists(copy)).isTrue();
        assertThat(Files.exists(sidecarMarkerOf(copy))).isTrue();
        // D5 계약 — 마커는 위치 독립이라 승격 시 재발급하지 않는다.
        verify(resource, never()).reissueMarker(anyString(), anyString());
        assertThat(drift.getReport().getDrifts()).isEmpty();
        // 재사용 계약 — 재계산은 정밀 점검과 같은 scanner 경로에 "사본 경로" 뷰로 위임된다.
        var viewCaptor = org.mockito.ArgumentCaptor.forClass(Markable.class);
        verify(isoScanner).recomputeManifestHash(viewCaptor.capture());
        assertThat(viewCaptor.getValue().getResourcePath()).isEqualTo(copy.toAbsolutePath().normalize());
        assertThat(viewCaptor.getValue().getResourceId()).isEqualTo(42L);
    }

    // ==== 실행 직전 가드 ==================================================

    @Test
    @DisplayName("stale 가드 : drift.oldPath != 현재 자원 경로 (다중 사본 잔여 행) → 409, 파일 무변경")
    void staleGuard_rejectsWhenDbPathChanged(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("dvd.iso");
        Path copy = tmp.resolve("backup_dvd.iso");
        sidecarPair(orig, copy, "hash-abc");
        // 그 사이 다른 카드의 승격으로 DB 경로가 이미 이동한 상황 재현
        Path movedPath = tmp.resolve("promoted_dvd.iso");
        Markable moved = resourceAt(ResourceType.OS_ISO, 42L, movedPath, MarkerLayout.SIDECAR, "hash-abc");
        given(isoScanner.findActiveMarkableById(42L)).willReturn(Optional.of(moved));
        Drift drift = duplicateDrift(ResourceType.OS_ISO, 42L, orig, copy);

        assertThatThrownBy(() -> service.resolve(1L, DuplicateSurvivor.ORIGINAL))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("상태가 바뀌어");
        assertThat(Files.exists(copy)).isTrue();
        assertThat(drift.getReport().getDrifts()).hasSize(1); // 카드 유지
    }

    @Test
    @DisplayName("승격 가드 : 사본 지문 != 엔티티 기록 지문(낡은 사본) → 409 duplicateNotPromotable, 파일·DB 무변경")
    void promoteGuard_rejectsStaleContentCopy(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("dvd.iso");
        Path copy = tmp.resolve("old_dvd.iso");
        sidecarPair(orig, copy, "old-hash"); // 사본 마커 지문이 정본 기록(hash-abc)과 다름
        duplicateDrift(ResourceType.OS_ISO, 42L, orig, copy);

        assertThatThrownBy(() -> service.resolve(1L, DuplicateSurvivor.DUPLICATE))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("승격할 수 없습니다")
                .hasMessageContaining("지문");
        verify(isoScanner, never()).applyDriftedPath(anyLong(), any());
        assertThat(Files.exists(orig)).isTrue();
        assertThat(Files.exists(copy)).isTrue();
    }

    @Test
    @DisplayName("검수 반려 재현 : 마커 기록 지문은 일치 + 페이로드 바이트만 변조(재계산 지문 불일치) → 409, 파일·DB 무변경")
    void promoteGuard_rejectsTamperedPayloadCopy(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("dvd.iso");
        Path copy = tmp.resolve("tampered_dvd.iso");
        // 사본 마커는 정상 최신본과 byte-동일(서명 유효 + 기록 지문 == 엔티티 기록 hash-abc) —
        // 종전 가드(기록값 대조)로는 구분 불가. 실제 페이로드만 변조된 상황.
        sidecarPair(orig, copy, "hash-abc");
        Files.writeString(copy, "TAMPERED-PAYLOAD");
        Drift drift = duplicateDrift(ResourceType.OS_ISO, 42L, orig, copy);
        // 재계산(정밀 점검과 동일 경로)은 변조된 실제 바이트의 지문을 돌려준다.
        given(isoScanner.recomputeManifestHash(any())).willAnswer(inv -> {
            Markable view = inv.getArgument(0);
            boolean isCopy = view.getResourcePath().equals(copy.toAbsolutePath().normalize());
            return Optional.of(isCopy ? "tampered-hash" : "hash-abc");
        });

        assertThatThrownBy(() -> service.resolve(1L, DuplicateSurvivor.DUPLICATE))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("실제 내용의 지문");
        verify(isoScanner, never()).applyDriftedPath(anyLong(), any());
        assertThat(Files.exists(orig)).isTrue();
        assertThat(Files.exists(sidecarMarkerOf(orig))).isTrue();
        assertThat(Files.exists(copy)).isTrue();
        assertThat(drift.getReport().getDrifts()).hasSize(1); // 카드 유지
    }

    @Test
    @DisplayName("승격 가드 : 사본 지문 재계산 불능(IO 오류 등 Optional.empty) → 409, 파일·DB 무변경")
    void promoteGuard_rejectsWhenRecomputeUnavailable(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("dvd.iso");
        Path copy = tmp.resolve("backup_dvd.iso");
        sidecarPair(orig, copy, "hash-abc");
        duplicateDrift(ResourceType.OS_ISO, 42L, orig, copy);
        given(isoScanner.recomputeManifestHash(any())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(1L, DuplicateSurvivor.DUPLICATE))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("지문을 계산할 수 없습니다");
        verify(isoScanner, never()).applyDriftedPath(anyLong(), any());
        assertThat(Files.exists(orig)).isTrue();
        assertThat(Files.exists(copy)).isTrue();
    }

    @Test
    @DisplayName("승격 가드 : 사본이 원본 트리 내부(포함 관계) → 409 — 원본 삭제가 사본까지 지우는 사고 차단")
    void promoteGuard_rejectsNestedCopy(@TempDir Path tmp) throws Exception {
        Path origTree = tmp.resolve("DiagTool");
        Files.createDirectories(origTree);
        writeMarker(ResourceType.SUBPROGRAM, origTree, MarkerLayout.IN_TREE, 7L, "tree-hash");
        Path nestedCopy = origTree.resolve("backup_DiagTool");
        Files.createDirectories(nestedCopy);
        writeMarker(ResourceType.SUBPROGRAM, nestedCopy, MarkerLayout.IN_TREE, 7L, "tree-hash");
        Markable resource = resourceAt(ResourceType.SUBPROGRAM, 7L, origTree, MarkerLayout.IN_TREE, "tree-hash");
        given(subprogramScanner.findActiveMarkableById(7L)).willReturn(Optional.of(resource));
        duplicateDrift(ResourceType.SUBPROGRAM, 7L, origTree, nestedCopy);

        assertThatThrownBy(() -> service.resolve(1L, DuplicateSurvivor.DUPLICATE))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("포함 관계");
        verify(subprogramScanner, never()).applyDriftedPath(anyLong(), any());
        assertThat(Files.isDirectory(origTree)).isTrue();
        assertThat(Files.isDirectory(nestedCopy)).isTrue();
    }

    @Test
    @DisplayName("파일 삭제 실패 : IOException → IllegalStateException 전파 (트랜잭션 롤백 경로 — 카드 유지 + 오류 응답)")
    void deleteFailure_propagatesForRollback(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("dvd.iso");
        Files.writeString(orig, "fake-iso");
        writeMarker(ResourceType.OS_ISO, orig, MarkerLayout.SIDECAR, 42L, "hash-abc");
        // 사본 자리가 비어 있지 않은 디렉토리 — SIDECAR 단일 파일 삭제가 DirectoryNotEmptyException 으로 실패
        Path copy = tmp.resolve("busy_copy");
        Files.createDirectories(copy);
        Files.writeString(copy.resolve("child.txt"), "x");
        writeMarker(ResourceType.OS_ISO, copy, MarkerLayout.SIDECAR, 42L, "hash-abc");
        Markable resource = resourceAt(ResourceType.OS_ISO, 42L, orig, MarkerLayout.SIDECAR, "hash-abc");
        given(isoScanner.findActiveMarkableById(42L)).willReturn(Optional.of(resource));
        duplicateDrift(ResourceType.OS_ISO, 42L, orig, copy);

        assertThatThrownBy(() -> service.resolve(1L, DuplicateSurvivor.ORIGINAL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("파일 삭제 실패");
        // 원본은 손대지 않았다 — 실패는 사본 삭제 단계에서만 발생.
        assertThat(Files.exists(orig)).isTrue();
    }

    // ==== 거절 (kind / 비활성 / 전역 OFF / 부재) ============================

    @Test
    @DisplayName("kind 불일치 : RESOURCE_DUPLICATED 외 종류의 direct POST → 409 notApplicable")
    void rejectsOtherKind(@TempDir Path tmp) {
        DriftReport report = DriftReport.builder()
                .scannedAt(Instant.now()).scanDurationMs(0).deep(false).totalChecked(1).build();
        Drift drift = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(DriftKind.PATH_DRIFT)
                .oldPath("/x").newPath("/y").detectedAt(Instant.now()).build();
        report.addDrift(drift);
        ReflectionTestUtils.setField(drift, "id", 1L);
        given(driftRepository.findById(1L)).willReturn(Optional.of(drift));

        assertThatThrownBy(() -> service.resolve(1L, DuplicateSurvivor.ORIGINAL))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("경로 이동됨");
    }

    @Test
    @DisplayName("자원 비활성(삭제·부재) → 409 staleState")
    void rejectsInactiveResource(@TempDir Path tmp) {
        duplicateDrift(ResourceType.OS_ISO, 42L, tmp.resolve("a.iso"), tmp.resolve("b.iso"));
        given(isoScanner.findActiveMarkableById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(1L, DuplicateSurvivor.ORIGINAL))
                .isInstanceOf(DriftResolutionNotAllowedException.class);
    }

    @Test
    @DisplayName("전역 resolution-enabled OFF → 409 globalOff (direct POST 안전망)")
    void rejectsWhenGlobalOff() {
        given(reconciliationService.isResolutionEnabled()).willReturn(false);

        assertThatThrownBy(() -> service.resolve(1L, DuplicateSurvivor.ORIGINAL))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("reconciliation.resolution-enabled");
    }

    @Test
    @DisplayName("존재하지 않는 driftId → DriftNotFoundException (404)")
    void rejectsUnknownDrift() {
        given(driftRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve(999L, DuplicateSurvivor.ORIGINAL))
                .isInstanceOf(DriftNotFoundException.class);
    }
}
