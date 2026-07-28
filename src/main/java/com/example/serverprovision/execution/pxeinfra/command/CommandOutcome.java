package com.example.serverprovision.execution.pxeinfra.command;

/**
 * 외부 명령 실행의 종료 양태 — 3-상태를 상호배타 타입화(int exitCode + boolean 직교 원시 2개의 무효 조합 방지).
 */
public enum CommandOutcome {
    COMPLETED, TIMED_OUT, NOT_FOUND
}
