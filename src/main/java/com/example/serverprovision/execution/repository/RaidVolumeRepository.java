package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.entity.RaidVolume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RaidVolumeRepository extends JpaRepository<RaidVolume, UUID> {

    List<RaidVolume> findAllByGuestServer_Id(UUID guestServerId);

    /** 재집행 replace 의 선행 삭제 — 원장이 아니라 "현재 실물" 표라 지우고 다시 쓴다. */
    void deleteByGuestServer_Id(UUID guestServerId);
}
