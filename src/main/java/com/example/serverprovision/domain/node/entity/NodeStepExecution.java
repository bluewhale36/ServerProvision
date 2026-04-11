package com.example.serverprovision.domain.node.entity;

import com.example.serverprovision.application.setting.model.enums.SettingProcessStep;
import com.example.serverprovision.domain.node.model.enums.StepExecutionStatus;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private ServerNode node;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false)
    private SettingProcessStep stepType;

    // SettingProcessStep.getOrder() 값을 저장 — DB ORDER BY에 사용 (VARCHAR 알파벳순 방지)
    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StepExecutionStatus status;

    public static NodeStepExecution of(ServerNode node, SettingProcessStep step) {
        return NodeStepExecution.builder()
                .node(node)
                .stepType(step)
                .stepOrder(step.getOrder())
                .status(StepExecutionStatus.PENDING)
                .build();
    }

    public void markInProgress() {
        this.status = StepExecutionStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        this.status = StepExecutionStatus.COMPLETED;
    }

    public void markFailed() {
        this.status = StepExecutionStatus.FAILED;
    }
}
