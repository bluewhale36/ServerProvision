package com.example.serverprovision.provisioning.biossetting.repository;

import com.example.serverprovision.provisioning.biossetting.entity.BiosRegistrySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BiosRegistrySnapshotRepository extends JpaRepository<BiosRegistrySnapshot, Long> {

    Optional<BiosRegistrySnapshot> findByBoardModel_IdAndBiosVersion(Long boardModelId, String biosVersion);

    /** 목표 버전 스냅샷이 없을 때의 폴백 — 이 보드에서 가장 최근에 채집한 것. */
    Optional<BiosRegistrySnapshot> findFirstByBoardModel_IdOrderByCapturedAtDesc(Long boardModelId);

    boolean existsByBoardModel_Id(Long boardModelId);
}
