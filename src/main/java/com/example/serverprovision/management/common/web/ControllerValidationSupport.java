package com.example.serverprovision.management.common.web;

import com.example.serverprovision.global.exception.ApiErrorResponse;
import org.springframework.validation.BindingResult;

import java.util.List;

/**
 * XHR JSON 폼의 {@code BindingResult → ApiErrorResponse} 변환 — 검증 응답의 단일 소스.
 *
 * <p>MA7 — 원래 {@code BoardControllerSupport} 의 정적 메서드였으나 RAID 카드 컨트롤러가 두 번째
 * 사용처가 되면서 공통으로 승격했다(불가침 "동일 로직이 두 곳 이상 복붙되면 즉시 공통 모듈로 추출" —
 * 두 번째 사용처가 생기는 시점이 공통화 시점). 도메인 무관 순수 변환이라 management/common 에 둔다.</p>
 */
public final class ControllerValidationSupport {

	private ControllerValidationSupport() {
	}

	public static ApiErrorResponse toValidationError(BindingResult bindingResult) {
		List<ApiErrorResponse.FieldError> fields = bindingResult.getFieldErrors().stream()
				.map(fe -> new ApiErrorResponse.FieldError(
						fe.getField(),
						fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "유효하지 않은 값"
				))
				.toList();
		return ApiErrorResponse.ofValidation(
				"입력 값이 유효하지 않습니다 (" + fields.size() + "개 필드).", fields);
	}
}
