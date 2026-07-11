package com.example.serverprovision.management.subprogram.repository;

import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Subprogram (Driver + Utility) 영속 연산.
 * <p>{@code boardModel} 이 nullable 이라 BMC/BIOS 의 단순 메서드 네임 규칙 으로 표현 안 되는 케이스
 * (공용 자원 lookup) 가 일부 있어 {@code @Query} 를 곁들인다.</p>
 */
public interface SubprogramRepository extends JpaRepository<Subprogram, Long> {

	Optional<Subprogram> findByIdAndIsDeletedFalse(Long id);

	List<Subprogram> findAllByKindAndIsDeletedFalse(SubprogramKind kind);

	List<Subprogram> findAllByKind(SubprogramKind kind);

	List<Subprogram> findAllByIsDeletedFalse();

	/* ───── 보드별 lookup (BoardModel FK not null) ───── */

	@Query(
			"""
					select s from Subprogram s
					where s.kind = :kind
					  and s.boardModel.id = :boardId
					  and s.name = :name
					  and s.version = :version
					  and s.isDeleted = false
					"""
	)
	Optional<Subprogram> findActiveByBoardKey(
			@Param("kind") SubprogramKind kind,
			@Param("boardId") Long boardId,
			@Param("name") String name,
			@Param("version") String version
	);

	@Query(
			"""
					select s from Subprogram s
					where s.kind = :kind
					  and s.boardModel.id = :boardId
					  and s.name = :name
					  and s.version = :version
					  and s.isDeleted = true
					"""
	)
	Optional<Subprogram> findSoftDeletedByBoardKey(
			@Param("kind") SubprogramKind kind,
			@Param("boardId") Long boardId,
			@Param("name") String name,
			@Param("version") String version
	);

	/* ───── 공용 lookup (boardModel IS NULL) ───── */

	@Query(
			"""
					select s from Subprogram s
					where s.kind = :kind
					  and s.boardModel is null
					  and s.name = :name
					  and s.version = :version
					  and s.isDeleted = false
					"""
	)
	Optional<Subprogram> findActiveByCommonKey(
			@Param("kind") SubprogramKind kind,
			@Param("name") String name,
			@Param("version") String version
	);

	@Query(
			"""
					select s from Subprogram s
					where s.kind = :kind
					  and s.boardModel is null
					  and s.name = :name
					  and s.version = :version
					  and s.isDeleted = true
					"""
	)
	Optional<Subprogram> findSoftDeletedByCommonKey(
			@Param("kind") SubprogramKind kind,
			@Param("name") String name,
			@Param("version") String version
	);

	/* ───── 트리 루트 충돌 검사 (활성, scope 무관) ───── */

	Optional<Subprogram> findFirstByTreeRootPathAndIsDeletedFalse(String treeRootPath);

	/* ───── R3-1 — BoardModel cascade SPI (board-scoped, kind 무관. boardModel.id 매칭이라 공용(null FK) 자동 제외) ───── */

	List<Subprogram> findAllByBoardModel_Id(Long boardModelId);

	List<Subprogram> findAllByBoardModel_IdAndIsDeletedFalse(Long boardModelId);

	List<Subprogram> findAllByBoardModel_IdAndIsDeletedTrue(Long boardModelId);

	List<Subprogram> findAllByBoardModel_IdIn(List<Long> boardModelIds);

	/* ───── Miller C2 목록 (kind + scope 별) ───── */

	@Query(
			"""
					select s from Subprogram s
					where s.kind = :kind
					  and s.boardModel.id = :boardId
					order by s.version desc
					"""
	)
	List<Subprogram> findByKindAndBoardId(
			@Param("kind") SubprogramKind kind,
			@Param("boardId") Long boardId
	);

	@Query(
			"""
					select s from Subprogram s
					where s.kind = :kind
					  and s.boardModel is null
					order by s.version desc
					"""
	)
	List<Subprogram> findByKindAndCommonScope(@Param("kind") SubprogramKind kind);

	/* ───── MK1 reconciliation 용 ───── */

	/* ───── MK2 — nudge 흐름 (소프트 삭제된 동일 키 후보) ───── */

	/**
	 * MK2 — soft-deleted 충돌 후보 단건 lookup. boardScope 가 nullable 이라 단순 메서드 네임으로 표현 안 되어
	 * board / common 두 변형을 함께 둔다 ({@link #findSoftDeletedByBoardKey} / {@link #findSoftDeletedByCommonKey}).
	 * 본 메서드는 두 케이스를 한 번에 처리하기 위한 조회 — boardScope 가 null 이면 공용, 아니면 보드별.
	 */
	@Query(
			"""
					select s from Subprogram s
					where s.kind = :kind
					  and (
					        (:boardId is null and s.boardModel is null)
					     or (s.boardModel.id = :boardId)
					  )
					  and s.name = :name
					  and s.version = :version
					  and s.isDeleted = true
					"""
	)
	Optional<Subprogram> findSoftDeletedConflictCandidate(
			@Param("kind") SubprogramKind kind,
			@Param("boardId") Long boardId,
			@Param("name") String name,
			@Param("version") String version
	);

	/**
	 * MK2 단계 A — 활성 (kind, scope, name, version) 메타 충돌 사전 안내.
	 */
	@Query(
			"""
					select case when count(s) > 0 then true else false end from Subprogram s
					where s.kind = :kind
					  and (
					        (:boardId is null and s.boardModel is null)
					     or (s.boardModel.id = :boardId)
					  )
					  and s.name = :name
					  and s.version = :version
					  and s.isDeleted = false
					"""
	)
	boolean existsActiveMatchingKey(
			@Param("kind") SubprogramKind kind,
			@Param("boardId") Long boardId,
			@Param("name") String name,
			@Param("version") String version
	);

	/* ───── MK2 WAVE 2 — intent 메타 nudge 후보 (soft-deleted + Deprecated) ───── */

	@Query(
			"""
					select s from Subprogram s
					where s.kind = :kind
					  and (
					        (:boardId is null and s.boardModel is null)
					     or (s.boardModel.id = :boardId)
					  )
					  and s.name = :name
					  and s.version = :version
					  and (s.isDeleted = true or s.isDeprecated = true)
					"""
	)
	List<Subprogram> findIntentNudgeCandidates(
			@Param("kind") SubprogramKind kind,
			@Param("boardId") Long boardId,
			@Param("name") String name,
			@Param("version") String version
	);

	/* ───── MK2 WAVE 2 — hash 충돌 후보 (soft-deleted + Deprecated, 같은 scope) ───── */

	@Query(
			"""
					select s from Subprogram s
					where s.kind = :kind
					  and (
					        (:boardId is null and s.boardModel is null)
					     or (s.boardModel.id = :boardId)
					  )
					  and s.manifestHash = :manifestHash
					  and (s.isDeleted = true or s.isDeprecated = true)
					"""
	)
	List<Subprogram> findHashConflictCandidates(
			@Param("kind") SubprogramKind kind,
			@Param("boardId") Long boardId,
			@Param("manifestHash") String manifestHash
	);

	// ---- MK3 — Trash TTL 정책 -------------------------------

	/**
	 * TTL worker — 만료 자원 일괄 조회.
	 */
	List<Subprogram> findByIsDeletedTrueAndTrashedAtBefore(Instant threshold);

	/**
	 * TTL 알림 worker — 만료 임박 자원.
	 */
	List<Subprogram> findByIsDeletedTrueAndTrashedAtBetween(Instant start, Instant end);

	/**
	 * TrashController.list — 휴지통 페이지 합본 조회.
	 */
	List<Subprogram> findByIsDeletedTrueOrderByTrashedAtDesc();

	/**
	 * MK3-1 — ghost 후보 (DB-only soft-deleted). FS 부재 검증은 scanner 가 추가.
	 */
	List<Subprogram> findByIsDeletedTrueAndTrashedPathIsNull();
}
