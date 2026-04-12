package com.example.serverprovision.domain.node.repository;

import com.example.serverprovision.domain.node.entity.ServerNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code ServerNode} 엔티티의 JPA Repository이다.
 *
 * <p>역할: {@code ServerNode}의 기본 CRUD와 MAC 주소 기반 가용 노드 조회를 제공한다.</p>
 *
 * <p>유스케이스: {@code ServerNodeService#getOrRegisterNode}와 {@code ServerNodeService#getNodeByMac}이
 * {@code findAvailableNodeByMacAddress}를 사용한다. {@code COMPLETED}, {@code FAILED} 상태를
 * 제외함으로써 프로비저닝이 완료된 서버가 PXE 부팅 시 다시 신규 등록되지 않도록 보호한다.</p>
 *
 * <p>확장 가이드: 관리자 검색 기능이 필요하면 JPQL 또는 Specification 기반 메소드를 추가한다.
 * 필터 조건이 변경될 경우 {@code findAvailableNodeByMacAddress}의 {@code NOT IN} 절을 수정하고
 * {@code ProvisioningStatus} 열거형과 일치하는지 확인한다.</p>
 */
@Repository
public interface ServerNodeRepository extends JpaRepository<ServerNode, Long> {

    /**
     * MAC 주소로 사용 가능한 서버 노드를 조회한다.
     * {@code COMPLETED}, {@code FAILED} 상태의 노드는 결과에서 제외된다.
     *
     * @param macAddress 조회할 MAC 주소
     * @return 가용 상태의 {@code ServerNode}, 없으면 {@code Optional.empty()}
     */
    @Query("SELECT sn FROM ServerNode sn WHERE sn.macAddress = :macAddress AND sn.status NOT IN ('COMPLETED', 'FAILED')")
    Optional<ServerNode> findAvailableNodeByMacAddress(String macAddress);

    /**
     * 모든 서버 노드를 연관된 세팅 주문서 및 보드 모델과 함께 조회한다.
     * fetch join으로 N+1 쿼리를 방지하고, open-in-view에 의존하지 않는다.
     *
     * @return 세팅과 보드 모델이 즉시 로딩된 전체 {@code ServerNode} 목록
     */
    @Query("SELECT sn FROM ServerNode sn LEFT JOIN FETCH sn.serverSetting LEFT JOIN FETCH sn.boardModel")
    List<ServerNode> findAllWithSettingAndBoardModel();
}
