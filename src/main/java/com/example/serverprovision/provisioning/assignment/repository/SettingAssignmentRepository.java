package com.example.serverprovision.provisioning.assignment.repository;

import com.example.serverprovision.provisioning.assignment.entity.SettingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettingAssignmentRepository extends JpaRepository<SettingAssignment, Long> {

    /** 활성 스냅샷(supersededAt IS NULL) — 게스트당 최대 1개(활성 유일성, 결정 D-A). */
    Optional<SettingAssignment> findByGuestServer_IdAndSupersededAtIsNull(UUID guestServerId);

    /** 활성 유일성 가드 — 이미 활성 스냅샷이 있으면 재할당은 409(안전망, UI 1차 차단). */
    boolean existsByGuestServer_IdAndSupersededAtIsNull(UUID guestServerId);
}
