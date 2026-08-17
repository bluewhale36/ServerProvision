package com.example.serverprovision.management.raidcard.controller;

/**
 * RaidCard 컨트롤러 3분할({@link RaidCardMetadataController} / {@link RaidCardLifecycleController} /
 * {@link RaidCardNudgeController}) 공용 view 헬퍼 (BoardControllerSupport 선례).
 * <p>검증 오류 변환은 {@code management/common/web/ControllerValidationSupport} 공용을 쓴다.</p>
 */
public final class RaidCardControllerSupport {

	private RaidCardControllerSupport() {
	}

	/**
	 * 목록 페이지로 redirect 하되 Miller 의 selectId 를 보존한다.
	 * 상태 전이 (toggle / restore / deprecate / undeprecate) / update 후 호출자가 사용.
	 */
	public static String redirectToListWithSelect(Long selectId) {
		return "redirect:/management/raidcard?selectId=" + selectId;
	}

	/**
	 * Thymeleaf 폼 binding 안전화 — null 문자열을 빈 문자열로 변환.
	 */
	public static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
