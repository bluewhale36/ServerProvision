package com.example.serverprovision.domain.order.repository;

import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.order.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link WorkOrder} 엔티티의 CRUD를 담당하는 Spring Data JPA Repository이다.
 */
@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    /**
     * 전체 작업 지시서를 생성일시 내림차순으로 조회한다.
     * N+1 문제를 방지하기 위해 node와 setting을 함께 페치한다.
     */
    @Query("SELECT wo FROM WorkOrder wo " +
           "JOIN FETCH wo.node " +
           "JOIN FETCH wo.setting " +
           "ORDER BY wo.createdAt DESC")
    List<WorkOrder> findAllWithNodeAndSetting();

    /**
     * 특정 노드의 작업 지시서 목록을 조회한다.
     */
    List<WorkOrder> findByNode(ServerNode node);
}
