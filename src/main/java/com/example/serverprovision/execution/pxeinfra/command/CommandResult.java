package com.example.serverprovision.execution.pxeinfra.command;

/**
 * 외부 명령 실행 결과. 실패(타임아웃·부재·비0)를 예외가 아닌 상태로 담아, 관측 경로가 예외로 끊기지 않게 한다.
 */
public record CommandResult(CommandOutcome outcome, int exitCode, String stdout, String stderr) {

    /** COMPLETED 이고 종료코드 0 일 때만 true — exitCode 는 COMPLETED 에서만 의미로 가드된다. */
    public boolean exitedZero() {
        return outcome == CommandOutcome.COMPLETED && exitCode == 0;
    }

    public static CommandResult completed(int exitCode, String stdout, String stderr) {
        return new CommandResult(CommandOutcome.COMPLETED, exitCode, stdout, stderr);
    }

    public static CommandResult timedOut() {
        return new CommandResult(CommandOutcome.TIMED_OUT, -1, "", "");
    }

    public static CommandResult notFound() {
        return new CommandResult(CommandOutcome.NOT_FOUND, -1, "", "");
    }
}
