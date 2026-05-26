package com.example.serverprovision.management.os.repository;

import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * OSMetadata 영속 연산. 모든 목록 메서드는 {@code @EntityGraph} 로 자식 ISO 를 같이 로드해 Miller 상세 렌더의 N+1 을 막는다.
 */
public interface OSMetadataRepository extends JpaRepository<OSMetadata, Long> {

	/**
	 * 기본 보기 : soft 삭제된 레코드 제외. OSName 이름 오름차순 → 등록일 내림차순(최신 버전이 상단).
	 */
	@EntityGraph(attributePaths = "isos")
	List<OSMetadata> findAllByIsDeletedFalseOrderByOsNameAscCreatedAtDesc();

	/**
	 * 휴지통 포함 보기 : 모든 레코드.
	 */
	@EntityGraph(attributePaths = "isos")
	List<OSMetadata> findAllByOrderByOsNameAscCreatedAtDesc();

	/**
	 * 단건 조회 (삭제된 레코드 제외). 수정/토글/삭제 시 사용.
	 */
	Optional<OSMetadata> findByIdAndIsDeletedFalse(Long id);

	/**
	 * 복구 조회 (삭제된 레코드만 대상).
	 */
	Optional<OSMetadata> findByIdAndIsDeletedTrue(Long id);

	/**
	 * 중복 등록 방지 — 삭제된 동일 조합은 중복으로 취급하지 않는다.
	 */
	boolean existsByOsNameAndOsVersionAndIsDeletedFalse(OSName osName, String osVersion);

	/**
	 * MK2 — 단계 A (메타 사전 경고). 같은 (OSName, osVersion) 의 soft-deleted 레코드 후보를 찾는다.
	 * 사용자가 같은 OS + 같은 버전 메타로 새 등록을 시도할 때 "이미 휴지통에 같은 자원이 있다" 안내용.
	 */
	Optional<OSMetadata> findFirstByOsNameAndOsVersionAndIsDeletedTrue(OSName osName, String osVersion);

	/**
	 * R1-1 — {@code create()} 진입 시점 충돌 탐지용 단일 쿼리. 동일 (OSName, osVersion) 의 모든
	 * lifecycle 상태 (ACTIVE / DEPRECATED / SOFT_DELETED) row 를 한 번에 반환한다.
	 *
	 * <p>호출 측은 {@link com.example.serverprovision.global.lifecycle.LifecycleStage#of(boolean, boolean)}
	 * 로 stream 그룹핑하여 분기한다. 옛 MK2 WAVE 1 의 분리된 두 쿼리
	 * ({@code findAllByOsNameAndOsVersionAndIsDeletedTrue} +
	 *  {@code findAllByOsNameAndOsVersionAndIsDeprecatedTrueAndIsDeletedFalse}) 를 대체한다.</p>
	 */
	List<OSMetadata> findAllByOsNameAndOsVersion(OSName osName, String osVersion);

	/**
	 * S5-2-3+ — soft-deleted OS metadata (메타 자원). 휴지통 표시 + cascade restore 흐름에서 활용.
	 */
	List<OSMetadata> findAllByIsDeletedTrue();
}
