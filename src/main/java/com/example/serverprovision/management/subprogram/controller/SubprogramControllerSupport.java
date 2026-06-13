package com.example.serverprovision.management.subprogram.controller;

import org.springframework.validation.BindingResult;

/**
 * Subprogram 컨트롤러 분할 (R6-1) 후 여러 컨트롤러가 공유하는 정적 헬퍼.
 *
 * <p>이전엔 {@code SubprogramController} 안 private 메서드로 같은 로직을 보관했는데, 컨트롤러를
 * 4 개 (CRUD+lifecycle / Upload / Nudge / Browse) 로 분리하면서 redirect URL 조립 / 첫 필드 에러
 * 추출 / null 보정이 여러 컨트롤러에 동시에 필요해졌다. CLAUDE.md 의 "중복 로직은 즉시 공통 유틸로
 * 추출" 원칙에 따라 정적 헬퍼 클래스로 승격.</p>
 *
 * <p>{@code redirectToListWithSelect} 는 R6 Miller 2축 (selectKind/selectKey) 보존 변형이라 OS 의
 * 동명 헬퍼와 시그니처가 다르다. 도메인을 가로지르므로 OS 와 공통화하지 않는다 (over-abstraction
 * 경계, CLAUDE.md §over-abstraction).</p>
 */
public final class SubprogramControllerSupport {

	private SubprogramControllerSupport() {
	}

	/**
	 * Subprogram 목록 페이지로 redirect 하되 Miller 의 selectId 를 보존한다.
	 * 단일 자원의 상태 전이 (update / toggle / restore / deprecate / undeprecate 등) 후 호출자가 사용.
	 */
	public static String redirectToListWithSelect(Long id) {
		return "redirect:/management/subprogram?selectId=" + id;
	}

	/**
	 * BindingResult 의 첫 필드 검증 에러를 "field: message" 형태 문자열로 추출 → {@code ApiErrorResponse} 본문.
	 */
	public static String firstFieldError(BindingResult bindingResult) {
		return bindingResult.getFieldErrors().stream()
				.findFirst()
				.map(err -> err.getField() + ": " + err.getDefaultMessage())
				.orElse("입력값이 올바르지 않습니다.");
	}

	/**
	 * Thymeleaf 폼 binding 안전화 — null 문자열을 빈 문자열로 변환.
	 */
	public static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
