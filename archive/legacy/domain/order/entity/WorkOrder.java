package com.example.serverprovision.domain.order.entity;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.order.model.enums.WorkOrderStatus;
import com.example.serverprovision.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * 작업 지시서를 표현하는 JPA 엔티티이다.
 *
 * <p>역할: "어떤 노드에 어떤 세팅이 언제 할당됐는가"를 추적하는 엔티티로,
 * {@link ServerNode}와 {@link ServerSetting} 사이의 할당 이력을 영속화한다.</p>
 *
 * <p>유스케이스: 관리자가 작업 지시서 생성 폼에서 노드와 세팅을 선택하면
 * {@link com.example.serverprovision.domain.order.service.WorkOrderService#create}가
 * 이 엔티티를 생성하고, 내부적으로 {@code ServerNodeService#assignSetting}을 호출하여
 * 실제 세팅 할당을 수행한다. 취소 시 노드의 세팅 참조가 해제된다.</p>
 */
@Entity
@Table(name = "work_order")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"node", "setting"})
public class WorkOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 작업 대상 서버 노드이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_node_id", nullable = false)
    private ServerNode node;

    /** 적용할 세팅 주문서이다. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "server_setting_id", nullable = false)
    private ServerSetting setting;

    /** 작업 지시서의 현재 상태이다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WorkOrderStatus status;

    /** 관리자가 입력한 메모이다. */
    @Column(name = "memo")
    private String memo;

    /**
     * 작업 지시서를 생성하는 팩토리 메서드이다.
     *
     * @param node    작업 대상 서버 노드
     * @param setting 적용할 세팅 주문서
     * @param memo    관리자 메모 (nullable)
     * @return 초기 상태({@code CREATED})의 {@code WorkOrder}
     */
    public static WorkOrder create(ServerNode node, ServerSetting setting, String memo) {
        return WorkOrder.builder()
                .node(node)
                .setting(setting)
                .status(WorkOrderStatus.CREATED)
                .memo(memo)
                .build();
    }

    /**
     * 작업 지시서를 취소한다.
     *
     * @throws IllegalStateException 이미 완료되었거나 취소된 상태인 경우
     */
    public void cancel() {
        if (this.status == WorkOrderStatus.COMPLETED || this.status == WorkOrderStatus.CANCELLED) {
            throw new IllegalStateException(
                    this.status.getDescription() + " 상태의 작업 지시서는 취소할 수 없습니다.");
        }
        this.status = WorkOrderStatus.CANCELLED;
    }

    /**
     * 작업 진행 중 상태로 전이한다.
     */
    public void start() {
        this.status = WorkOrderStatus.IN_PROGRESS;
    }

    /**
     * 작업 완료 상태로 전이한다.
     */
    public void complete() {
        this.status = WorkOrderStatus.COMPLETED;
    }

    /**
     * 작업 실패 상태로 전이한다.
     */
    public void fail() {
        this.status = WorkOrderStatus.FAILED;
    }
}
