package com.example.serverprovision.management.board.controller;

/**
 * Board 모델 컨트롤러 분할 후 공통으로 쓰이는 view / 응답 헬퍼.
 *
 * <p>R3-2 — 단일 fat {@code BoardModelController} 를 책임군 3 컨트롤러
 * ({@link BoardModelMetadataController} / {@link BoardModelLifecycleController} /
 * {@link BoardModelNudgeController}) 로 분할하면서, redirect URL 조립 / null 보정 /
 * 검증 오류 변환이 여러 컨트롤러에 동시에 필요해졌다. CLAUDE.md 의 "중복 로직은 즉시 공통 유틸로 추출"
 * 원칙에 따라 정적 헬퍼 클래스로 승격해 복붙 진원지를 사전 차단한다.</p>
 *
 * <p>MA7 — {@code toValidationError} 는 RAID 카드가 두 번째 사용처가 되면서
 * {@code management/common/web/ControllerValidationSupport} 로 승격 이동했다.</p>
 */
public final class BoardControllerSupport {

	private BoardControllerSupport() {
	}

	/**
	 * Board 모델 목록 페이지로 redirect 하되 Miller 의 selectId 를 보존한다.
	 * 단일 자원의 상태 전이 (toggle / restore / deprecate / undeprecate) / update 후 호출자가 사용.
	 */
	public static String redirectToListWithSelect(Long selectId) {
		return "redirect:/management/board?selectId=" + selectId;
	}

	/**
	 * Thymeleaf 폼 binding 안전화 — null 문자열을 빈 문자열로 변환.
	 */
	public static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
