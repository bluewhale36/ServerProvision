package com.example.serverprovision.global.redfish;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 저수준 호출 실패의 분류 — {@code RedfishClient} 내부에서 붙이고 {@code RedfishPowerService} 가 결과 타입으로 흡수한다(E1.5 D3).
 * 사용자 문구도 상수가 보유한다 — 상수가 늘면 문구도 같이 오는 구조로, 소비처의 switch 분기를 없앤다
 * (조건분기 확장 금지 · {@code OsVolumeTargetKind.messageTemplate} 과 같은 결). 문맥 접미가 필요한 소비처는 덧붙여 쓴다.
 */
@RequiredArgsConstructor
@Getter
public enum RedfishError {

    /** 연결 자체가 안 됨 — BMC 다운 · 경로 불가 · 타임아웃. */
    CONNECT_FAILED("BMC 에 연결하지 못했습니다 — 주소 · 네트워크 · BMC 상태를 확인하세요."),
    /** 401 — 자격증명 불일치(다음 후보로 폴백하는 신호). 이 문구는 후보가 소진됐을 때만 사용자에게 닿는다. */
    AUTH_FAILED("모든 자격증명이 거부되었습니다 — 표준 비밀번호와 보드 시리얼(공장 기본)을 확인하세요."),
    /** 412 — ETag 선행 조건 불일치(fresh ETag 재시도 신호 — 이번 슬라이스의 전원 경로에는 없음). */
    PRECONDITION_FAILED("BMC 가 선행 조건(ETag)을 거절했습니다 — 다시 시도하세요."),
    /** 404 — 리소스 부재. AMI TaskMonitor 는 작업 종결 후 소멸하므로(실측) 일시 도달 불가와 갈라야 한다. */
    NOT_FOUND("BMC 에 해당 리소스가 없습니다."),
    /** 그 외 프로토콜 오류 — 4xx/5xx · 본문 해석 불가. */
    PROTOCOL("BMC 가 요청을 거절했거나 응답을 해석하지 못했습니다.");

    private final String userMessage;
}
