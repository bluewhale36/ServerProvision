package com.example.serverprovision.execution.enums;

/**
 * 체크인·보고 응답으로 에이전트에 내리는 지시(E1-0b 골격). 신규 지시는 값 추가 + 에이전트 계약
 * 갱신으로 자란다 — E1-2 가 COLLECT(수집하라)·REBOOT(재부팅하라)를 추가할 확장 자리.
 */
public enum AgentDirective {

    /** 대기 — 현재 내릴 작업 지시가 없다. */
    WAIT
}
