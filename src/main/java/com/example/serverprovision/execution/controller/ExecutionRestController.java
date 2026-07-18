package com.example.serverprovision.execution.controller;

import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.engine.BootService;
import com.example.serverprovision.execution.engine.IpxeScripts;
import com.example.serverprovision.global.exception.ExceptionLogPolicy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

/**
 * PXE 부팅 채널(iPXE)의 진입점. E1-0b 부터 응답이 빈 200 이 아니라 <b>text/plain iPXE 스크립트</b>다 —
 * 판정(dispatch 매트릭스)은 {@link BootService} 가, 스크립트 조립은 {@code IpxeScripts} 가 담당한다.
 *
 * <p><b>예외 → 안전 스크립트 변환(DEC-5)</b>: JSON/HTML 오류가 iPXE 로 새면 파싱 불능 → 부팅 실패
 * 루프가 되므로, 이 채널의 모든 예외는 <b>200 + 재시도 대기 스크립트</b>로 변환한다(iPXE chain 은
 * 2xx 본문만 실행 — 5xx 로는 재시도 루프를 만들 수 없다. plan Q3). 매핑 위치가 별도 advice 가 아니라
 * 컨트롤러 내장 {@code @ExceptionHandler} 인 이유: 기존 두 전역 advice 가 이미
 * {@code @Order(HIGHEST_PRECEDENCE)}(더 높은 값이 존재하지 않는 {@code Integer.MIN_VALUE})라 별도
 * advice 는 동순위 추첨이 되는 반면, 컨트롤러 자신의 핸들러는 Spring 규약상 <b>모든 advice 보다 항상
 * 먼저</b> 평가된다 — "PXE 채널 한정" 을 결정론적으로 보장하는 유일한 자리다. (try/catch 복붙이 아닌
 * Spring primitive 경계 선언 — 에이전트 채널(JSON)은 기존 ApiExceptionHandler 그대로.)</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/pxe/v1")
@RequiredArgsConstructor
public class ExecutionRestController {

    private final BootService bootService;

    @GetMapping(value = "/boot", produces = MediaType.TEXT_PLAIN_VALUE)
    public String initialBoot(@ModelAttribute BootIPXEInfoRequest initialRequest, HttpServletRequest request) {
        log.info("PXE 부팅 요청 : info={}", initialRequest.toString());
        // 재진입(chain) URL 은 게스트가 보낸 쿼리를 그대로 되돌려 준다 — /boot 를 무상태로 유지하는 방법.
        return bootService.boot(initialRequest, request.getQueryString());
    }

    /** PXE 채널 전용 예외 경계 — 전 예외를 200 + 재시도 스크립트로. 원 예외 로그는 그대로(silent 흡수 아님). */
    @ExceptionHandler(Exception.class)
    public String convertToRetryScript(Exception ex, HttpServletRequest request) {
        ResponseStatus rs = AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class);
        HttpStatus mapped = rs != null ? rs.value() : HttpStatus.INTERNAL_SERVER_ERROR;
        ExceptionLogPolicy.record("pxe.boot.converted", ex, mapped, "ipxe");
        return IpxeScripts.retryAfterError(request.getQueryString());
    }
}
