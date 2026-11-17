package com.example.serverprovision.management.bmc.repository;

import com.example.serverprovision.management.bmc.entity.BoardBMC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BMC 펌웨어 영속 연산.
 */
public interface BmcRepository extends JpaRepository<BoardBMC, Long> {

	Optional<BoardBMC> findByIdAndBoardModel_Id(Long id, Long boardModelId);

	/** 순위 오름차순(1 = 최신) — 목록 · 정의서 폼 select · resolve 공유(E2-1-a, BiosRepository 와 대칭 계약). */
	List<BoardBMC> findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionRankAsc(Long boardModelId);

	/** 전체(삭제 포함) 순위 오름차순 — 재정렬 재번호 · 휴지통 겸용. */
	List<BoardBMC> findAllByBoardModel_IdOrderByVersionRankAsc(Long boardModelId);

	/** 순서 무관 소비(cascade · 집계 · 경고)용 — 순서 어휘 없는 이름(E2-1-a). */
	List<BoardBMC> findAllByBoardModel_IdAndIsDeletedFalse(Long boardModelId);

	/** 순서 무관 · 삭제 포함 — cascade 대상 수집용. */
	List<BoardBMC> findAllByBoardModel_Id(Long boardModelId);

	/** 신규 등록 선행 shift(E2-1-a) — 1위 자리 비우기. bulk 라 flush · clear 동반. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update BoardBMC b set b.versionRank = b.versionRank + 1 where b.boardModel.id = :boardModelId")
	int shiftAllVersionRanks(@org.springframework.data.repository.query.Param("boardModelId") Long boardModelId);

	/**
	 * S5-2-3 — 특정 보드의 soft-deleted BMC. Board restore cascade=true 시 일괄 복구 대상.
	 */
	List<BoardBMC> findAllByBoardModel_IdAndIsDeletedTrue(Long boardModelId);

	List<BoardBMC> findAllByBoardModel_IdIn(List<Long> boardModelIds);

	boolean existsByBoardModel_IdAndVersionAndIsDeletedFalse(Long boardModelId, String version);

	Optional<BoardBMC> findFirstByBoardModel_IdAndVersionAndIsDeletedTrue(Long boardModelId, String version);

	Optional<BoardBMC> findFirstByBoardModel_IdAndTreeRootPathAndIsDeletedFalse(Long boardModelId, String treeRootPath);

	List<BoardBMC> findAllByIsDeletedFalse();

	// ---- MK2 (단계 A · 메타) ------------------------------------------

	/**
	 * 업로드 전 중복 확인 창 후보 — 같은 (메인보드, 버전)의 휴지통 행(HF30-2 부터 창 후보는 휴지통뿐).
	 */
	List<BoardBMC> findAllByBoardModel_IdAndVersionAndIsDeletedTrue(Long boardModelId, String version);

	/**
	 * 같은 (메인보드, 버전)의 살아 있는 행(활성 · Deprecated) — 등록 충돌 안내가 상태를 고르려고 모두 읽는다(HF30-2).
	 */
	List<BoardBMC> findAllByBoardModel_IdAndVersionAndIsDeletedFalse(Long boardModelId, String version);

	// ---- MK2 (단계 B · 해시 충돌) -------------------------------------

	/**
	 * 업로드 뒤 중복 확인 창 후보 — 내용(해시)이 같은 휴지통 자원 (보드 scope). HF30-2 부터 Deprecated 는 후보가 아니다.
	 */
	@Query("select b from BoardBMC b where b.boardModel.id = :boardModelId and b.manifestHash = :manifestHash and b.isDeleted = true")
	List<BoardBMC> findHashConflictCandidates(
			@org.springframework.data.repository.query.Param("boardModelId") Long boardModelId,
			@org.springframework.data.repository.query.Param("manifestHash") String manifestHash
	);

	// ---- MK3 — Trash TTL 정책 -------------------------------

	/**
	 * TTL worker — 만료 자원 일괄 조회.
	 */
	List<BoardBMC> findByIsDeletedTrueAndTrashedAtBefore(Instant threshold);

	/**
	 * TTL 알림 worker — 만료 임박 자원.
	 */
	List<BoardBMC> findByIsDeletedTrueAndTrashedAtBetween(Instant start, Instant end);

	/**
	 * TrashController.list — 휴지통 페이지 합본 조회.
	 */
	List<BoardBMC> findByIsDeletedTrueOrderByTrashedAtDesc();

	/**
	 * MK3-1 — ghost 후보 (DB-only soft-deleted). FS 부재 검증은 scanner 가 추가.
	 */
	List<BoardBMC> findByIsDeletedTrueAndTrashedPathIsNull();
}
