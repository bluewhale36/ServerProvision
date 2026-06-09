package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.ResourceType;
import com.example.serverprovision.global.marker.exception.MarkerWriteFailedException;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.management.common.exception.RestorePathOccupiedException;
import com.example.serverprovision.management.common.exception.RestoreTrashLostException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * HF-1 — restore 멱등 사전게이트 (FS 4상태) + FS-first 역보상 + softDelete 대칭 보강 단위 테스트.
 *
 * <p>실제 IO 실패는 비결정적이므로 {@code markerService.write} / {@code resolveMarkerFile} 를 mock 으로
 * throw 시켜 catch 경로를 강제 실행하고, {@code moveBackReverse} 호출 + 원예외 재전파를 검증한다.
 * FS 비트 판정은 {@code @TempDir} 의 실제 파일 존재/부재로 구성한다.</p>
 */
class TrashLifecycleServiceTest {

    private TrashService trashService;
    private ProvisionMarkerService markerService;
    private TrashLifecycleService service;

    @BeforeEach
    void setUp() {
        trashService = mock(TrashService.class);
        markerService = mock(ProvisionMarkerService.class);
        service = new TrashLifecycleService(trashService, markerService);
    }

    @Nested
    @DisplayName("restore 멱등 사전게이트 (FS 2비트 4상태)")
    class RestoreGate {

        @Test
        @DisplayName("(원X·trashO) 정상 복원 — moveBack + write + DB 전이")
        void normal_restore(@TempDir Path tmp) {
            Path trashed = tmp.resolve("trashed.iso");
            Path original = tmp.resolve("active/original.iso");
            writeFile(trashed);
            ensureDir(original.getParent());

            TestEntity entity = deletedEntity(1L, original, trashed, "h-1");
            Path markerFile = original.resolveSibling("original.iso.provision.json");
            given(markerService.resolveMarkerFile(original, MarkerLayout.SIDECAR)).willReturn(markerFile);
            given(markerService.computeSignature(any())).willReturn("sig-1");

            service.restoreFromTrash(entity, e -> Map.of("k", "v"));

            verify(trashService).moveBack(trashed, original);
            verify(markerService).write(eqPath(original), eqLayout(), any());
            verify(trashService, never()).moveBackReverse(any(), any());
            assertThat(entity.isDeleted()).isFalse();
            assertThat(entity.getTrashedPath()).isNull();
            assertThat(entity.reissuedHash).isEqualTo("h-1");
            assertThat(entity.reissuedSignature).isEqualTo("sig-1");
        }

        @Test
        @DisplayName("(원O·trashX) partial-restore 잔여 — moveBack/write skip, DB 만 self-heal (2xx)")
        void self_heal(@TempDir Path tmp) {
            Path trashed = tmp.resolve("gone.iso");     // 부재
            Path original = tmp.resolve("active/original.iso");
            writeFile(original);                          // 원위치 존재
            Path markerFile = original.resolveSibling("original.iso.provision.json"); // 마커 부재 → 선택 검증 skip
            given(markerService.resolveMarkerFile(original, MarkerLayout.SIDECAR)).willReturn(markerFile);

            TestEntity entity = deletedEntity(2L, original, trashed, "h-2");

            service.restoreFromTrash(entity, e -> Map.of());

            verify(trashService, never()).moveBack(any(), any());
            verify(markerService, never()).write(any(), any(), any());
            assertThat(entity.isDeleted()).isFalse();
            assertThat(entity.getTrashedPath()).isNull();
        }

        @Test
        @DisplayName("(원O·trashX) 동명파일 오인 방어 — 원위치 마커 manifestHash 불일치 → RestorePathOccupied")
        void self_heal_hashMismatch_rejected(@TempDir Path tmp) {
            Path trashed = tmp.resolve("gone.iso");     // 부재
            Path original = tmp.resolve("active/original.iso");
            writeFile(original);                          // 원위치 존재
            Path markerFile = original.resolveSibling("original.iso.provision.json");
            writeFile(markerFile);                        // 원위치 마커 존재
            given(markerService.resolveMarkerFile(original, MarkerLayout.SIDECAR)).willReturn(markerFile);
            given(markerService.read(original, MarkerLayout.SIDECAR)).willReturn(
                    new MarkerContent("OS_ISO", 3L, Map.of(), Instant.now(), "OTHER-HASH", "sig"));

            TestEntity entity = deletedEntity(3L, original, trashed, "h-3");

            assertThatThrownBy(() -> service.restoreFromTrash(entity, e -> Map.of()))
                    .isInstanceOf(RestorePathOccupiedException.class);
            assertThat(entity.isDeleted()).isTrue();      // self-heal 미적용
        }

        @Test
        @DisplayName("(원X·trashX) 진짜 분실 → RestoreTrashLostException (409)")
        void really_lost(@TempDir Path tmp) {
            Path trashed = tmp.resolve("gone.iso");       // 부재
            Path original = tmp.resolve("active/original.iso"); // 부재
            ensureDir(original.getParent());

            TestEntity entity = deletedEntity(4L, original, trashed, "h-4");

            assertThatThrownBy(() -> service.restoreFromTrash(entity, e -> Map.of()))
                    .isInstanceOf(RestoreTrashLostException.class);
            verify(trashService, never()).moveBack(any(), any());
            assertThat(entity.isDeleted()).isTrue();
        }

        @Test
        @DisplayName("(원O·trashO) 경로 점유 → RestorePathOccupiedException (409)")
        void path_occupied(@TempDir Path tmp) {
            Path trashed = tmp.resolve("trashed.iso");
            Path original = tmp.resolve("active/original.iso");
            writeFile(trashed);
            writeFile(original);                          // 둘 다 존재

            TestEntity entity = deletedEntity(5L, original, trashed, "h-5");

            assertThatThrownBy(() -> service.restoreFromTrash(entity, e -> Map.of()))
                    .isInstanceOf(RestorePathOccupiedException.class);
            verify(trashService, never()).moveBack(any(), any());
            assertThat(entity.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("FS-first 역보상")
    class ReverseCompensation {

        @Test
        @DisplayName("restore write 실패 → 마커 delete + moveBackReverse(원→trash) + 원예외 재전파, DB 미전이")
        void restore_writeFails_compensates(@TempDir Path tmp) {
            Path trashed = tmp.resolve("trashed.iso");
            Path original = tmp.resolve("active/original.iso");
            writeFile(trashed);
            ensureDir(original.getParent());              // (원X·trashO) 게이트 통과

            Path markerFile = original.resolveSibling("original.iso.provision.json");
            given(markerService.resolveMarkerFile(original, MarkerLayout.SIDECAR)).willReturn(markerFile);
            given(markerService.computeSignature(any())).willReturn("sig-x");
            willThrow(new MarkerWriteFailedException("boom", new java.io.IOException("io")))
                    .given(markerService).write(eqPath(original), eqLayout(), any());

            TestEntity entity = deletedEntity(6L, original, trashed, "h-6");

            assertThatThrownBy(() -> service.restoreFromTrash(entity, e -> Map.of()))
                    .isInstanceOf(MarkerWriteFailedException.class);

            verify(trashService).moveBack(trashed, original);
            verify(trashService).moveBackReverse(original, trashed);  // FS 역보상 (원→trash)
            assertThat(entity.isDeleted()).isTrue();                  // @Transactional 롤백 영역 — DB 미전이
            assertThat(entity.getTrashedPath()).isNotNull();
        }

        @Test
        @DisplayName("softDelete markTrashed 전 예외 → moveBackReverse(trash→원) 대칭 역보상 + 원예외 재전파")
        void softDelete_failsBeforeMarkTrashed_compensates(@TempDir Path tmp) {
            Path resource = tmp.resolve("active/resource.iso");
            writeFile(resource);
            Path trashed = tmp.resolve("trash/resource_x.iso");

            Path activeMarker = resource.resolveSibling("resource.iso.provision.json");
            given(markerService.resolveMarkerFile(resource, MarkerLayout.SIDECAR)).willReturn(activeMarker);
            given(trashService.moveToTrash(resource, ResourceType.OS_ISO, 7L)).willReturn(trashed);
            // moveToTrash 후 markTrashed 전 단계 (moved marker resolve) 에서 RuntimeException → 외부 catch 진입
            given(markerService.resolveMarkerFile(trashed, MarkerLayout.SIDECAR))
                    .willThrow(new IllegalStateException("resolve fail"));

            TestEntity entity = activeEntity(7L, resource);

            assertThatThrownBy(() -> service.softDeleteToTrash(entity))
                    .isInstanceOf(IllegalStateException.class);

            verify(trashService).moveBackReverse(trashed, resource);  // trash → 원위치 역복귀
            assertThat(entity.getTrashedPath()).isNull();             // markTrashed 미반영
        }

        @Test
        @DisplayName("restore computeSignature 실패 (write 이전 단계) → moveBackReverse + 원예외 재전파, DB 미전이")
        void restore_computeSignatureFails_compensates(@TempDir Path tmp) {
            Path trashed = tmp.resolve("trashed.iso");
            Path original = tmp.resolve("active/original.iso");
            writeFile(trashed);
            ensureDir(original.getParent());              // (원X·trashO) 게이트 통과

            Path markerFile = original.resolveSibling("original.iso.provision.json");
            given(markerService.resolveMarkerFile(original, MarkerLayout.SIDECAR)).willReturn(markerFile);
            // moveBack 직후·write 이전 단계(computeSignature)에서 throw — 확장된 보상 윈도우가 덮어야 한다.
            willThrow(new MarkerWriteFailedException("sig-boom", new java.io.IOException("hmac")))
                    .given(markerService).computeSignature(any());

            TestEntity entity = deletedEntity(8L, original, trashed, "h-8");

            assertThatThrownBy(() -> service.restoreFromTrash(entity, e -> Map.of()))
                    .isInstanceOf(MarkerWriteFailedException.class);

            verify(trashService).moveBack(trashed, original);
            verify(trashService).moveBackReverse(original, trashed);   // write 이전 예외도 역보상
            verify(markerService, never()).write(any(), any(), any()); // write 도달 안 함
            assertThat(entity.isDeleted()).isTrue();                   // @Transactional 롤백 영역 — DB 미전이
            assertThat(entity.getTrashedPath()).isNotNull();
        }

        @Test
        @DisplayName("restore attributeBuilder 실패 (moveBack 직후 첫 statement) → moveBackReverse + 원예외 재전파, DB 미전이")
        void restore_attributeBuilderThrows_compensates(@TempDir Path tmp) {
            Path trashed = tmp.resolve("trashed.iso");
            Path original = tmp.resolve("active/original.iso");
            writeFile(trashed);
            ensureDir(original.getParent());              // (원X·trashO) 게이트 통과

            Path markerFile = original.resolveSibling("original.iso.provision.json");
            given(markerService.resolveMarkerFile(original, MarkerLayout.SIDECAR)).willReturn(markerFile);

            TestEntity entity = deletedEntity(9L, original, trashed, "h-9");

            // caller lambda 가 throw (예: 부모 reference NPE) — moveBack 직후 첫 statement.
            assertThatThrownBy(() -> service.restoreFromTrash(entity, e -> {
                throw new IllegalStateException("attr-boom");
            })).isInstanceOf(IllegalStateException.class);

            verify(trashService).moveBack(trashed, original);
            verify(trashService).moveBackReverse(original, trashed);   // attributeBuilder 예외도 역보상
            verify(markerService, never()).computeSignature(any());
            verify(markerService, never()).write(any(), any(), any());
            assertThat(entity.isDeleted()).isTrue();                   // DB 미전이
            assertThat(entity.getTrashedPath()).isNotNull();
        }
    }

    // ==== matcher helpers (가독성) ====

    private static Path eqPath(Path p) {
        return org.mockito.ArgumentMatchers.eq(p);
    }

    private static MarkerLayout eqLayout() {
        return org.mockito.ArgumentMatchers.eq(MarkerLayout.SIDECAR);
    }

    private static void writeFile(Path p) {
        try {
            Files.createDirectories(p.getParent());
            Files.writeString(p, "x");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void ensureDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static TestEntity deletedEntity(Long id, Path path, Path trashedPath, String hash) {
        TestEntity e = new TestEntity(id, path, hash);
        e.softDelete();
        e.markTrashed(trashedPath.toString()); // caller 가 Path.of(getTrashedPath()) 로 사용
        return e;
    }

    private static TestEntity activeEntity(Long id, Path path) {
        return new TestEntity(id, path, "h");
    }

    /** 테스트 전용 entity — LifecycleEntity lifecycle + Markable 시그니처. reissueMarker 결과 캡처. */
    private static class TestEntity extends LifecycleEntity implements Markable {
        private final Long id;
        private final Path path;
        private final String manifestHash;
        String reissuedHash;
        String reissuedSignature;

        TestEntity(Long id, Path path, String manifestHash) {
            this.id = id;
            this.path = path;
            this.manifestHash = manifestHash;
        }

        @Override protected Long resourceId() { return id; }
        @Override protected LifecycleEntity parentLifecycle() { return null; }
        @Override public Long getResourceId() { return id; }
        @Override public ResourceType getResourceType() { return ResourceType.OS_ISO; }
        @Override public Path getResourcePath() { return path; }
        @Override public MarkerLayout getMarkerLayout() { return MarkerLayout.SIDECAR; }
        @Override public String getManifestHash() { return manifestHash; }
        @Override public String getMarkerSignature() { return null; }
        @Override public void reissueMarker(String hash, String signature) {
            this.reissuedHash = hash;
            this.reissuedSignature = signature;
        }
    }
}
