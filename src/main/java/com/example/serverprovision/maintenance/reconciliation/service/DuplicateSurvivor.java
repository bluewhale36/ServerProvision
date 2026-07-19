package com.example.serverprovision.maintenance.reconciliation.service;

/**
 * HF4-5 — 자원 중복 존재(RESOURCE_DUPLICATED) 해소의 택일 파라미터. "남길 쪽"을 가리킨다.
 * 원시 문자열 대신 enum 타입화 (Primitive Obsession 금지) — 컨트롤러 @RequestParam 바인딩에서
 * Spring 변환 실패가 곧 400 이라 별도 검증 분기가 필요 없다.
 */
public enum DuplicateSurvivor {

	/**
	 * 원본(DB 기록 경로) 유지 — 복제본 파일을 삭제한다. 모달의 기본 선택 (사용자 결정 ③).
	 */
	ORIGINAL,

	/**
	 * 복제본 유지 — DB 경로를 복제본 경로로 갱신(PATH_DRIFT 해결 로직 재사용)하고 원본 파일을 삭제한다.
	 */
	DUPLICATE;

	/**
	 * 복제본을 정본으로 승격하는 갈래인가 — 승격 가드(서명·지문·포함 관계)와 DB 경로 갱신의 발동 조건.
	 */
	public boolean promotesDuplicate() {
		return this == DUPLICATE;
	}
}
