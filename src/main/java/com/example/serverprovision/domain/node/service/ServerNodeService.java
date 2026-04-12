package com.example.serverprovision.domain.node.service;

import com.example.serverprovision.domain.board.entity.BoardModel;
import com.example.serverprovision.domain.board.repository.BoardModelRepository;
import com.example.serverprovision.domain.node.entity.*;
import com.example.serverprovision.domain.node.model.enums.JobType;
import com.example.serverprovision.domain.node.model.enums.ProvisioningStatus;
import com.example.serverprovision.domain.node.model.enums.Vendor;
import com.example.serverprovision.domain.node.repository.ServerNodeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 서버 노드({@code ServerNode}) 조회 및 자동 등록을 담당하는 서비스이다.
 *
 * <p>역할: PXE 부팅 요청을 처리하는 {@code PXEBootRestController}와 관리자 화면을 지원하는
 * {@code AdminController}에 서버 노드 조회 기능을 제공한다. MAC 주소 기반으로 노드를 찾거나,
 * 미등록 서버를 자동으로 신규 등록하는 핵심 로직을 담는다.</p>
 *
 * <p>유스케이스: PXE 부팅 흐름에서 {@code PXEBootRestController}가
 * {@code getOrRegisterNode}를 호출하면, 해당 MAC 주소의 노드가 DB에 존재하면 기존 노드를
 * 반환하고 없으면 {@code ServerNode#create(String, BoardModel)}로 신규 노드를 생성해 저장한다.
 * {@code AdminController#dashboard}는 {@code getAllNodes}로 전체 노드 목록을 가져와 관리자
 * 대시보드에 표시하며, {@code AdminController#settings}는 {@code getNodeByMac}으로 특정 노드의
 * 설정 화면 데이터를 제공한다.</p>
 *
 * <p>확장 가이드: 자동 등록 시 추가 초기화 로직(예: 기본 세팅 주문서 자동 할당)이 필요하면
 * {@code getOrRegisterNode} 내 {@code orElseGet} 람다를 확장한다. 노드 검색 기준을
 * MAC 주소 외로 확장해야 할 경우 {@code ServerNodeRepository}에 쿼리 메소드를 추가하고
 * 이 서비스에 새 조회 메소드를 제공한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServerNodeService {

    private final ServerNodeRepository serverNodeRepository;
    private final BoardModelRepository boardModelRepository;

    /**
     * MAC 주소로 사용 가능한 서버 노드를 조회하거나, 없으면 신규 노드로 자동 등록한다.
     *
     * <p>조회 조건은 {@code ServerNodeRepository#findAvailableNodeByMacAddress}에 정의되며,
     * {@code COMPLETED} 또는 {@code FAILED} 상태 노드는 제외된다. 미등록 MAC이면
     * {@code ServerNode#create(String, BoardModel)}로 {@code IDLE}/{@code NEW} 상태 노드를 생성한다.</p>
     *
     * @param macAddress    PXE 부팅 요청의 MAC 주소
     * @param vendorStr     제조사 문자열 ({@code Vendor} 열거형으로 변환됨)
     * @param boardModelStr 보드 모델명
     * @return 기존 또는 신규 등록된 {@code ServerNode}
     * @throws RuntimeException 보드 모델 조회 실패 시
     */
    @Transactional
    public ServerNode getOrRegisterNode(String macAddress, String vendorStr, String boardModelStr) {

        BoardModel boardModel = boardModelRepository.findByVendorAndModelName(Vendor.valueOf(vendorStr), boardModelStr)
                .orElseThrow(() -> new RuntimeException("해당 보드 모델을 찾을 수 없습니다. Vendor: " + vendorStr + ", Model: " + boardModelStr));

        return serverNodeRepository.findAvailableNodeByMacAddress(macAddress).orElseGet(() -> {
            log.info("신규 물리 서버 감지. MAC: {}", macAddress);
            ServerNode newNode = ServerNode.create(macAddress, boardModel);
            return serverNodeRepository.save(newNode);
        });
    }

    /**
     * 전체 서버 노드 목록을 반환한다.
     *
     * @return 전체 {@code ServerNode} 목록
     */
    public List<ServerNode> getAllNodes() {
        return serverNodeRepository.findAll();
    }

    /**
     * MAC 주소로 사용 가능한 서버 노드를 조회한다.
     *
     * @param mac 조회할 MAC 주소
     * @return 해당 MAC 주소의 {@code ServerNode}
     * @throws RuntimeException 노드가 없거나 {@code COMPLETED}/{@code FAILED} 상태인 경우
     */
    public ServerNode getNodeByMac(String mac) {
        return serverNodeRepository.findAvailableNodeByMacAddress(mac).orElseThrow(() -> new RuntimeException("서버 노드를 찾을 수 없습니다. MAC: " + mac));
    }

    /**
     * ID로 서버 노드를 조회한다.
     *
     * <p>Kickstart 스크립트 서빙 등 ID 기반 직접 조회가 필요한 경우 사용한다.
     * 상태 필터 없이 전체 상태의 노드를 반환하며, 노드가 없으면 예외를 던진다.</p>
     *
     * @param id 조회할 서버 노드 기본키
     * @return 해당 ID의 {@code ServerNode}
     * @throws RuntimeException 노드가 존재하지 않는 경우
     */
    @Transactional
    public ServerNode getNodeById(Long id) {
        return serverNodeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("서버 노드를 찾을 수 없습니다. ID: " + id));
    }

    /**
     * ID로 서버 노드를 Optional로 조회한다.
     *
     * <p>컨트롤러에서 not-found 판단을 직접 수행해야 하는 경우 사용한다.
     * 예외 대신 {@link java.util.Optional}을 반환하므로 광범위한 try-catch 없이
     * 안전하게 null 여부를 처리할 수 있다.</p>
     *
     * @param id 조회할 서버 노드 기본키
     * @return 해당 ID의 {@code ServerNode}를 감싼 Optional, 없으면 empty
     */
    @Transactional
    public java.util.Optional<ServerNode> findNodeById(Long id) {
        return serverNodeRepository.findById(id);
    }
}
