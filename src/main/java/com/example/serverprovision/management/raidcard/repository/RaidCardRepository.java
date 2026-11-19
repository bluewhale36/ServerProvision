package com.example.serverprovision.management.raidcard.repository;

import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * RaidCard 영속 연산.
 *
 * <p>유일성 어휘(HF30-1 — MA7 D7 을 뒤집음) — (vendor, modelName) 은 휴지통 · Deprecated 를 포함한 모든 상태에서 유일하다.
 * DB 는 {@code uk_raid_card_vendor_model_name}(ddl/HF30_meta_identity.sql)이 동시성까지 강제한다 — 서비스 검사는 상태별 409 안내,
 * DB 는 최종 판정.</p>
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
	 * 복구 · Deprecated 해제의 안전망(HF30-1 이후 DB UNIQUE 가 휴지통까지 점유해 실제로는 겹칠 수 없다).
	 */
	boolean existsByVendorAndModelNameAndIsDeletedFalseAndIsDeprecatedFalse(RaidCardVendor vendor, String modelName);

	/**
	 * 같은 (vendor, modelName) 의 모든 행(상태 무관) — HF30-1 등록 · 이름 수정 충돌 판정. UNIQUE 이후에는 0 또는 1 행이지만,
	 * 그 이전 옛 데이터의 다건을 견디도록 목록으로 받는다.
	 */
	List<RaidCard> findAllByVendorAndModelName(RaidCardVendor vendor, String modelName);

	/**
	 * soft-deleted 카드 (메타 자원). 휴지통 표시용.
	 */
	List<RaidCard> findAllByIsDeletedTrue();
}
