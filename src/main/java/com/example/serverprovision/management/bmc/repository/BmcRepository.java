package com.example.serverprovision.management.bmc.repository;

import com.example.serverprovision.management.bmc.entity.BoardBMC;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * BMC 펌웨어 영속 연산.
 */
public interface BmcRepository extends JpaRepository<BoardBMC, Long> {

    Optional<BoardBMC> findByIdAndBoardModel_Id(Long id, Long boardModelId);

    List<BoardBMC> findAllByBoardModel_IdAndIsDeletedFalseOrderByVersionDesc(Long boardModelId);

    List<BoardBMC> findAllByBoardModel_IdOrderByVersionDesc(Long boardModelId);

    List<BoardBMC> findAllByBoardModel_IdIn(List<Long> boardModelIds);

    boolean existsByBoardModel_IdAndVersionAndIsDeletedFalse(Long boardModelId, String version);

    Optional<BoardBMC> findFirstByBoardModel_IdAndVersionAndIsDeletedTrue(Long boardModelId, String version);

    Optional<BoardBMC> findFirstByBoardModel_IdAndTreeRootPathAndIsDeletedFalse(Long boardModelId, String treeRootPath);

    List<BoardBMC> findAllByIsDeletedFalse();

    @Query("select b.id from BoardBMC b where b.isDeleted = true")
    Set<Long> findIdsByIsDeletedTrue();

    // ---- MK2 (단계 A · 메타) ------------------------------------------

    /** WAVE 2 — 단계 A intent nudge 후보 수집 (soft-deleted). */
    List<BoardBMC> findAllByBoardModel_IdAndVersionAndIsDeletedTrue(Long boardModelId, String version);

    /** WAVE 2 — 단계 A intent nudge 후보 수집 (Deprecated 활성). */
    List<BoardBMC> findAllByBoardModel_IdAndVersionAndIsDeprecatedTrueAndIsDeletedFalse(Long boardModelId, String version);

    // ---- MK2 (단계 B · 해시 충돌) -------------------------------------

    /** WAVE 2 — 단계 B hash nudge 후보 (해시 동일 + soft-deleted/Deprecated). */
    @Query("select b from BoardBMC b where b.boardModel.id = :boardModelId and b.manifestHash = :manifestHash and (b.isDeleted = true or b.isDeprecated = true)")
    List<BoardBMC> findHashConflictCandidates(@org.springframework.data.repository.query.Param("boardModelId") Long boardModelId,
                                              @org.springframework.data.repository.query.Param("manifestHash") String manifestHash);
}
