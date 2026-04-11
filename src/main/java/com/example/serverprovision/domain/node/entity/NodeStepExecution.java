package com.example.serverprovision.domain.node.entity;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.domain.node.model.enums.StepExecutionStatus;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * {@code ServerNode}의 각 세팅 단계 실행 이력을 추적하는 엔티티이다.
 *
 * <p>역할: 세팅 주문서({@code ServerSetting})의 각 {@code SettingProcessStep}별로 단계 하나당
 * 레코드 하나가 생성되어 실행 상태({@code status})를 관리한다. {@code (node_id, step_type)} 유니크
 * 제약으로 동일 서버-단계 쌍의 중복 이력을 방지한다. {@code stepOrder}는 {@code step_type}의
 * 알파벳순 정렬 문제를 피하기 위해 {@code SettingProcessStep#getOrder()} 값을 DB에 저장한다.</p>
 *
 * <p>유스케이스: {@code ProvisioningScriptService#generateIPXEScript}가
 * {@code NodeStepExecutionRepository#findFirstByNodeAndStatusOrderByStepOrderAsc}로
 * {@code PENDING} 상태 중 {@code stepOrder}가 가장 낮은 레코드를 조회하여 다음 실행 단계를 결정한다.
 * 해당 단계 스크립트 반환 직전 {@code markInProgress}로 상태를 {@code IN_PROGRESS}로 전이시킨다.
 * 세팅 주문서가 재할당될 경우 {@code NodeStepExecutionRepository#deleteAllByNode}로
 * 해당 노드의 모든 이력이 초기화된다.</p>
 *
 * <p>확장 가이드: 단계 실패 처리 로직을 추가할 때는 {@code markFailed} 후 재시도 정책(수동 개입
 * 또는 자동 재시도)을 결정하고, 재시도 시 이 레코드를 {@code PENDING}으로 되돌리는 별도 메소드를
 * 추가한다. 새 {@code SettingProcessStep} 상수가 추가되면 세팅 주문서 저장 시
 * 해당 단계의 {@code NodeStepExecution}도 함께 생성해야 한다.</p>
 */
@Entity
@Table(
        name = "node_step_execution",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_node_step",
                columnNames = {"node_id", "step_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@ToString(exclude = "node")
public class NodeStepExecution extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이 실행 이력이 속한 서버 노드이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private ServerNode node;

    /** 이 레코드가 추적하는 세팅 단계 유형이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false)
    private SettingProcessStep stepType;

    /**
     * 실행 순서를 나타내는 정수값이다. {@code SettingProcessStep#getOrder()} 값을 저장하며,
     * DB {@code ORDER BY step_order ASC}에 사용한다. {@code VARCHAR} 알파벳순 정렬과의 불일치를
     * 방지하기 위해 별도 컬럼으로 관리한다.
     */
    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    /** 현재 단계의 실행 상태이다. 최초 생성 시 {@code PENDING}이며 상태 전이 메소드로만 변경된다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StepExecutionStatus status;

    /**
     * 서버 노드와 세팅 단계를 받아 {@code PENDING} 상태의 실행 이력 레코드를 생성한다.
     *
     * @param node 이 이력이 속할 서버 노드
     * @param step 추적할 세팅 단계
     * @return {@code PENDING} 상태의 {@code NodeStepExecution}
     */
    public static NodeStepExecution of(ServerNode node, SettingProcessStep step) {
        return NodeStepExecution.builder()
                .node(node)
                .stepType(step)
                .stepOrder(step.getOrder())
                .status(StepExecutionStatus.PENDING)
                .build();
    }

    /** iPXE 스크립트 반환 직전 호출되어 이 단계의 상태를 {@code IN_PROGRESS}로 전이시킨다. */
    public void markInProgress() {
        this.status = StepExecutionStatus.IN_PROGRESS;
    }

    /** 단계 정상 완료 시 호출되어 상태를 {@code COMPLETED}로 전이시킨다. */
    public void markCompleted() {
        this.status = StepExecutionStatus.COMPLETED;
    }

    /** 단계 실패 시 호출되어 상태를 {@code FAILED}로 전이시킨다. 수동 개입이 필요한 상태이다. */
    public void markFailed() {
        this.status = StepExecutionStatus.FAILED;
    }
}
