package com.example.serverprovision.execution.repository;

import com.example.serverprovision.execution.entity.SetupStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SetupStepRepository extends JpaRepository<SetupStep, UUID> {

    /**
     * 상세용 — 단일 서버의 세부 단계 이력. 시작 시각 순(미시작 단계는 뒤로).
     */
    @Query("select s from SetupStep s where s.guestServer.id = :serverId order by s.startedAt asc nulls last")
    List<SetupStep> findAllByServerIdOrderByStartedAt(@Param("serverId") UUID serverId);
}
