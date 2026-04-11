package com.example.serverprovision.domain.node.repository;

import com.example.serverprovision.domain.node.entity.NodeStepExecution;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.model.enums.StepExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NodeStepExecutionRepository extends JpaRepository<NodeStepExecution, Long> {

    // PENDING 단계 중 step_order 가장 낮은 것 조회 — 다음 실행할 단계 결정에 사용
    Optional<NodeStepExecution> findFirstByNodeAndStatusOrderByStepOrderAsc(
            ServerNode node, StepExecutionStatus status);

    // 주문서 재할당 시 기존 이력 전체 초기화
    void deleteAllByNode(ServerNode node);
}
