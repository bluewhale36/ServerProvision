package com.example.serverprovision.global.exception;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 전역 예외 핸들러.
 * <ul>
 *   <li>{@link NotFoundException} → 404 (HTML 에러 뷰)</li>
 *   <li>{@link ConflictException} → 409 (HTML 에러 뷰)</li>
 *   <li>기타 {@link DomainException} → 500 (HTML 에러 뷰)</li>
 *   <li>{@link MaxUploadSizeExceededException} → 413 (JSON {@link ApiErrorResponse}) —
 *       multipart 파서가 컨트롤러 메서드 진입 이전에 거절하므로 feature 컨트롤러의 try/catch 로 잡히지 않아
 *       여기서 공통 처리. A1 ISO · A3 BIOS · A4 BMC · A5 Driver 모든 업로드 경로에서 재사용.</li>
 * </ul>
 * {@code Exception.class} 는 가로채지 않아 Spring Boot 기본 에러 페이지 흐름을 유지한다.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public String handleNotFound(NotFoundException ex, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.NOT_FOUND.value());
        populate(model, 404, "Not Found", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(ConflictException.class)
    public String handleConflict(ConflictException ex, Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.CONFLICT.value());
        populate(model, 409, "Conflict", ex.getMessage());
        return "error";
    }

    /**
     * (권고3) JPA 낙관적 락 충돌 → 409. 동시 apply / dismiss / 자동 적용이 같은 보고서/drift 행을
     * 동시 갱신할 때 발생. 사용자 메시지는 "다시 시도" 안내.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String handleOptimisticLock(OptimisticLockingFailureException ex,
                                       Model model, HttpServletResponse response) {
        log.warn("OptimisticLockingFailureException : {}", ex.getMessage());
        response.setStatus(HttpStatus.CONFLICT.value());
        populate(model, 409, "Conflict",
                "다른 작업이 같은 항목을 동시에 수정했습니다. 페이지를 새로 고친 뒤 다시 시도해주세요.");
        return "error";
    }

    @ExceptionHandler(DomainException.class)
    public String handleDomain(DomainException ex, Model model, HttpServletResponse response) {
        // NotFound / Conflict 하위를 제외한 도메인 예외는 잠재적 버그로 간주하고 로그 남김
        log.warn("Unhandled DomainException: {}", ex.getMessage(), ex);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        populate(model, 500, "Internal Error", ex.getMessage());
        return "error";
    }

    /**
     * multipart 업로드 크기 초과 — XHR 업로드 클라이언트가 실제 사유를 파싱할 수 있도록 JSON 으로 응답.
     * HTTP 413 Payload Too Large.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseBody
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("MaxUploadSizeExceededException : {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiErrorResponse(
                        "업로드 크기가 서버 설정 한도를 초과했습니다. 관리자에게 문의하세요. (" + ex.getMessage() + ")"));
    }

    private static void populate(Model model, int status, String statusLabel, String message) {
        model.addAttribute("status", status);
        model.addAttribute("statusLabel", statusLabel);
        model.addAttribute("message", message);
    }
}
