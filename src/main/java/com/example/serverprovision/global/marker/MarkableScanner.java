package com.example.serverprovision.global.marker;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 도메인별 자원 인벤토리 공급자 SPI.
 * <p>각 도메인 (management/bios, management/os 등) 이 자기 자원 종류 1 개에 대해 본 인터페이스를
 * 구현해 등록한다. {@code PathReconciliationService} 는 도메인 모르고
 * 등록된 모든 scanner 를 합산해 전체 인벤토리를 얻는다.</p>
 *
 * <p>본 인터페이스는 도메인 ↔ 인프라 사이의 경계. 본체({@code maintenance/reconciliation/}) 도
 * 도메인을 직접 알지 않는다 — 본 SPI 만 사용.</p>
 */
public interface MarkableScanner {

    /** 본 scanner 가 책임지는 자원 종류. 1 도메인 = 1 ResourceType 가정. */
    ResourceType supportedType();

    /** 활성(미삭제) 자원만. PATH_DRIFT / MISSING / SIGNATURE_INVALID / HASH_MISMATCH 분류 비교 대상. */
    List<Markable> findActiveMarkables();

    /**
     * MK3-2 — 단일 자원 lookup. {@code PathReconciliationService.scanForResource} 에서 사용.
     * default 는 전체 인벤토리 stream filter (효율 떨어짐). 도메인이 repository.findById 로 override 권장.
     * <p>TODO(MK3-3): 외부 비판 §11.2 의 SPI 분리 시 {@code MarkableInventory} 인터페이스로 이전.</p>
     */
    default Optional<Markable> findActiveMarkableById(Long resourceId) {
        return findActiveMarkables().stream()
                .filter(m -> resourceId.equals(m.getResourceId()))
                .findFirst();
    }

    /**
     * soft-deleted 자원의 ID 셋 (D20). 디스크에 마커가 그대로 남아있을 때
     * ORPHAN 으로 잘못 분류되지 않도록 ORPHAN 후처리에서 매칭 제외.
     * 복구 가능성을 위해 마커는 보존하되 활성 인벤토리엔 포함시키지 않는 절충.
     */
    Set<Long> findSoftDeletedResourceIds();

    /**
     * PATH_DRIFT 자동 적용 시 호출. 도메인이 자기 엔티티의 path 필드를 newPath 로 업데이트하고 영속화한다.
     * 마커 파일은 이미 newPath 로 옮겨진 상태가 전제 (관리자가 mv 한 후 자동 적용을 누른 경우).
     */
    void applyDriftedPath(Long resourceId, Path newPath);

    /**
     * deep scan 시 manifestHash 재계산. 단일 파일은 SHA-256(file bytes), 디렉토리는 canonicalized 트리 hash.
     * 자원이 더 이상 존재하지 않으면 {@code Optional.empty()}.
     */
    Optional<String> recomputeManifestHash(Markable markable);

    // ---- MK3 — Trash 측 SPI (default 는 trash 미적용 도메인) -------------------------------

    /** 휴지통 자원 전체 (TrashController.list 합본 용도). 도메인이 trash 적용이면 override. */
    default List<Markable> findTrashed() {
        return List.of();
    }

    /** TTL 만료 자원 (TtlWorker.purgeExpired 용도). */
    default List<Markable> findTrashedBefore(Instant threshold) {
        return List.of();
    }

    /** TTL 알림 임박 자원 (TtlWorker.notifyUpcomingExpiration 용도). */
    default List<Markable> findTrashedBetween(Instant start, Instant end) {
        return List.of();
    }

    /** TtlExtensionService 의 자원별 보존기간 연장 — 도메인이 자기 entity 의 trashed_at 만 갱신. */
    default void extendTrashTtl(Long resourceId) {
        throw new UnsupportedOperationException(supportedType() + " 는 trash TTL 연장을 지원하지 않습니다.");
    }

    /**
     * MK3 — 휴지통 페이지에서 복원 액션. 도메인이 자기 service 의 restore 메서드 호출 (부모 ID 자체 lookup).
     * 4 단계 검증 + 마커 재발급은 도메인 service 가 trashLifecycleService 위임.
     */
    default void restoreFromTrash(Long resourceId) {
        throw new UnsupportedOperationException(supportedType() + " 는 trash 복원을 지원하지 않습니다.");
    }

    /**
     * MK3 — 휴지통 페이지에서 영구삭제 액션. 도메인이 자기 service 의 purge 메서드 호출.
     * S5-2-2 의 typed-name 검증은 후속 sub-slice — 본 메서드는 단순 purge.
     */
    default void purgeFromTrash(Long resourceId) {
        throw new UnsupportedOperationException(supportedType() + " 는 trash 영구삭제를 지원하지 않습니다.");
    }

    // ---- MK3-1 — Ghost row 일급 개념 ----------------------------------------
    //
    //  Ghost = is_deleted=true AND trashed_at=null AND trashed_path=null AND Files.notExists(DB.path).
    //  DB-truth 와 FS-truth 가 모두 음수인 dead row — 어느 쪽도 회복 불가.
    //  4 영역 (nudge 후보 필터 / reconciliation drift / restore 거절 / 휴지통 표시) 이 동일 정의 공유.

    /**
     * MK3-1 — 단일 자원이 ghost 상태인지 판정. nudge 후보 필터 / restore 거절 분기에서 호출.
     * default 는 trash 미적용 도메인 — 항상 false. trash 적용 도메인은 override.
     */
    default boolean isGhost(Long resourceId) {
        return false;
    }

    /**
     * MK3-1 — 본 scanner 가 책임지는 ghost row 의 Markable 목록. {@code PathReconciliationService} 가
     * 합산해 GHOST_DB_ROW drift 로 보고 (oldPath = DB.resourcePath, newPath = null). trash 미적용 도메인은 빈 리스트.
     */
    default List<Markable> findGhostMarkables() {
        return List.of();
    }

    /**
     * MK3-1 — ghost 정리 (DB row hard-delete). reconciliation drift apply 또는 휴지통 clear-ghost
     * 진입점에서 호출. 도메인 service 의 hard-delete 흐름에 위임 — 자원 / 마커 IO 는 이미 부재이므로
     * row 삭제만 수행하면 충분.
     */
    default void applyGhostClear(Long resourceId) {
        throw new UnsupportedOperationException(supportedType() + " 는 ghost 정리를 지원하지 않습니다.");
    }

    /**
     * MK3-2 (DCM3-2.5) — softDelete reject 의 "강제 정리" 진입점. lifecycle 상태 / FS 자원 존재 여부와
     * 무관하게 DB row hard-delete 수행. {@link #applyGhostClear} 와 분리한 이유 :
     * <ul>
     *   <li>{@code applyGhostClear} 는 GhostEvaluator 검증 통과 (ghost 상태) 만 처리</li>
     *   <li>{@code applyForcedClear} 는 사용자 명시 액션이므로 검증 없이 처리 — 호출 의도 명확화</li>
     * </ul>
     * <p>TODO(MK3-3): 외부 비판 §11.2 의 SPI 분리 시 {@code MarkableGhostOperator} 로 이전 (또는 별 인터페이스).</p>
     */
    default void applyForcedClear(Long resourceId) {
        throw new UnsupportedOperationException(supportedType() + " 는 forced clear 를 지원하지 않습니다.");
    }
}
