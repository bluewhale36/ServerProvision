package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.request.RhelInstallCompletionRequest;
import com.example.serverprovision.execution.dto.response.OsInstallCompletionResponse;
import com.example.serverprovision.execution.engine.rhel.RhelInstallCompletionService;
import com.example.serverprovision.global.security.springsecurity.guest.GuestPrincipal;
import com.example.serverprovision.global.security.springsecurity.guest.web.CurrentGuest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설치된 RHEL 계열 OS 가 첫 부팅에서 부르는 완료 보고 창구(E4-1-b-5). 인증과 오류 형식은 Windows 창구와 같다.
 * <ul>
 *   <li>{@code POST /api/pxe/v1/agent/rhel/complete} — 200 closed / 200 no-op(중복) / 400 검증 / 401 토큰(게스트 체인) / 409 상태</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pxe/v1/agent/rhel")
@RequiredArgsConstructor
public class RhelInstallReportRestController {

    private final RhelInstallCompletionService completionService;

    @PostMapping("/complete")
    public OsInstallCompletionResponse complete(
            @CurrentGuest GuestPrincipal guest,
            @Valid @RequestBody RhelInstallCompletionRequest request) {
        return completionService.complete(guest, request);
    }
}
