package com.example.serverprovision.maintenance.reconciliation.service.resolution;

import com.example.serverprovision.global.marker.DriftKind;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkableScanner;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.TrashService;
import com.example.serverprovision.maintenance.reconciliation.entity.Drift;
import com.example.serverprovision.maintenance.reconciliation.exception.DriftResolutionNotAllowedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * S6-3-2 — 미등록 마커 격리 회수의 단위 검증. 비파괴 이동·재검증 거절·역보상.
 */
class OrphanMarkerQuarantineResolutionTest {

    private final MarkableScanner scanner = mock(MarkableScanner.class);
    private final ProvisionMarkerService markerService = withSecret();

    private static ProvisionMarkerService withSecret() {
        ProvisionMarkerService s = new ProvisionMarkerService();
        ReflectionTestUtils.setField(s, "secret", "test-secret");
        return s;
    }

    private OrphanMarkerQuarantineResolution resolution(TrashService trashService, Path trashRoot) {
        OrphanMarkerQuarantineResolution r = new OrphanMarkerQuarantineResolution(markerService, trashService);
        ReflectionTestUtils.setField(r, "trashRoot", trashRoot.toString());
        return r;
    }

    private void writeOrphanMarker(Path resourcePath, Long id) {
        MarkerContent unsigned = new MarkerContent(
                ResourceType.OS_ISO.name(), id, Map.of(), Instant.now(), "hash-x", null);
        markerService.write(resourcePath, MarkerLayout.SIDECAR,
                unsigned.withSignature(markerService.computeSignature(unsigned)));
    }

    private static Drift orphanDrift(Long driftId, Long resourceId, Path resourcePath) {
        Drift d = Drift.builder()
                .resourceType(ResourceType.OS_ISO).resourceId(resourceId).kind(DriftKind.ORPHAN)
                .oldPath(resourcePath.toString()).newPath(null).detectedAt(Instant.now())
                .build();
        ReflectionTestUtils.setField(d, "id", driftId);
        return d;
    }

    @Test
    @DisplayName("회수 성공 : 본체+마커가 격리 구역으로 이동 (비파괴 — 실물 보존)")
    void quarantinesBodyAndMarker(@TempDir Path tmp) throws Exception {
        Path stray = tmp.resolve("iso/ghost.iso");
        Files.createDirectories(stray.getParent());
        Files.writeString(stray, "body");
        writeOrphanMarker(stray, 99L);
        Path trashRoot = tmp.resolve(".soft-deleted");

        resolution(new TrashService(null), trashRoot)
                .resolve(orphanDrift(5L, 99L, stray), scanner);

        assertThat(Files.exists(stray)).isFalse();
        assertThat(Files.exists(trashRoot.resolve("orphan/drift5_ghost.iso"))).isTrue();
        assertThat(Files.exists(trashRoot.resolve("orphan/drift5_ghost.iso.provision.json"))).isTrue();
    }

    @Test
    @DisplayName("회수 성공 : 본체 없이 마커만 남은 orphan — 마커만 회수")
    void quarantinesMarkerOnly(@TempDir Path tmp) throws Exception {
        Path stray = tmp.resolve("iso/ghost.iso"); // 본체 없음
        Files.createDirectories(stray.getParent());
        writeOrphanMarker(stray, 99L);
        Path trashRoot = tmp.resolve(".soft-deleted");

        resolution(new TrashService(null), trashRoot)
                .resolve(orphanDrift(6L, 99L, stray), scanner);

        assertThat(Files.exists(trashRoot.resolve("orphan/drift6_ghost.iso.provision.json"))).isTrue();
    }

    @Test
    @DisplayName("거절 : 그 사이 DB 에 주인이 생김(재등록) → 409 + 디스크 무변경")
    void rejectsWhenOwnerAppeared(@TempDir Path tmp) throws Exception {
        Path stray = tmp.resolve("iso/ghost.iso");
        Files.createDirectories(stray.getParent());
        Files.writeString(stray, "body");
        writeOrphanMarker(stray, 99L);
        Markable owner = mock(Markable.class);
        given(scanner.findActiveMarkableById(99L)).willReturn(Optional.of(owner));

        assertThatThrownBy(() -> resolution(new TrashService(null), tmp.resolve(".soft-deleted"))
                .resolve(orphanDrift(7L, 99L, stray), scanner))
                .isInstanceOf(DriftResolutionNotAllowedException.class);
        assertThat(Files.exists(stray)).isTrue(); // 무변경
    }

    @Test
    @DisplayName("거절 : 마커의 신분이 바뀜(교체·재발급) → 409 (방금 등록된 자원 격리 사고 차단)")
    void rejectsWhenMarkerIdentityChanged(@TempDir Path tmp) throws Exception {
        Path stray = tmp.resolve("iso/ghost.iso");
        Files.createDirectories(stray.getParent());
        Files.writeString(stray, "body");
        writeOrphanMarker(stray, 123L); // drift 는 99 를 기억하는데 실제 마커는 123

        assertThatThrownBy(() -> resolution(new TrashService(null), tmp.resolve(".soft-deleted"))
                .resolve(orphanDrift(8L, 99L, stray), scanner))
                .isInstanceOf(DriftResolutionNotAllowedException.class)
                .hasMessageContaining("상태가 바뀌어");
        assertThat(Files.exists(stray)).isTrue();
    }

    @Test
    @DisplayName("역보상 : 마커 이동 실패 시 본체를 원위치로 되돌림 (반쪽 회수 방지)")
    void compensatesWhenMarkerMoveFails(@TempDir Path tmp) throws Exception {
        Path stray = tmp.resolve("iso/ghost.iso");
        Files.createDirectories(stray.getParent());
        Files.writeString(stray, "body");
        writeOrphanMarker(stray, 99L);
        Path trashRoot = tmp.resolve(".soft-deleted");

        TrashService failing = mock(TrashService.class);
        // 본체 이동은 실제 수행, 마커 이동만 실패 재현
        org.mockito.Mockito.doAnswer(inv -> {
            Files.createDirectories(((Path) inv.getArgument(1)).getParent());
            Files.move(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(failing).relocate(org.mockito.ArgumentMatchers.eq(stray), any());
        doThrow(new IllegalStateException("mv 실패"))
                .when(failing).relocate(org.mockito.ArgumentMatchers.eq(tmp.resolve("iso/ghost.iso.provision.json")), any());
        org.mockito.Mockito.doAnswer(inv -> {
            Files.move(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(failing).relocate(org.mockito.ArgumentMatchers.eq(trashRoot.resolve("orphan/drift9_ghost.iso")), any());

        assertThatThrownBy(() -> resolution(failing, trashRoot)
                .resolve(orphanDrift(9L, 99L, stray), scanner))
                .isInstanceOf(IllegalStateException.class);
        assertThat(Files.exists(stray)).isTrue(); // 본체가 원위치로 복귀
    }
}
