package com.example.serverprovision.global.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * REST/XHR 엔드포인트 실패 응답 포맷.
 * <p>S4 (폼 유효성 검증 일관화) — {@code fieldErrors} 를 추가해 Bean Validation 위반 (Layer A) 또는
 * 필드 직결 도메인 예외 ({@link FieldBoundConflictException} / {@link FieldBoundBadRequestException})
 * 가 클라이언트 폼에 자동 매핑될 수 있도록 한다. 프론트엔드는 {@code FormError.renderResponse(body)}
 * 단일 경로로 처리한다.</p>
 * <p>{@code @JsonInclude(JsonInclude.Include.NON_NULL)} — fieldErrors 가 null 인 경우 (단순 메시지) 응답
 * 직렬화에서 생략되어 기존 클라이언트 호환성을 유지한다.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
		String message,
		List<FieldError> fieldErrors
) {

	/**
	 * 단일 메시지만 보내는 기존 호환 생성자. fieldErrors 는 null 로 직렬화에서 생략된다.
	 */
	public ApiErrorResponse(String message) {
		this(message, null);
	}

	/**
	 * 단일 필드 직결 도메인 예외용 생성자. message 와 함께 fieldErrors 1건을 동봉한다.
	 */
	public static ApiErrorResponse ofFieldBound(String message, String fieldName) {
		return new ApiErrorResponse(message, List.of(new FieldError(fieldName, message)));
	}

	/**
	 * Layer A (Bean Validation) 위반 시 BindingResult 의 FieldError 목록을 매핑한 응답.
	 */
	public static ApiErrorResponse ofValidation(String message, List<FieldError> fieldErrors) {
		return new ApiErrorResponse(message, fieldErrors);
	}

	/**
	 * 폼 필드 단위 에러 정보. {@code field} 는 DTO 필드명 (HTML 의 {@code data-error-field} 속성 매핑값).
	 */
	public record FieldError(
			String field,
			String message
	) {

	}
}
