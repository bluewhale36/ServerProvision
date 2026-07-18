package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.request.StepCloseRequest;
import com.example.serverprovision.execution.dto.request.StepOpenRequest;
import com.example.serverprovision.execution.dto.response.AgentCheckinResponse;
import com.example.serverprovision.execution.dto.response.StepOpenResponse;
import com.example.serverprovision.execution.engine.AgentReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 에이전트 채널(진단 리눅스 {@code agent.sh})의 JSON API(E1-0b). 부팅 채널(text/plain, 예외→스크립트)과
 * 의도적으로 분리된 컨트롤러 — 오류 형식 이원화(DEC-5)가 클래스 경계로 지켜진다. 예외 매핑은 기존
 * {@code ApiExceptionHandler}(JSON) 그대로(분기 추가 0).
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

    static final String TOKEN_HEADER = "X-Guest-Token";

    private final AgentReportService agentReportService;

    @PostMapping("/checkin")
    public AgentCheckinResponse checkin(@RequestHeader(TOKEN_HEADER) String token) {
        return agentReportService.checkin(token);
    }

    @PostMapping("/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public StepOpenResponse openStep(
            @RequestHeader(TOKEN_HEADER) String token,
            @Valid @RequestBody StepOpenRequest request) {
        return agentReportService.openStep(token, request.stepCode());
    }

    @PostMapping("/steps/{stepId}/close")
    public void closeStep(
            @RequestHeader(TOKEN_HEADER) String token,
            @PathVariable("stepId") UUID stepId,
            @Valid @RequestBody StepCloseRequest request) {
        agentReportService.closeStep(token, stepId, request.status(), request.statusMeta());
    }
}
