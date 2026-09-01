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

    /**
     * E4 인계 계약(E3.5-4 결정 5) — "이 서버의 OS 영역 볼륨". 계획이 OS 를 최대 1개로 보장하므로
     * findFirst 로 충분하다. 소비 배선은 OS 설치 슬라이스 몫.
     */
    java.util.Optional<RaidVolume> findFirstByGuestServer_IdAndVolumeRole(
            UUID guestServerId, com.example.serverprovision.execution.engine.raid.PlannedVolumeRole volumeRole);
}
