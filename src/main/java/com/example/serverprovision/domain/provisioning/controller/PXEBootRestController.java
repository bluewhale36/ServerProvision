package com.example.serverprovision.domain.provisioning.controller;

import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import com.example.serverprovision.domain.provisioning.service.ProvisioningScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/pxe/v1/api")
@RequiredArgsConstructor
public class PXEBootRestController {

    private final ServerNodeService serverNodeService;
    private final ProvisioningScriptService provisioningScriptService;

    /**
     * 물리 서버가 켜질 때 iPXE 커널이 이 주소를 호출합니다.
     * 반환 타입은 반드시 text/plain 이어야 iPXE 가 해석할 수 있습니다.
     *
     * <p>처리 순서:</p>
     * <ol>
     *   <li>MAC 주소로 서버 노드 조회 또는 신규 등록</li>
     *   <li>{@link ProvisioningScriptService}에 위임하여 현재 단계에 맞는 iPXE 스크립트 생성</li>
     * </ol>
     */
    @GetMapping(value = "/boot", produces = "text/plain")
    public String getInitialBootScript(
            @RequestParam("mac") String macAddress,
            @RequestParam(value = "vendor", required = false) String vendor,
            @RequestParam(value = "board-model", required = false) String boardModel
    ) {
        log.info("iPXE 부팅 요청 수신. MAC: {}", macAddress);

        // 1. DB 에서 서버 정보를 가져오거나 신규 등록
        ServerNode node = serverNodeService.getOrRegisterNode(macAddress, vendor, boardModel);

        // 2. ProvisioningScriptService 에 위임하여 현재 단계에 맞는 iPXE 스크립트 생성
        return provisioningScriptService.generateIPXEScript(node);
    }
}
