package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.TrashService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * S6-2-2 — SOFTDEL ESCAPE 해결 전략 2종의 단위 검증.
 * 복귀 = 기존 복원 절차 위임(1줄), 이탈 = 사본충돌 거절 → 동반 마커 정리 → 회수 → 기록 갱신(+역보상).
 */
class SoftDeleteEscapeResolutionTest {

    private final MarkableScanner scanner = mock(MarkableScanner.class);
    private final TrashService trashService = mock(TrashService.class);
    private final ProvisionMarkerService markerService = new ProvisionMarkerService();

    /** LifecycleEntity + Markable 겸용 fixture (TrashLifecycleServiceTest.TestEntity 선례). */
    private static class DeletedIso extends LifecycleEntity implements Markable {
        private final Long id;
        private final Path path;
        private boolean markTrashedThrows = false;
        DeletedIso(Long id, Path path, String trashedPath) {
            this.id = id;
            this.path = path;
            softDelete();
            if (trashedPath != null) markTrashed(trashedPath);
        }
        @Override public void markTrashed(String trashedPath) {
            if (markTrashedThrows) throw new IllegalStateException("기록 갱신 실패 재현");
            super.markTrashed(trashedPath);
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

    private static Drift driftOf(DriftKind kind, String newPath) {
        return Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(42L).kind(kind)
                .oldPath("/expected").newPath(newPath).detectedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("복귀 : 기존 복원 절차(restoreFromTrash)에 그대로 위임 — 복원 코드 이중화 없음")
    void toOriginal_delegatesToRestore() {
        new SoftDeleteEscapeToOriginalResolution()
                .resolve(driftOf(DriftKind.SOFTDEL_ESCAPE_TO_ORIGINAL, "/iso/dvd.iso"), scanner);

        verify(scanner, times(1)).restoreFromTrash(42L);
    }

    @Test
    @DisplayName("이탈 회수 성공 : 동반 마커 정리 → 휴지통 이동 → 기록 갱신")
    void toOther_requarantines(@TempDir Path tmp) throws Exception {
        Path found = tmp.resolve("backup/dvd.iso");
        Files.createDirectories(found.getParent());
        Files.writeString(found, "body");
        Path strayMarker = tmp.resolve("backup/dvd.iso.provision.json");
        Files.writeString(strayMarker, "{}"); // 동반 마커 찌꺼기
        Path moved = tmp.resolve("trash/dvd_x.iso");

        DeletedIso resource = new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), null);
        given(scanner.findTrashedById(42L)).willReturn(Optional.of(resource));
        given(trashService.moveToTrash(found, ResourceType.OS_ISO, 42L)).willReturn(moved);

        new SoftDeleteEscapeToOtherResolution(trashService, markerService)
                .resolve(driftOf(DriftKind.SOFTDEL_ESCAPE_TO_OTHER, found.toString()), scanner);

        assertThat(Files.exists(strayMarker)).isFalse();               // 동반 마커 정리
        verify(trashService, times(1)).moveToTrash(found, ResourceType.OS_ISO, 42L);
        assertThat(resource.getTrashedPath()).isEqualTo(moved.toString()); // 기록 갱신
    }

    @Test
    @DisplayName("이탈 회수 거절 : 휴지통 기존 사본 생존 → 409 + 디스크 무변경")
    void toOther_rejectsWhenTrashCopyAlive(@TempDir Path tmp) throws Exception {
        Path aliveTrashed = tmp.resolve("trash/dvd_old.iso");
        Files.createDirectories(aliveTrashed.getParent());
        Files.writeString(aliveTrashed, "old-copy"); // 기존 사본 생존
        DeletedIso resource = new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), aliveTrashed.toString());
        given(scanner.findTrashedById(42L)).willReturn(Optional.of(resource));

        assertThatThrownBy(() -> new SoftDeleteEscapeToOtherResolution(trashService, markerService)
                .resolve(driftOf(DriftKind.SOFTDEL_ESCAPE_TO_OTHER, tmp.resolve("backup/dvd.iso").toString()), scanner))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("휴지통");
        verify(trashService, never()).moveToTrash(any(), any(), any());
    }

    @Test
    @DisplayName("이탈 회수 역보상 : 기록 갱신 실패 시 파일을 발견 위치로 되돌림 (HF-1 패턴)")
    void toOther_compensatesOnMarkFailure(@TempDir Path tmp) throws Exception {
        Path found = tmp.resolve("backup/dvd.iso");
        Files.createDirectories(found.getParent());
        Files.writeString(found, "body");
        Path moved = tmp.resolve("trash/dvd_x.iso");

        DeletedIso resource = new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), null);
        resource.markTrashedThrows = true;
        given(scanner.findTrashedById(42L)).willReturn(Optional.of(resource));
        given(trashService.moveToTrash(found, ResourceType.OS_ISO, 42L)).willReturn(moved);

        assertThatThrownBy(() -> new SoftDeleteEscapeToOtherResolution(trashService, markerService)
                .resolve(driftOf(DriftKind.SOFTDEL_ESCAPE_TO_OTHER, found.toString()), scanner))
                .isInstanceOf(IllegalStateException.class);
        verify(trashService, times(1)).moveBack(moved, found); // 역보상 — 롤백과 디스크 동방향
    }

    @Test
    @DisplayName("이탈 회수 거절 : 발견 위치에 파일이 없음(마커만 발견/stale) → 409 + 마커 보존 + 디스크 무변경")
    void toOther_rejectsWhenFoundFileMissing(@TempDir Path tmp) throws Exception {
        Path found = tmp.resolve("backup/dvd.iso"); // 본체 없음
        Files.createDirectories(found.getParent());
        Path strayMarker = tmp.resolve("backup/dvd.iso.provision.json");
        Files.writeString(strayMarker, "{}"); // 마커만 존재
        DeletedIso resource = new DeletedIso(42L, tmp.resolve("iso/dvd.iso"), null);
        given(scanner.findTrashedById(42L)).willReturn(Optional.of(resource));

        assertThatThrownBy(() -> new SoftDeleteEscapeToOtherResolution(trashService, markerService)
                .resolve(driftOf(DriftKind.SOFTDEL_ESCAPE_TO_OTHER, found.toString()), scanner))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("발견 위치에 파일이 더 이상 없습니다");
        assertThat(Files.exists(strayMarker)).isTrue(); // 비가역 정리가 선행되지 않음
        verify(trashService, never()).moveToTrash(any(), any(), any());
    }
}
