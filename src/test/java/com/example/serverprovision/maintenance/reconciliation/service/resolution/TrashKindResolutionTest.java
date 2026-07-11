package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.PurgeRequest;
import com.example.serverprovision.global.trash.PurgeResult;
import com.example.serverprovision.global.trash.enums.PurgeOrigin;
import com.example.serverprovision.global.trash.service.PurgeExecutor;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * S6-2-3 — TRASH 계열 해결 전략 2종의 단위 검증.
 * 소실 = 영구삭제 파이프라인 재사용(감사 기록 동반) + 실행 직전 재검증, 잔여 마커 = 멱등 삭제.
 */
class TrashKindResolutionTest {

    private final MarkableScanner scanner = mock(MarkableScanner.class);
    private final PurgeExecutor purgeExecutor = mock(PurgeExecutor.class);
    private final ProvisionMarkerService markerService = new ProvisionMarkerService();

    private static class DeletedIso extends LifecycleEntity implements Markable {
        private final Long id;
        private final Path path;
        DeletedIso(Long id, Path path, String trashedPath) {
            this.id = id;
            this.path = path;
            softDelete();
            if (trashedPath != null) markTrashed(trashedPath);
        }
        @Override protected Long resourceId() { return id; }
        @Override protected LifecycleEntity parentLifecycle() { return null; }
        @Override public Long getResourceId() { return id; }
        @Override public ResourceType getResourceType() { return ResourceType.OS_ISO; }
        @Override public Path getResourcePath() { return path; }
        @Override public MarkerLayout getMarkerLayout() { return MarkerLayout.SIDECAR; }
        @Override public String getManifestHash() { return "hash-abc"; }
        @Override public String getMarkerSignature() { return "sig"; }
        @Override public void reissueMarker(String h, String sg) { }
    }

    private static Drift driftOf(DriftKind kind, String oldPath) {
        return Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(kind)
                .oldPath(oldPath).newPath(null).detectedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("소실 정리 : 영구삭제 파이프라인(DRIFT_TRASH_LOST 진입경로)에 위임 — 감사 기록이 공짜로 남음")
    void trashLost_delegatesToPurgeExecutor(@TempDir Path tmp) {
        String trashed = tmp.resolve("trash/gone.iso").toString(); // 실물 부재 유지
        given(scanner.findTrashedById(42L)).willReturn(Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed)));
        given(purgeExecutor.execute(any())).willAnswer(inv ->
                new PurgeResult.Success(inv.getArgument(0), 7L));

        new TrashLostClearResolution(purgeExecutor)
                .resolve(driftOf(DriftKind.TRASH_LOST, trashed), scanner);

        ArgumentCaptor<PurgeRequest> captor = ArgumentCaptor.forClass(PurgeRequest.class);
        verify(purgeExecutor).execute(captor.capture());
        assertThat(captor.getValue().origin()).isEqualTo(PurgeOrigin.DRIFT_TRASH_LOST);
        assertThat(captor.getValue().resourceId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("소실 정리 거절 : 적용 시점에 휴지통 파일 재출현 → 409 (멀쩡해진 자원 삭제 차단)")
    void trashLost_rejectsWhenFileReappeared(@TempDir Path tmp) throws Exception {
        Path trashed = tmp.resolve("trash/back.iso");
        Files.createDirectories(trashed.getParent());
        Files.writeString(trashed, "재출현"); // 백업 복원 등으로 되살아남
        given(scanner.findTrashedById(42L)).willReturn(Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed.toString())));

        assertThatThrownBy(() -> new TrashLostClearResolution(purgeExecutor)
                .resolve(driftOf(DriftKind.TRASH_LOST, trashed.toString()), scanner))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("상태가 바뀌어");
        verify(purgeExecutor, never()).execute(any());
    }

    @Test
    @DisplayName("소실 정리 실패 전파 : 파이프라인이 Failed 를 반환하면 원인 예외로 표출")
    void trashLost_propagatesFailure(@TempDir Path tmp) {
        String trashed = tmp.resolve("trash/gone.iso").toString();
        given(scanner.findTrashedById(42L)).willReturn(Optional.of(new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed)));
        given(purgeExecutor.execute(any())).willAnswer(inv ->
                new PurgeResult.Failed(inv.getArgument(0), 8L, new IllegalStateException("row 삭제 실패")));

        assertThatThrownBy(() -> new TrashLostClearResolution(purgeExecutor)
                .resolve(driftOf(DriftKind.TRASH_LOST, trashed), scanner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("row 삭제 실패");
    }

    @Test
    @DisplayName("잔여 마커 정리 : 마커 파일만 삭제 — 자원·기록 불변, 재실행도 안전(멱등)")
    void staleMarker_deletesMarkerOnly(@TempDir Path tmp) throws Exception {
        Path trashed = tmp.resolve("trash/dvd_x.iso");
        Files.createDirectories(trashed.getParent());
        Files.writeString(trashed, "body");
        Path staleMarker = tmp.resolve("trash/dvd_x.iso.provision.json");
        Files.writeString(staleMarker, "{}");
        DeletedIso resource = new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), trashed.toString());
        given(scanner.findTrashedById(42L)).willReturn(Optional.of(resource));

        StaleTrashMarkerCleanupResolution resolution = new StaleTrashMarkerCleanupResolution(markerService);
        resolution.resolve(driftOf(DriftKind.TRASH_MARKER_STALE, trashed.toString()), scanner);

        assertThat(Files.exists(staleMarker)).isFalse();
        assertThat(Files.exists(trashed)).isTrue(); // 실물 불변
        assertThat(resource.isDeleted()).isTrue();  // 기록 불변

        // 멱등 — 마커가 이미 없어도 성공 취급
        resolution.resolve(driftOf(DriftKind.TRASH_MARKER_STALE, trashed.toString()), scanner);
    }

    @Test
    @DisplayName("잔여 마커 정리 거절 : 자원이 더 이상 휴지통에 없음(복원/영구삭제됨) → 409")
    void staleMarker_rejectsWhenResourceGone() {
        given(scanner.findTrashedById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> new StaleTrashMarkerCleanupResolution(markerService)
                .resolve(driftOf(DriftKind.TRASH_MARKER_STALE, "/trash/x.iso"), scanner))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("상태가 바뀌어");
    }

    @Test
    @DisplayName("소실 정리 거절 : 원위치가 그 사이 다른 파일에 점유됨 → 409 (무관한 신규 자원 실물 삭제 사고 차단)")
    void trashLost_rejectsWhenOriginalPathOccupied(@TempDir Path tmp) throws Exception {
        Path orig = tmp.resolve("iso/dvd.iso");
        Files.createDirectories(orig.getParent());
        Files.writeString(orig, "새로 등록된 무관한 자원"); // 경로 재사용
        String trashed = tmp.resolve("trash/gone.iso").toString(); // 소실 유지
        given(scanner.findTrashedById(42L)).willReturn(Optional.of(new DeletedIso(42L, orig, trashed)));

        assertThatThrownBy(() -> new TrashLostClearResolution(purgeExecutor)
                .resolve(driftOf(DriftKind.TRASH_LOST, trashed), scanner))
                .isInstanceOf(DriftResolutionNotAllowedException.class);
        verify(purgeExecutor, never()).execute(any());
        assertThat(Files.exists(orig)).isTrue(); // 점유 파일 무사
    }

    private static class DeletedTree extends LifecycleEntity implements Markable {
        private final Long id;
        private final Path path;
        DeletedTree(Long id, Path path, String trashedPath) {
            this.id = id;
            this.path = path;
            softDelete();
            if (trashedPath != null) markTrashed(trashedPath);
        }
        @Override protected Long resourceId() { return id; }
        @Override protected LifecycleEntity parentLifecycle() { return null; }
        @Override public Long getResourceId() { return id; }
        @Override public ResourceType getResourceType() { return ResourceType.BIOS_BUNDLE; }
        @Override public Path getResourcePath() { return path; }
        @Override public MarkerLayout getMarkerLayout() { return MarkerLayout.IN_TREE; }
        @Override public String getManifestHash() { return "hash-tree"; }
        @Override public String getMarkerSignature() { return "sig"; }
        @Override public void reissueMarker(String h, String sg) { }
    }

    @Test
    @DisplayName("IN_TREE 잔여 마커 정리 : 휴지통 트리 내부 .provision.json 만 삭제 — 트리 본체 불변")
    void staleMarker_inTree_deletesInnerMarker(@TempDir Path tmp) throws Exception {
        Path trashedTree = tmp.resolve("trash/R23_x");
        Files.createDirectories(trashedTree);
        Files.writeString(trashedTree.resolve("rom.bin"), "rom");
        Path innerMarker = trashedTree.resolve(".provision.json");
        Files.writeString(innerMarker, "{}");
        given(scanner.findTrashedById(7L)).willReturn(Optional.of(
                new DeletedTree(7L, tmp.resolve("bios/R23"), trashedTree.toString())));

        Drift drift = Drift.builder()
                .resourceType(ResourceType.BIOS_BUNDLE).resourceId(7L).kind(DriftKind.TRASH_MARKER_STALE)
                .oldPath(trashedTree.toString()).newPath(null).detectedAt(Instant.now())
                .build();
        new StaleTrashMarkerCleanupResolution(markerService).resolve(drift, scanner);

        assertThat(Files.exists(innerMarker)).isFalse();
        assertThat(Files.exists(trashedTree.resolve("rom.bin"))).isTrue(); // 트리 본체 불변
    }
}
