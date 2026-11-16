package com.example.serverprovision.management.common.nudge;

/**
 * MK2 — nudge_session 이 포함된 자원 도메인 식별자.
 *
 * <p>{@code com.example.serverprovision.global.marker.ResourceType} (마커 발급 대상) 와 의미가 겹치지
 * 않도록 별도 enum 으로 둔다. 본 enum 은 nudge 흐름 한정으로 "어느 도메인 컨트롤러가 발급한
 * nudgeId 인가" 를 식별한다 — confirm 엔드포인트가 nudgeId 만으로 자기 자신의 도메인을 검증할
 * 수 있게 한다.</p>
 *
 * <p>메타 단독 자원(메인보드 · OS 버전 · RAID 카드)은 중복 확인 창을 쓰지 않는다 — 키가 곧 정체라 같은 키가 어느 상태로든
 * 있으면 등록을 거절하고 다음 행동을 안내한다(메인보드 HF26, OS 버전 · RAID 카드 HF30-1). 그래서 남은 상수는 파일 자원뿐이다.</p>
 */
public enum NudgeResourceType {
	BIOS,
	BMC,
	SUBPROGRAM,
	OS_ISO
}
