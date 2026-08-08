package com.example.serverprovision.global.marker;

/**
 * 이 종류를 <b>어느 입구로</b> 해결하는가.
 *
 * <p>{@link DriftResolutionMode} 가 "얼마나 자동으로 되는가"(사용자에게 보이는 등급)를 말한다면,
 * 이 축은 "어느 폼 · 어느 endpoint 로 처리되는가"(배선)를 말한다. 종전에는 {@code mode} 하나가
 * 둘을 겸했는데, 그래서 <b>사람 확인만 받으면 시스템이 처리해 주는데도 전용 입구를 쓰는 종류</b>가
 * "시스템 해결 없음(NONE)"으로 분류될 수밖에 없었다 — 자원 중복 존재와 내용 변경 감지가 그랬다.
 * 두 종류 모두 모달에서 한 번 더 확인을 받은 뒤 시스템이 파일과 DB 를 바꿔 주므로, 사용자가 보는
 * 등급은 '확인 후 자동 해결 가능' 이 맞다.</p>
 *
 * <p>둘을 갈라 놓으면 등급을 올려도 버튼 배선은 그대로 유지된다 — 표준 [해결] 폼은 여전히
 * {@link #STANDARD} 에만 뜨므로 전용 폼과 버튼이 겹치지 않는다.</p>
 */
public enum DriftResolveEntry {

	/**
	 * 해결 입구 없음. 사람이 앱 밖에서 파일을 되돌려 놓아야 한다(자원 소실).
	 */
	NONE,

	/**
	 * 표준 [해결] 버튼 — 공용 {@code /drifts/{id}/apply} 와 종류별 {@code DriftResolution} 전략 bean.
	 */
	STANDARD,

	/**
	 * 전용 폼 · 전용 endpoint. 사용자 입력을 더 받아야 해서 표준 계약에 담기지 않는다
	 * (자원명 입력 · 남길 쪽 택일).
	 */
	DEDICATED
}
