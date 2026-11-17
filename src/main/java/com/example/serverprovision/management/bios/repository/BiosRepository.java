package com.example.serverprovision.management.bios.repository;

import com.example.serverprovision.management.bios.entity.BoardBIOS;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BIOS 번들 영속 연산.
 * <ul>
 *   <li>단건 조회는 {@code (id, boardId)} 쌍으로 수행 — URL 변조로 다른 보드의 BIOS 를 건드리는 것을 차단.</li>
 *   <li>상태(is_deleted / is_enabled) 분기 선택은 Service 레이어에서 처리한다.</li>
 *   <li>목록 조회는 보드 scope 가 기본 — Miller 는 보드별 BIOS 리스트로 렌더한다.</li>
 *   <li>v3 부터 파일 단위 중복 검사(filePath / checksum) 는 의미를 잃어 제거. 번들 단위 dedup 은
 *       {@code manifestHash} 로 집계한다 (선택적 소프트 경고용).</li>
 * </ul>
 */
public interface BiosRepository extends JpaRepository<BoardBIOS, Long> {

	/**
	 * 단건 — 부모 보드 FK 까지 검증해서 반환. Controller 의 경로 스코프 안전장치.
	 */
	Optional<BoardBIOS> findByIdAndBoardModel_Id(Long id, Long boardModelId);

	// ---- 목록 · 순위 (Miller C2 · E2-1-a) ------------------------------

	/**
	 * 특정 보드의 활성(미삭제) BIOS 를 버전 순위 오름차순(1 = 최신)으로 — 목록 · 정의서 폼 select ·
	 * resolve(E2-1-b) 가 공유하는 "운영자가 정한 순서" 소비 질의. 문자열 정렬 대체(E2-1-a).
	 */
	List<BoardBIOS> findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(Long boardModelId);

	/**
	 * 특정 보드의 전체 BIOS (삭제 포함) 순위 오름차순 — 재정렬 재번호와 휴지통 겸용 보기용.
	 */
	List<BoardBIOS> findAllByBoardModel_IdOrderByVersionRankAsc(Long boardModelId);

	/**
	 * 순서 무관 소비(cascade · 집계 · 경고 anyMatch)용 — 이름에 순서 어휘를 두지 않는다.
	 * 옛 {@code …OrderByVersionDesc} 는 정렬을 보장한다는 거짓 이름이 되어 제거했다(E2-1-a).
	 */
	List<BoardBIOS> findAllByBoardModel_IdAndIsDeletedFalse(Long boardModelId);

	/** 순서 무관 · 삭제 포함 — cascade 대상 수집용. */
	List<BoardBIOS> findAllByBoardModel_Id(Long boardModelId);

	/**
	 * 신규 등록 선행 shift(E2-1-a) — 보드의 전 행 순위를 +1 해 1위 자리를 비운다(신규 = 최신 기본).
	 * bulk 갱신이라 영속성 컨텍스트를 거치지 않으므로 flush · clear 를 함께 건다.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update BoardBIOS b set b.versionRank = b.versionRank + 1 where b.boardModel.id = :boardModelId")
	int shiftAllVersionRanks(@org.springframework.data.repository.query.Param("boardModelId") Long boardModelId);

	/**
	 * S5-2-3 — 특정 보드의 soft-deleted BIOS. Board restore cascade=true 시 일괄 복구 대상.
	 */
	List<BoardBIOS> findAllByBoardModel_IdAndIsDeletedTrue(Long boardModelId);

	/**
	 * 복수 보드 일괄 조회 (N+1 방지용). Service 에서 `findAllGrouped` 구성 시 활용.
	 */
	List<BoardBIOS> findAllByBoardModel_IdIn(List<Long> boardModelIds);

	// ---- 중복 검사 (쓰기 전제) ----------------------------------------

	/**
	 * 같은 보드에 같은 version 의 활성 BIOS 가 있는지 — Service 가 신규 등록 / 메타 수정 시 호출.
	 */
	boolean existsByBoardModel_IdAndVersionAndIsDeletedFalse(Long boardModelId, String version);

	/**
	 * 같은 보드에 같은 version 의 soft-deleted BIOS 가 있는지 조회 — 동일 (board, version) 재업로드 흐름에서
	 * 기존 레코드의 트리·marker 를 물리 삭제 + 레코드 하드 삭제 후 새로 등록하기 위한 선행 조회.
	 */
	Optional<BoardBIOS> findFirstByBoardModel_IdAndVersionAndIsDeletedTrue(Long boardModelId, String version);

	/**
	 * 같은 manifestHash 를 가진 활성 BIOS 가 이미 있는지 — Intent 응답 warnings 에 소프트 경고로 첨부.
	 * 하드 거절이 아니라 "이미 같은 내용의 번들이 다른 (board, version) 으로 등록돼 있음" 을 관리자에게 안내하기 위함.
	 */
	Optional<BoardBIOS> findFirstByManifestHashAndIsDeletedFalse(String manifestHash);

	/**
	 * 관리자 조회용 — 특정 trueRootPath 를 사용하는 활성 레코드 존재 확인 (marker 경로 충돌 감지 보조).
	 */
	Optional<BoardBIOS> findFirstByTreeRootPathAndIsDeletedFalse(String treeRootPath);

	// ---- MK1 reconciliation 인벤토리 -------------------------------

	/**
	 * Markable 인벤토리 (활성). MK1 PathReconciliationService 가 BIOS_BUNDLE 자원 수집 시 사용.
	 */
	List<BoardBIOS> findAllByIsDeletedFalse();

	// ---- MK2 (단계 A · 메타 사전 매칭) --------------------------------

	/**
	 * MK2 — 단계 A. (board, version) 메타가 같은 자원이 어떤 lifecycle 이든 존재하는지.
	 * upload-intent 응답의 {@code preExistingMatch} 안내용. 활성 / Deprecated / SoftDeleted 모두 포함.
	 */
	Optional<BoardBIOS> findFirstByBoardModel_IdAndVersion(Long boardModelId, String version);

	/**
	 * 업로드 전 중복 확인 창 후보 — 같은 (메인보드, 버전)의 휴지통 행(HF30-2 부터 창 후보는 휴지통뿐).
	 */
	List<BoardBIOS> findAllByBoardModel_IdAndVersionAndIsDeletedTrue(Long boardModelId, String version);

	/**
	 * 같은 (메인보드, 버전)의 살아 있는 행(활성 · Deprecated) — 등록 충돌 안내가 상태를 고르려고 모두 읽는다(HF30-2).
	 */
	List<BoardBIOS> findAllByBoardModel_IdAndVersionAndIsDeletedFalse(Long boardModelId, String version);

	// ---- MK2 (단계 B · 해시 충돌 후보) --------------------------------

	/**
	 * 업로드 뒤 중복 확인 창 후보 — 내용(해시)이 같은 휴지통 자원 (보드 scope). HF30-2 부터 Deprecated 는 후보가 아니다 —
	 * 버전이 다르면 활성 자원과 같은 내용이 허용되듯 Deprecated 와도 공존한다.
	 */
	@Query(
			"select b from BoardBIOS b where b.boardModel.id = :boardModelId "
					+ "and b.manifestHash = :manifestHash "
					+ "and b.isDeleted = true"
	)
	List<BoardBIOS> findHashConflictCandidates(
			@org.springframework.data.repository.query.Param("boardModelId") Long boardModelId,
			@org.springframework.data.repository.query.Param("manifestHash") String manifestHash
	);

	// ---- MK3 — Trash TTL 정책 -------------------------------

	/**
	 * TTL worker — 만료 자원 일괄 조회.
	 */
	List<BoardBIOS> findByIsDeletedTrueAndTrashedAtBefore(Instant threshold);

	/**
	 * TTL 알림 worker — 만료 임박 자원.
	 */
	List<BoardBIOS> findByIsDeletedTrueAndTrashedAtBetween(Instant start, Instant end);

	/**
	 * TrashController.list — 휴지통 페이지 합본 조회.
	 */
	List<BoardBIOS> findByIsDeletedTrueOrderByTrashedAtDesc();

	/**
	 * MK3-1 — ghost 후보 (DB-only soft-deleted). FS 부재 검증은 scanner 가 추가.
	 */
	List<BoardBIOS> findByIsDeletedTrueAndTrashedPathIsNull();
}
