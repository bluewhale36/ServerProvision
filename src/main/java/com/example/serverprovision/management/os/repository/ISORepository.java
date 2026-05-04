package com.example.serverprovision.management.os.repository;

import com.example.serverprovision.management.os.entity.ISO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ISO 영속 연산. 단건 조회는 (id, osImageId) 쌍으로 수행해서 URL 변조로 다른 OS 의 ISO 를 건드리는 것을 막는다.
 * 상태(is_deleted / is_enabled) 분기는 Service 레이어에서 처리한다.
 */
public interface ISORepository extends JpaRepository<ISO, Long> {

    Optional<ISO> findByIdAndOsImage_Id(Long id, Long osImageId);

    /**
     * 동일 checksum 을 가진 활성 ISO 중 가장 먼저 발견되는 것을 돌려준다.
     * 중복 업로드 방지에서 "이미 등록된 경로" 를 사용자에게 안내하기 위해 사용한다.
     */
    Optional<ISO> findFirstByChecksumAndIsDeletedFalse(String checksum);

    /**
     * 업로드 intent 사전 검사용 — 같은 OS 내 동일 isoPath 로 등록된 활성 ISO 조회.
     * 존재하면 "같은 경로에 이미 등록됨" 으로 사용자에게 알려 업로드 자체를 스킵한다.
     */
    Optional<ISO> findFirstByOsImage_IdAndIsoPathAndIsDeletedFalse(Long osImageId, String isoPath);

    // ---- MK2 — 메타 사전 경고 (단계 A) -------------------------------

    /**
     * 같은 OS 의 isoPath 가 일치하는 soft-deleted ISO 후보 (단계 A — sidecar 충돌 사전 경고).
     * intent 응답의 {@code preExistingMatch} 채울 때 사용. PURGED 자원은 row 부재라 후보가 안 됨.
     */
    Optional<ISO> findFirstByOsImage_IdAndIsoPathAndIsDeletedTrue(Long osImageId, String isoPath);

    /**
     * MK2 WAVE 2 — intent path nudge 후보 수집 (soft-deleted + Deprecated, 같은 OS + 같은 isoPath).
     */
    @Query("select i from ISO i where i.osImage.id = :osImageId and i.isoPath = :isoPath and (i.isDeleted = true or i.isDeprecated = true)")
    List<ISO> findIntentPathNudgeCandidates(@org.springframework.data.repository.query.Param("osImageId") Long osImageId,
                                            @org.springframework.data.repository.query.Param("isoPath") String isoPath);

    /**
     * MK2 WAVE 3 — Phase 1 hash check candidates : 같은 osImageId 의 휴지통 / Deprecated ISO 1건 이상 존재 여부 안내용.
     * client 가 SHA-256 계산해서 Phase 2 로 재호출할지 결정하는 sentinel. size 매칭 안 함 (size 컬럼 부재) — 단순 lifecycle 필터.
     */
    @Query("select i from ISO i where i.osImage.id = :osImageId and (i.isDeleted = true or i.isDeprecated = true)")
    List<ISO> findIntentHashCheckCandidates(@org.springframework.data.repository.query.Param("osImageId") Long osImageId);

    /**
     * MK2 WAVE 3 — Phase 2 hash match : client 가 보낸 SHA-256 과 일치하는 휴지통 / Deprecated ISO 후보.
     * 일치 시 IsoNudgeRequiredException (NUDGE_REQUIRED) — 사용자 3택.
     */
    @Query("select i from ISO i where i.osImage.id = :osImageId and i.manifestHash = :manifestHash and (i.isDeleted = true or i.isDeprecated = true)")
    List<ISO> findIntentHashNudgeCandidates(@org.springframework.data.repository.query.Param("osImageId") Long osImageId,
                                            @org.springframework.data.repository.query.Param("manifestHash") String manifestHash);

    // ---- MK2 — 해시 충돌 후보 (단계 B) -------------------------------

    /**
     * 동일 manifestHash 를 가진 soft-deleted 또는 deprecated ISO 후보들.
     * <p>활성 ISO 와의 해시 충돌은 그대로 hard conflict ({@code DuplicateISOContentException}) 이지만,
     * 휴지통/Deprecated 자원과의 충돌은 사용자가 confirm 하면 등록을 진행할 수 있는 nudge 흐름으로 분기한다.</p>
     */
    @Query("select i from ISO i where i.manifestHash = :hash and (i.isDeleted = true or i.isDeprecated = true)")
    List<ISO> findByManifestHashAndIsDeletedTrueOrIsDeprecatedTrue(String hash);

    // ---- MK1 reconciliation 인벤토리 -------------------------------

    /** Markable 인벤토리 (활성). MK1 PathReconciliationService 가 OS_ISO 자원 수집 시 사용. */
    List<ISO> findAllByIsDeletedFalse();

    /** soft-deleted 자원의 ID Set (D20). ORPHAN 분류 후처리에서 매칭 제외 용도. */
    @Query("select i.id from ISO i where i.isDeleted = true")
    Set<Long> findIdsByIsDeletedTrue();
}
