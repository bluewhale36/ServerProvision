package com.example.serverprovision.execution.repository;

import java.util.Optional;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;

import com.example.serverprovision.execution.entity.ProvisioningHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProvisioningHistoryRepository extends JpaRepository<ProvisioningHistory, UUID> {

    /**
     * 상세용 — 단일 서버의 세부 단계 이력. 시작 시각 순(미시작 단계는 뒤로).
     */
    @Query("select s from ProvisioningHistory s where s.guestServer.id = :serverId order by s.startedAt asc nulls last")
    List<ProvisioningHistory> findAllByServerIdOrderByStartedAt(@Param("serverId") UUID serverId);

    /** E3.5-3 — step 별 최신 행(동결 계획 조회 · 보류 사유 중복 억제의 판정 입력). */
    Optional<ProvisioningHistory> findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(
            UUID guestServerId, ProvisioningPhaseStep stepCode);

    /** step 의 가장 최근 <b>특정 상태</b> 행 — "지금 열려 있는 RUNNING 행" 처럼 뒤에 다른 상태 행이 쌓여도 가려지지 않는다(E4-1-a-3 CP5 F-1). */
    Optional<ProvisioningHistory> findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
            UUID guestServerId, ProvisioningPhaseStep stepCode, com.example.serverprovision.execution.enums.ProvisioningStatus status);
}
