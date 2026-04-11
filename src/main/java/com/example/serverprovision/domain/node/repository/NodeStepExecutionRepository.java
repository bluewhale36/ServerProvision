package com.example.serverprovision.domain.node.repository;

import com.example.serverprovision.domain.node.entity.NodeStepExecution;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.model.enums.StepExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@code NodeStepExecution} 엔티티의 JPA Repository이다.
 *
 * <p>역할: 서버 노드별 단계 실행 이력의 CRUD와 다음 실행 단계 조회, 이력 초기화를 제공한다.</p>
 *
 * <p>유스케이스: {@code ProvisioningScriptService#generateIPXEScript}가
 * {@code findFirstByNodeAndStatusOrderByStepOrderAsc}로 다음 실행할 {@code PENDING} 단계를
 * 결정한다. 세팅 주문서 재할당 시 {@code deleteAllByNode}로 기존 이력을 초기화하여 처음부터
 * 다시 단계가 진행되도록 한다.</p>
 *
 * <p>확장 가이드: 단계별 실행 이력 조회(예: 완료된 단계 목록, 실패한 단계 목록)가 필요하면
 * {@code findAllByNodeAndStatus} 등의 메소드를 추가한다.</p>
 */
@Repository
public interface NodeStepExecutionRepository extends JpaRepository<NodeStepExecution, Long> {

    /**
     * 특정 노드에서 지정 상태인 단계 중 {@code stepOrder}가 가장 낮은 레코드를 조회한다.
     * {@code PENDING} 상태와 함께 사용하여 다음 실행할 단계를 결정한다.
     *
     * @param node   조회 대상 서버 노드
     * @param status 조회할 실행 상태
     * @return {@code stepOrder}가 가장 낮은 해당 상태의 {@code NodeStepExecution}
     */
    Optional<NodeStepExecution> findFirstByNodeAndStatusOrderByStepOrderAsc(
            ServerNode node, StepExecutionStatus status);

    /**
     * 특정 노드의 모든 단계 실행 이력을 삭제한다.
     * 세팅 주문서 재할당 시 기존 이력을 초기화하여 단계를 처음부터 재시작할 때 사용된다.
     *
     * @param node 이력을 삭제할 서버 노드
     */
    void deleteAllByNode(ServerNode node);
}
