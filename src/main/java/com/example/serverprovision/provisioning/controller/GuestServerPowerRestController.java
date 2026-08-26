package com.example.serverprovision.provisioning.controller;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.service.GuestServerQueryService;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishTarget;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import com.example.serverprovision.execution.exception.DisruptiveActionRejectedException;
import com.example.serverprovision.provisioning.dto.request.PowerResetRequest;

/**
 * 게스트 전원 제어 XHR (E1.5) — 화면 경로는 <b>단발</b>이다(발행 + Task 판독 + 직후 상태 1 회, 60초 폴링 없음).
 * 실패도 결과({@code PowerControlResult})라 2xx 로 내려간다 — 4xx 는 게스트 없음(404) · 요청 형식 위반(400)뿐.
 * 켜짐 검증({@code powerOnAndVerify})은 집행 소비처(E2-3)가 Java API 로 부른다 — HTTP 로 노출하지 않는다.
 */
@RestController
@RequestMapping("/provisioning/server/{id}/power")
@RequiredArgsConstructor
public class GuestServerPowerRestController {

    private final GuestServerQueryService guestServerQueryService;
    private final RedfishPowerService redfishPowerService;

    @GetMapping
    public ResponseEntity<PowerControlResult> state(@PathVariable UUID id) {
        return ResponseEntity.ok(redfishPowerService.powerState(targetOf(id)));
    }

    @PostMapping("/reset")
    public ResponseEntity<PowerControlResult> reset(@PathVariable UUID id, @Valid @RequestBody PowerResetRequest request) {
        GuestServerDetailResponse server = guestServerQueryService.findDetail(id);
        // 회수됨(U6) · 펌웨어를 굽는 중(R13 후속)의 전원 조작 거절 — UI 가 버튼을 막으므로 direct POST 안전망.
        // 사유는 GuestServer.powerControlBlockReason 하나에서 온다(뷰의 tooltip 과 같은 SSOT).
        if (server.powerBlockReason() != null) {
            throw new DisruptiveActionRejectedException(id, server.powerBlockReason());
        }
        return ResponseEntity.ok(redfishPowerService.reset(targetOf(server), request.resetType()));
    }

    /** 도메인 VO(IpAddressVO) → 인프라 경계 값 — 조회는 404 안전망(GuestServerNotFoundException)을 그대로 탄다. */
    private RedfishTarget targetOf(UUID id) {
        return targetOf(guestServerQueryService.findDetail(id));
    }

    private RedfishTarget targetOf(GuestServerDetailResponse server) {
        GuestServerDetailResponse.Inventory inventory = server.inventory();
        if (inventory == null) {
            return new RedfishTarget(null, null);
        }
        return new RedfishTarget(
                inventory.bmcIp() == null ? null : inventory.bmcIp().value(),
                inventory.boardSerial());
    }
}
