package com.example.serverprovision.execution.pxeinfra.command;

/**
 * 외부 명령 실행의 종료 양태 — 3-상태를 상호배타 타입화(int exitCode + boolean 직교 원시 2개의 무효 조합 방지).
 * 화면 이름은 적용 실패 안내에 상수 원문 대신 쓴다(S22-4).
 */
public enum CommandOutcome {
    COMPLETED("완료"), TIMED_OUT("시간 초과"), NOT_FOUND("실행 파일 미발견");

    private final String description;

    CommandOutcome(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
