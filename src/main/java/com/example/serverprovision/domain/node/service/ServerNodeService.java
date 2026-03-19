package com.example.serverprovision.domain.node.service;

import com.example.serverprovision.domain.node.entity.*;
import com.example.serverprovision.domain.node.model.enums.BoardModel;
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

    /**
     * MAC 주소로 서버를 찾고, 없으면 IDLE 상태의 신규 서버로 DB에 자동 등록합니다.
     */
    @Transactional
    public ServerNode getOrRegisterNode(String macAddress, String vendor, String boardModel) {

        Vendor vendorEnum = Vendor.getVendorByString(vendor);
        BoardModel boardModelEnum = BoardModel.getBoardModelByString(boardModel, vendorEnum);

        return serverNodeRepository.findById(macAddress).orElseGet(() -> {
            log.info("새로운 물리 서버 감지 및 DB 등록 완료. MAC: {}", macAddress);
            ServerNode newNode = ServerNode.builder()
                    .macAddress(macAddress)
                    .vendor(vendorEnum)
                    .boardModel(boardModelEnum)
                    .targetJob(JobType.IDLE)
                    .status(ProvisioningStatus.NEW)
                    .build();
            return serverNodeRepository.save(newNode);
        });
    }

    public List<ServerNode> getAllNodes() {
        return serverNodeRepository.findAll();
    }

    public ServerNode getNodeByMac(String mac) {
        return serverNodeRepository.findById(mac).orElseThrow(() -> new RuntimeException("서버 노드를 찾을 수 없습니다. MAC: " + mac));
    }
}
