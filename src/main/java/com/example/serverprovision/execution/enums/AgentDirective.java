package com.example.serverprovision.execution.enums;

/**
 * 체크인·보고 응답으로 에이전트에 내리는 지시(E1-0b 골격 → E1-2 실전화). 신규 지시는 값 추가 +
 * 에이전트 계약(agent.sh) 갱신으로 자란다. 판정 규칙은 {@code AgentReportService#directiveFor} 단일 지점.
 */
public enum AgentDirective {

    /** 대기 — 현재 내릴 작업 지시가 없다(적재 후 판정 대기 등 과도 상태 포함). */
    WAIT,

    /** 수집하라(E1-2) — 커서가 진단이고 아직 미수집(IPXE_REGISTERED)일 때. */
    COLLECT,

    /**
     * 재부팅하라(E1-2) — phase 완주(종단 포함) 후 진단 리눅스를 떠나 iPXE 폴링으로 복귀시킨다.
     * 완주 서버의 체크인은 보고 게이트(PROVISIONING 한정)가 거절하므로 이 지시는 <b>close 응답</b>이
     * 운반한다(같은 트랜잭션에서 완주가 판정된 직후 — 로드맵 "checkin/close 응답의 directive").
     */
    REBOOT
}
