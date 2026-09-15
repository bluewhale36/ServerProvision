package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.request.StepCloseRequest;
import com.example.serverprovision.execution.dto.request.StepOpenRequest;
import com.example.serverprovision.execution.dto.response.AgentCheckinResponse;
import com.example.serverprovision.execution.dto.response.StepCloseResponse;
import com.example.serverprovision.execution.dto.response.StepOpenResponse;
import com.example.serverprovision.execution.engine.diagnose.AgentReportService;
import jakarta.validation.Valid;
import com.example.serverprovision.global.security.springsecurity.guest.web.CurrentGuest;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 에이전트 채널(진단 리눅스 {@code agent.sh})의 JSON API(E1-0b). 부팅 채널(text/plain, 예외→스크립트)과
 * 의도적으로 분리된 컨트롤러 — 오류 형식 이원화(DEC-5)가 클래스 경계로 지켜진다. 예외 매핑은 기존
 * {@code ApiExceptionHandler}(JSON) 그대로(분기 추가 0). 게스트 신원은 S19-1 부터 게스트 체인이 세우고 {@code @CurrentGuest} 로 받는다
 * (X-Guest-Token 헤더의 대조 · 401 은 필터 몫).
 * <ul>
 *   <li>{@code POST /api/pxe/v1/agent/checkin} — 기동 사실 신호 (첫 체크인 = 진단 전이)</li>
 *   <li>{@code POST /api/pxe/v1/agent/steps} — step 시작 보고 (RUNNING 열림, 201 + stepId)</li>
 *   <li>{@code POST /api/pxe/v1/agent/steps/{stepId}/close} — 종료 보고 (중복 = no-op 멱등)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pxe/v1/agent")
@RequiredArgsConstructor
public class GuestAgentRestController {

    private final AgentReportService agentReportService;

    @PostMapping("/checkin")
    public AgentCheckinResponse checkin(@CurrentGuest GuestPrincipal guest) {
        return agentReportService.checkin(guest);
    }

    @PostMapping("/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public StepOpenResponse openStep(
            @CurrentGuest GuestPrincipal guest,
            @Valid @RequestBody StepOpenRequest request) {
        return agentReportService.openStep(guest, request.stepCode());
    }

    /** 종료 보고 — 응답에 다음 지시를 싣는다(E1-2). 완주(REBOOT)의 유일한 운반로다(체크인은 게이트가 거절). */
    @PostMapping("/steps/{stepId}/close")
    public StepCloseResponse closeStep(
            @CurrentGuest GuestPrincipal guest,
            @PathVariable("stepId") UUID stepId,
            @Valid @RequestBody StepCloseRequest request) {
        return agentReportService.closeStep(guest, stepId, request.status(), request.statusMeta());
    }
}
