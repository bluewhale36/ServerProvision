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
}
