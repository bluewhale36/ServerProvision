package com.example.serverprovision.global.exception;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JSON API 엔드포인트의 전역 에러 응답 포맷이다.
 *
 * <p>역할: {@code GlobalExceptionHandler}가 예외를 캐치한 후 HTTP 응답 본문으로
 * 직렬화하는 공통 DTO이다. {@code code}로 에러 종류를 기계적으로 분류하고
 * {@code message}로 사람이 읽을 수 있는 설명을 제공한다. Bean Validation 또는
 * {@code FieldValidationException} 발생 시에만 {@code fieldErrors}가 채워진다.</p>
 *
 * <p>유스케이스: 세팅 주문서 생성 폼({@code templates/setting/new.html})의 JS가
 * {@code submitSettingTemplate()} 내에서 {@code err.message} 필드를 읽어 알림을 표시하고,
 * {@code form-error.js}가 {@code fieldErrors[].field} 경로를 파싱하여
 * {@code data-error-field} DOM 마커와 매칭해 인라인 에러를 렌더링한다.
 * 따라서 {@code message}는 반드시 사람이 읽을 수 있는 문장으로 채워야 한다.</p>
 *
 * <p>확장 가이드: 새로운 에러 코드가 필요하면 {@code GlobalExceptionHandler}의 해당
 * 핸들러 메소드에서 {@code ErrorResponse.of(code, message)} 호출 시 코드 문자열을
 * 추가한다. 필드별 에러가 필요한 새 예외 유형을 추가하려면 {@code ofValidation}을
 * 사용하고, 프론트 {@code form-error.js}의 DOM 매칭 로직도 함께 검증한다.</p>
 *
 * @param code        내부 분류 코드 ({@code VALIDATION_FAILED}, {@code INVALID_ARGUMENT},
 *                    {@code MALFORMED_JSON}, {@code UNSUPPORTED_OPERATION}, {@code INTERNAL_ERROR})
 * @param message     사람이 읽을 수 있는 에러 설명
 * @param fieldErrors Bean Validation 또는 {@code FieldValidationException} 발생 시 채워지는 필드 단위 오류 목록, 그 외 {@code null}
 * @param timestamp   에러 발생 시각
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldError> fieldErrors,
        LocalDateTime timestamp
) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null, LocalDateTime.now());
    }

    public static ErrorResponse ofValidation(String message, List<FieldError> fieldErrors) {
        return new ErrorResponse("VALIDATION_FAILED", message, fieldErrors, LocalDateTime.now());
    }
}
