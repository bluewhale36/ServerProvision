package com.example.serverprovision.management.os.repository;

import com.example.serverprovision.management.os.entity.ISO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * ISO 영속 연산. 단건 조회는 (id, osMetadataId) 쌍으로 수행해서 URL 변조로 다른 OS 의 ISO 를 건드리는 것을 막는다.
 * 상태(is_deleted / is_enabled) 분기는 Service 레이어에서 처리한다.
 */
public interface ISORepository extends JpaRepository<ISO, Long> {

	Optional<ISO> findByIdAndOsMetadata_Id(Long id, Long osMetadataId);

	/**
	 * 같은 OS 에서 내용(해시)이 같은 살아 있는 ISO(활성 · Deprecated) — 같은 내용은 OS 버전마다 하나만 둔다(HF30-2 · U4).
	 * 등록 충돌 안내가 상태를 고르려고 모두 읽는다.
	 */
	List<ISO> findAllByOsMetadata_IdAndManifestHashAndIsDeletedFalse(Long osMetadataId, String manifestHash);

	/**
	 * 업로드 intent 사전 검사용 — 같은 OS 내 동일 isoPath 로 등록된 활성 ISO 조회.
	 * 존재하면 "같은 경로에 이미 등록됨" 으로 사용자에게 알려 업로드 자체를 스킵한다.
	 */
	Optional<ISO> findFirstByOsMetadata_IdAndIsoPathAndIsDeletedFalse(Long osMetadataId, String isoPath);

	// ---- MK2 — 메타 사전 경고 (단계 A) -------------------------------

	/**
	 * 같은 OS 의 isoPath 가 일치하는 soft-deleted ISO 후보 (단계 A — sidecar 충돌 사전 경고).
	 * intent 응답의 {@code preExistingMatch} 채울 때 사용. PURGED 자원은 row 부재라 후보가 안 됨.
	 */
	Optional<ISO> findFirstByOsMetadata_IdAndIsoPathAndIsDeletedTrue(Long osMetadataId, String isoPath);

	/**
	 * 업로드 전 중복 확인 창 후보 — 같은 OS · 같은 isoPath 의 휴지통 ISO(HF30-2 부터 휴지통뿐).
	 */
	@Query("select i from ISO i where i.osMetadata.id = :osMetadataId and i.isoPath = :isoPath and i.isDeleted = true")
	List<ISO> findIntentPathNudgeCandidates(
			@org.springframework.data.repository.query.Param("osMetadataId") Long osMetadataId,
			@org.springframework.data.repository.query.Param("isoPath") String isoPath
	);

	/**
	 * MK2 WAVE 3 — Phase 1 hash check candidates : 같은 osMetadataId 의 휴지통 / Deprecated ISO 1건 이상 존재 여부 안내용.
	 * client 가 SHA-256 계산해서 Phase 2 로 재호출할지 결정하는 sentinel. size 매칭 안 함 (size 컬럼 부재) — 단순 lifecycle 필터.
	 */
	@Query("select i from ISO i where i.osMetadata.id = :osMetadataId and (i.isDeleted = true or i.isDeprecated = true)")
	List<ISO> findIntentHashCheckCandidates(@org.springframework.data.repository.query.Param("osMetadataId") Long osMetadataId);

	/**
	 * 업로드 전 중복 확인 창 후보 — client 가 보낸 SHA-256 과 일치하는 같은 OS 의 휴지통 ISO(HF30-2 부터 휴지통뿐).
	 * 같은 내용의 살아 있는 ISO 는 창 없이 상태별 안내로 막는다.
	 */
	@Query("select i from ISO i where i.osMetadata.id = :osMetadataId and i.manifestHash = :manifestHash and i.isDeleted = true")
	List<ISO> findIntentHashNudgeCandidates(
			@org.springframework.data.repository.query.Param("osMetadataId") Long osMetadataId,
			@org.springframework.data.repository.query.Param("manifestHash") String manifestHash
	);

	// ---- MK1 reconciliation 인벤토리 -------------------------------

	/**
	 * Markable 인벤토리 (활성). MK1 PathReconciliationService 가 OS_ISO 자원 수집 시 사용.
	 */
	List<ISO> findAllByIsDeletedFalse();

	// ---- MK3 — Trash TTL 정책 -------------------------------

	/**
	 * TTL worker — 만료 자원 (trashed_at + TTL < now) 일괄 조회.
	 */
	List<ISO> findByIsDeletedTrueAndTrashedAtBefore(Instant threshold);

	/**
	 * TTL 알림 worker — 만료 임박 자원 (between window).
	 */
	List<ISO> findByIsDeletedTrueAndTrashedAtBetween(Instant start, Instant end);

	/**
	 * TrashController.list — 휴지통 페이지 합본 조회.
	 */
	List<ISO> findByIsDeletedTrueOrderByTrashedAtDesc();

	/**
	 * MK3-1 — ghost 후보 (DB-only soft-deleted, trash 미적용). FS 부재 검증은 scanner 가 추가 수행.
	 */
	List<ISO> findByIsDeletedTrueAndTrashedPathIsNull();
}
