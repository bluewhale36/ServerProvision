package com.example.serverprovision.global.exception;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * 도메인 예외를 HTTP 상태 + 공통 에러 뷰({@code templates/error.html}) 로 변환.
 * <ul>
 *   <li>{@link NotFoundException} → 404</li>
 *   <li>{@link ConflictException} → 409</li>
 *   <li>기타 {@link DomainException} → 500</li>
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

    @ExceptionHandler(DomainException.class)
    public String handleDomain(DomainException ex, Model model, HttpServletResponse response) {
        // NotFound / Conflict 하위를 제외한 도메인 예외는 잠재적 버그로 간주하고 로그 남김
        log.warn("Unhandled DomainException: {}", ex.getMessage(), ex);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        populate(model, 500, "Internal Error", ex.getMessage());
        return "error";
    }

    private static void populate(Model model, int status, String statusLabel, String message) {
        model.addAttribute("status", status);
        model.addAttribute("statusLabel", statusLabel);
        model.addAttribute("message", message);
    }
}
