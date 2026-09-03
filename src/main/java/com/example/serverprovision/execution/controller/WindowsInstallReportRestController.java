package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.request.WindowsInstallCompletionRequest;
import com.example.serverprovision.execution.dto.response.WindowsInstallCompletionResponse;
import com.example.serverprovision.execution.engine.windows.WindowsInstallCompletionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설치된 Windows 의 첫 로그온 스크립트가 부르는 완료 보고 창구(E4-1-a-4 D-4) — 진단 에이전트 채널과 같은 토큰 헤더 ·
 * 같은 JSON 오류 형식({@code ApiExceptionHandler})이며 컨트롤러는 분기하지 않는다.
 * <ul>
 *   <li>{@code POST /api/pxe/v1/agent/windows/complete} — 200 closed / 200 no-op(중복) / 400 검증 / 404 토큰 / 409 상태</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pxe/v1/agent/windows")
@RequiredArgsConstructor
public class WindowsInstallReportRestController {

    private final WindowsInstallCompletionService completionService;

    @PostMapping("/complete")
    public WindowsInstallCompletionResponse complete(
            @RequestHeader(GuestAgentRestController.TOKEN_HEADER) String token,
            @Valid @RequestBody WindowsInstallCompletionRequest request) {
        return completionService.complete(token, request);
    }
}
