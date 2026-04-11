package com.example.serverprovision.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * {@code application.setting} 패키지 내 JSON API 엔드포인트용 전역 예외 핸들러.
 *
 * <p>적용 범위를 {@code com.example.serverprovision.application.setting} 패키지로
 * 제한해, 관리자 MVC 폼 컨트롤러(@ModelAttribute + BindingResult, {@code application.admin}
 * 패키지) 의 HTML 응답 흐름을 JSON 으로 덮어쓰지 않는다. setting 패키지 하위에 신규
 * JSON 엔드포인트가 추가되면 자동으로 이 핸들러의 적용 범위에 포함된다.
 *
 * <p>응답 스키마는 {@link ErrorResponse} 참고. 프론트의 {@code form-error.js} 가
 * {@code fieldErrors[].field} 경로를 파싱해 {@code data-error-field} DOM 마커와
 * 매칭하므로 각 핸들러는 사용자-친화적 {@code message} 와 경로 형식이 일관된
 * {@code fieldErrors} 를 채워야 한다.
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.example.serverprovision.application.setting")
public class GlobalExceptionHandler {

    /**
     * {@code @Valid} 요청 바디 검증 실패 (e.g. name 빈 값, processList 빈 배열).
     * 첫 필드 에러를 대표 메시지로 노출하고, 전체 목록은 {@code fieldErrors} 에 담는다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldError(
                        fe.getField(),
                        fe.getDefaultMessage() == null ? "" : fe.getDefaultMessage()))
                .toList();
        String summary = fieldErrors.isEmpty()
                ? "요청 값 검증에 실패했습니다."
                : fieldErrors.get(0).message();
        log.warn("[GlobalExceptionHandler] Validation 실패. fieldErrors={}", fieldErrors);
        return ResponseEntity.badRequest().body(ErrorResponse.ofValidation(summary, fieldErrors));
    }

    /**
     * 도메인/서비스 계층에서 특정 필드에 귀속되는 검증 실패.
     * Bean Validation 과 동일한 {@link ErrorResponse.FieldError} 포맷으로 포장해
     * 프론트가 단일 경로 → DOM 매칭 로직 하나로 두 경우를 모두 처리할 수 있게 한다.
     */
    @ExceptionHandler(FieldValidationException.class)
    public ResponseEntity<ErrorResponse> handleFieldValidation(FieldValidationException ex) {
        ErrorResponse.FieldError fieldError = new ErrorResponse.FieldError(ex.getField(), ex.getMessage());
        log.warn("[GlobalExceptionHandler] FieldValidationException. field={}, message={}",
                ex.getField(), ex.getMessage());
        return ResponseEntity.badRequest().body(
                ErrorResponse.ofValidation(ex.getMessage(), List.of(fieldError)));
    }

    /**
     * 잘못된 JSON 본문 또는 Jackson 다형성 {@code type} 판별자 미스매치.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedJson(HttpMessageNotReadableException ex) {
        log.warn("[GlobalExceptionHandler] JSON 파싱 실패. message={}", ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of(
                "MALFORMED_JSON",
                "요청 본문의 JSON 형식이 올바르지 않거나 알 수 없는 프로세스 타입입니다."));
    }

    /**
     * 도메인/서비스 계층에서 던지는 인자 오류. resolver 의 엔티티 조회 실패,
     * 패키지 그룹 환경 불일치, 마운트포인트 누락, OSTemplate 호환성 실패 등이 여기로 떨어진다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[GlobalExceptionHandler] IllegalArgumentException. message={}", ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of("INVALID_ARGUMENT", ex.getMessage()));
    }

    /**
     * 미지원 OS 타입 선택 등 런타임 단계에서 지원되지 않는 연산.
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupported(UnsupportedOperationException ex) {
        log.warn("[GlobalExceptionHandler] UnsupportedOperationException. message={}", ex.getMessage());
        return ResponseEntity.badRequest().body(ErrorResponse.of("UNSUPPORTED_OPERATION", ex.getMessage()));
    }

    /**
     * 예측하지 못한 오류. 원문은 로그에만 남기고 클라이언트에게는 일반화된 메시지를 반환해
     * 내부 정보 노출을 최소화한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("[GlobalExceptionHandler] 예상치 못한 오류.", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.of("INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."));
    }
}
