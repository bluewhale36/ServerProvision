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

@Slf4j
@Service
@RequiredArgsConstructor
public class ServerNodeService {

    private final ServerNodeRepository serverNodeRepository;
    private final BoardModelRepository boardModelRepository;

    /**
     * MAC 주소로 서버를 찾고, 없으면 IDLE 상태의 신규 서버로 DB에 자동 등록합니다.
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

    public List<ServerNode> getAllNodes() {
        return serverNodeRepository.findAll();
    }

    public ServerNode getNodeByMac(String mac) {
        return serverNodeRepository.findAvailableNodeByMacAddress(mac).orElseThrow(() -> new RuntimeException("서버 노드를 찾을 수 없습니다. MAC: " + mac));
    }
}
