package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.exception.GhostRowRestoreNotAllowedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * MK3-1 — Ghost 케이스의 restore 거절 흐름 검증.
 *
 * <p>{@link TrashLifecycleService#restoreFromTrash} 의 trashed_path=null 분기 :
 * <ul>
 *   <li>FS 자원이 DB.path 에 살아있음 → 단순 lifecycle 복원 (정상 흐름)</li>
 *   <li>FS 자원도 부재 = ghost → {@link GhostRowRestoreNotAllowedException} (409)</li>
 * </ul>
 */
class TrashLifecycleServiceGhostRestoreTest {

    private TrashLifecycleService service;

    @BeforeEach
    void setUp() {
        TrashService trashService = mock(TrashService.class);
        ProvisionMarkerService markerService = mock(ProvisionMarkerService.class);
        service = new TrashLifecycleService(trashService, markerService);
    }

    @Test
    @DisplayName("J1 : ghost row restore → GhostRowRestoreNotAllowedException")
    void restore_ghost_throws(@TempDir Path tmp) {
        Path missing = tmp.resolve("ghost-resource.iso"); // 파일 없음
        TestEntity ghost = new TestEntity(99L, missing);
        ghost.softDelete(); // is_deleted=true, trashed_*=null
        // 자원 부재 + trashed_path=null = ghost

        assertThatThrownBy(() -> service.restoreFromTrash(ghost, e -> Map.of()))
                .isInstanceOf(GhostRowRestoreNotAllowedException.class)
                .hasMessageContaining("OS_ISO#99");
    }

    @Test
    @DisplayName("trashed_path=null + FS 자원 살아있음 → 단순 lifecycle 복원 (정상 흐름)")
    void restore_resourceAlive_lifecycleOnly(@TempDir Path tmp) throws Exception {
        Path resourceBack = tmp.resolve("recovered.iso");
        Files.writeString(resourceBack, "back");
        TestEntity entity = new TestEntity(50L, resourceBack);
        entity.softDelete();

        service.restoreFromTrash(entity, e -> Map.of());

        assertThat(entity.isDeleted()).isFalse();
        assertThat(entity.getTrashedAt()).isNull();
        assertThat(entity.getTrashedPath()).isNull();
    }

    /** 테스트 전용 entity — LifecycleEntity 의 lifecycle 메서드 + Markable 시그니처만 노출. */
    private static class TestEntity extends LifecycleEntity implements Markable {
        private final Long id;
        private final Path path;

        TestEntity(Long id, Path path) {
            this.id = id;
            this.path = path;
        }

        @Override protected Long resourceId() { return id; }
        @Override protected LifecycleEntity parentLifecycle() { return null; }
        @Override public Long getResourceId() { return id; }
        @Override public ResourceType getResourceType() { return ResourceType.OS_ISO; }
        @Override public Path getResourcePath() { return path; }
        @Override public MarkerLayout getMarkerLayout() { return MarkerLayout.SIDECAR; }
        @Override public String getManifestHash() { return "h"; }
        @Override public String getMarkerSignature() { return null; }
        @Override public void reissueMarker(String hash, String signature) {}
    }
}
