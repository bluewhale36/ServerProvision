package com.example.serverprovision.global.trash;

import com.example.serverprovision.global.entity.LifecycleEntity;
import com.example.serverprovision.global.marker.Markable;
import com.example.serverprovision.global.marker.MarkerContent;
import com.example.serverprovision.global.marker.MarkerLayout;
import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.trash.exception.GhostRowRestoreNotAllowedException;
import com.example.serverprovision.management.common.exception.RestorePathOccupiedException;
import com.example.serverprovision.management.common.exception.RestoreTargetUnreachableException;
import com.example.serverprovision.management.common.exception.RestoreTrashLostException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;

/**
 * MK3 — 4 자원 도메인 (ISO / BIOS / BMC / Subprogram) 이 공통으로 사용하는 trash 기반 lifecycle 흐름 헬퍼.
 *
 * <p>각 도메인 Service 의 softDelete / restore 메서드가 동일한 흐름 :
 * <ol>
 *   <li>마커 삭제</li>
 *   <li>자원 trash 이동</li>
 *   <li>{@code markTrashed} 기록</li>
 * </ol>
 * 으로 수렴하는 것을 단일 진입점으로 공통화. CLAUDE.md §중복된 코드와 가독성 (불가침) 정합.
 *
 * <p>도메인별 차이는 두 곳뿐 :
 * <ul>
 *   <li>마커 attributes 합성 (도메인 부속 메타) — caller 의 {@link Function} lambda 로 위임</li>
 *   <li>도메인-specific 가드 (이미 active 거절, 활성 자원 충돌 거절 등) — caller 가 본 메서드 호출 전 처리</li>
 * </ul>
 *
 * <p>본 헬퍼는 도메인을 모르므로 {@link Markable} 의 SPI 만 사용한다.
 * over-abstraction 우려 차단 — 새 도메인 자원이 추가되면 동일하게 본 헬퍼 호출 + lambda 만 작성하면 됨.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashLifecycleService {

    private final TrashService trashService;
    private final ProvisionMarkerService markerService;

    /**
     * 공통 soft-delete 흐름.
     * <ol>
     *   <li>{@link LifecycleEntity#softDelete()} — DB lifecycle 전이</li>
     *   <li>active 위치의 마커 파일 삭제 (sidecar 또는 in-tree)</li>
     *   <li>자원 파일 존재 시 trash 로 mv + {@link LifecycleEntity#markTrashed(String)}. 부재 시 (A2) DB 만 정리</li>
     *   <li>IN_TREE 마커가 함께 이동된 경우 trash 안에서 추가 삭제 (TRASH_MARKER_STALE 발생 방지)</li>
     * </ol>
     */
    public <T extends LifecycleEntity & Markable> void softDeleteToTrash(T entity) {
        Path resourcePath = entity.getResourcePath();
        MarkerLayout layout = entity.getMarkerLayout();

        entity.softDelete();

        Path activeMarker = markerService.resolveMarkerFile(resourcePath, layout);
        try {
            Files.deleteIfExists(activeMarker);
        } catch (IOException e) {
            log.warn("[trash] active 마커 삭제 실패 — TRASH_MARKER_STALE 후속 정리. path={}, msg={}",
                    activeMarker, e.getMessage());
        }

        if (Files.exists(resourcePath)) {
            Path trashed = trashService.moveToTrash(resourcePath, entity.getResourceType(), entity.getResourceId());
            // IN_TREE 마커가 트리와 함께 trash 로 따라간 경우 정리.
            Path movedMarker = markerService.resolveMarkerFile(trashed, layout);
            try {
                Files.deleteIfExists(movedMarker);
            } catch (IOException e) {
                log.warn("[trash] trash 내부 마커 삭제 실패. path={}, msg={}", movedMarker, e.getMessage());
            }
            entity.markTrashed(trashed.toString());
        } else {
            log.warn("[trash] 자원 부재 (A2 케이스) — DB 만 정리. type={} id={} path={}",
                    entity.getResourceType(), entity.getResourceId(), resourcePath);
        }
    }

    /**
     * 공통 restore 흐름.
     * <ol>
     *   <li>검증 (1) trash 실체 존재 — 부재 시 {@link RestoreTrashLostException}</li>
     *   <li>검증 (2) 원래 경로 부모 접근 가능 — {@link RestoreTargetUnreachableException}</li>
     *   <li>검증 (3) 원래 경로 동일 이름 점유 부재 — {@link RestorePathOccupiedException}</li>
     *   <li>trash → 원래 경로 mv + 마커 재발급 + {@link LifecycleEntity#restore()} + {@link LifecycleEntity#clearTrashed()}</li>
     * </ol>
     * 검증 (4) hash 충돌은 caller 가 S5-2-3 (cascade restore) 와 함께 통합 검증 — 본 헬퍼는 (1)~(3) 까지.
     *
     * <p>{@code trashed_path=null} 인 자원 (A2 — soft-delete 시 자원 부재였던 경우) 은 단순 lifecycle 복원.</p>
     */
    public <T extends LifecycleEntity & Markable> void restoreFromTrash(
            T entity, Function<T, Map<String, String>> attributeBuilder) {

        String trashedPathStr = entity.getTrashedPath();
        if (trashedPathStr == null) {
            // MK3-1 — trashed_path=null 분기 분리.
            //   (a) FS 자원이 DB.path 에 살아있음 → 단순 lifecycle 복원 (외부 mv 로 자원만 돌아온 케이스 등)
            //   (b) FS 자원도 부재 → ghost row → 명시적 거절 (휴지통 정리 / reconciliation drift apply 안내)
            if (!Files.exists(entity.getResourcePath())) {
                throw new GhostRowRestoreNotAllowedException(
                        entity.getResourceType().name() + "#" + entity.getResourceId());
            }
            entity.restore();
            entity.clearTrashed();
            return;
        }

        Path trashedPath = Path.of(trashedPathStr);
        Path originalPath = entity.getResourcePath();

        if (!Files.exists(trashedPath)) {
            throw new RestoreTrashLostException(trashedPathStr);
        }
        Path parent = originalPath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            throw new RestoreTargetUnreachableException(parent == null ? "(none)" : parent.toString());
        }
        if (Files.exists(originalPath)) {
            throw new RestorePathOccupiedException(originalPath.toString());
        }

        trashService.moveBack(trashedPath, originalPath);

        MarkerContent content = new MarkerContent(
                entity.getResourceType().name(),
                entity.getResourceId(),
                attributeBuilder.apply(entity),
                Instant.now(),
                entity.getManifestHash(),
                null);
        String signature = markerService.computeSignature(content);
        markerService.write(originalPath, entity.getMarkerLayout(), content.withSignature(signature));
        entity.reissueMarker(entity.getManifestHash(), signature);

        entity.restore();
        entity.clearTrashed();
    }
}
