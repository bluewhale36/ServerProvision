package com.example.serverprovision.domain.provisioning.controller;

import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.node.service.ServerNodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/pxe/v1/api")
@RequiredArgsConstructor
public class PXEBootRestController {

    private final ServerNodeService serverNodeService;

    /**
     * 물리 서버가 켜질 때 iPXE 커널이 이 주소를 호출합니다.
     * 반환 타입은 반드시 text/plain 이어야 iPXE 가 해석할 수 있습니다.
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

        // 2. 응답할 iPXE 스크립트 작성 (현재는 무조건 IDLE 상태로 가정하고 로컬 부팅 지시)
        StringBuilder script = new StringBuilder();
        script.append("#!ipxe\n");
        script.append("echo [Provisioning Server] Connected. MAC: ").append(macAddress).append("\n");

        // 향후 이 부분에 OS_INSTALL_ROCKY8 등의 조건문이 추가될 예정입니다.
        script.append("echo Target Job is IDLE. Booting from Local Hard Drive...\n");

        // 0x80은 첫 번째 로컬 하드디스크를 의미합니다.
        script.append("sanboot --no-describe --drive 0x80\n");

        return script.toString();
    }
}
