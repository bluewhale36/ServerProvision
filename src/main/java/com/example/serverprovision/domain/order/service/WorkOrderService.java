package com.example.serverprovision.domain.order.service;

import com.example.serverprovision.application.setting.domain.entity.ServerSetting;
import com.example.serverprovision.application.setting.repository.SettingRepository;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.repository.ServerNodeRepository;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.order.entity.WorkOrder;
import com.example.serverprovision.domain.order.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 작업 지시서({@link WorkOrder})의 생성·조회·취소 유스케이스를 담당하는 서비스이다.
 *
 * <p>역할: 작업 지시서 생성 시 {@link ServerNodeService#assignSetting}을 호출하여
 * 세팅 할당과 NodeStepExecution 초기화를 함께 수행한다. 취소 시 노드의 세팅 참조를 해제한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkOrderService {

    private final WorkOrderRepository workOrderRepository;
    private final ServerNodeRepository serverNodeRepository;
    private final SettingRepository settingRepository;
    private final ServerNodeService serverNodeService;

    /**
     * 작업 지시서를 생성하고 노드에 세팅을 할당한다.
     *
     * @param nodeId    작업 대상 서버 노드 ID
     * @param settingId 적용할 세팅 주문서 ID
     * @param memo      관리자 메모 (nullable)
     * @return 생성된 작업 지시서
     */
    @Transactional
    public WorkOrder create(Long nodeId, Long settingId, String memo) {
        ServerNode node = serverNodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "서버 노드를 찾을 수 없습니다. ID: " + nodeId));

        ServerSetting setting = settingRepository.findById(settingId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "세팅 주문서를 찾을 수 없습니다. ID: " + settingId));

        // 기존 assignSetting 로직 재사용 (NodeStepExecution 초기화 포함)
        serverNodeService.assignSetting(nodeId, settingId);

        WorkOrder workOrder = WorkOrder.create(node, setting, memo);
        WorkOrder saved = workOrderRepository.save(workOrder);

        log.info("[WorkOrderService] 작업 지시서 생성 완료. orderId={}, nodeId={}, settingId={}",
                saved.getId(), nodeId, settingId);
        return saved;
    }

    /**
     * 작업 지시서를 취소하고 노드의 세팅 할당을 해제한다.
     *
     * @param orderId 취소할 작업 지시서 ID
     */
    @Transactional
    public void cancel(Long orderId) {
        WorkOrder workOrder = workOrderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "작업 지시서를 찾을 수 없습니다. ID: " + orderId));

        workOrder.cancel();

        // 노드의 세팅 할당 해제
        ServerNode node = workOrder.getNode();
        node.unassignSetting();

        log.info("[WorkOrderService] 작업 지시서 취소 완료. orderId={}, nodeId={}",
                orderId, node.getId());
    }

    /**
     * 전체 작업 지시서 목록을 생성일시 내림차순으로 반환한다.
     */
    @Transactional(readOnly = true)
    public List<WorkOrder> findAll() {
        return workOrderRepository.findAllWithNodeAndSetting();
    }

    /**
     * ID로 작업 지시서를 조회한다.
     *
     * @param id 조회할 작업 지시서 ID
     * @return 조회된 작업 지시서
     */
    @Transactional(readOnly = true)
    public WorkOrder findById(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "작업 지시서를 찾을 수 없습니다. ID: " + id));
    }
}
