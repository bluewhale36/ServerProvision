package com.example.serverprovision.management.common.nudge;

/**
 * MK2 — nudge_session 이 포함된 자원 도메인 식별자.
 *
 * <p>{@code com.example.serverprovision.global.marker.ResourceType} (마커 발급 대상) 와 의미가 겹치지
 * 않도록 별도 enum 으로 둔다. 본 enum 은 nudge 흐름 한정으로 "어느 도메인 컨트롤러가 발급한
 * nudgeId 인가" 를 식별한다 — confirm 엔드포인트가 nudgeId 만으로 자기 자신의 도메인을 검증할
 * 수 있게 한다.</p>
 */
public enum NudgeResourceType {
    BIOS,
    BMC,
    SUBPROGRAM,
    OS_ISO
}
