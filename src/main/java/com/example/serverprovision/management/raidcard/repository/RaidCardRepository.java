package com.example.serverprovision.management.raidcard.repository;

import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * RaidCard 영속 연산.
 *
 * <p>유일성 어휘(MA7 D7) — "살아 있는 카드"(is_deleted=false AND is_deprecated=false)만 (vendor,
 * modelName) 이 유일하다. DB 는 생성 컬럼 {@code active_identity} 의 유니크 인덱스가 같은 조건으로
 * 동시성까지 강제한다(ddl/MA7_raid_card.sql) — 서비스 검사는 친절한 409 안내, DB 는 최종 판정.</p>
 */
public interface RaidCardRepository extends JpaRepository<RaidCard, Long> {

	/**
	 * 기본 보기 : soft 삭제된 레코드 제외. Vendor 오름차순 → 등록일 내림차순(최신 등록이 상단).
	 */
	List<RaidCard> findAllByIsDeletedFalseOrderByVendorAscCreatedAtDesc();

	/**
	 * 휴지통 포함 보기 : 모든 레코드.
	 */
	List<RaidCard> findAllByOrderByVendorAscCreatedAtDesc();

	/**
	 * 단건 조회 (삭제된 레코드 제외). 수정/토글/삭제 시 사용.
	 */
	Optional<RaidCard> findByIdAndIsDeletedFalse(Long id);

	/**
	 * 복구/영구삭제 조회 (삭제된 레코드만 대상).
	 */
	Optional<RaidCard> findByIdAndIsDeletedTrue(Long id);

	/**
	 * 중복 등록 방지 — "살아 있는"(비삭제 · 비 Deprecated) 카드 안에서 (vendor, modelName) 유일성 검사.
	 * DB 생성 컬럼 유니크 인덱스 {@code uk_raid_card_active_identity} 와 이중 가드를 이룬다.
	 */
	boolean existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(RaidCardVendor vendor, String modelName);

	/**
	 * 메타 nudge 후보 (soft-deleted ∪ active+deprecated). 중복 등록 시도 시 충돌 후보 회수용 (MK2 선례).
	 */
	List<RaidCard> findAllByVendorAndModelNameAndIsDeletedTrue(RaidCardVendor vendor, String modelName);

	List<RaidCard> findAllByVendorAndModelNameAndIsDeprecatedTrueAndIsDeletedFalse(RaidCardVendor vendor, String modelName);

	/**
	 * soft-deleted 카드 (메타 자원). 휴지통 표시용.
	 */
	List<RaidCard> findAllByIsDeletedTrue();
}
