package com.example.serverprovision.global.marker;

import java.nio.file.Path;
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
}
