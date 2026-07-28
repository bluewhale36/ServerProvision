package com.example.serverprovision.execution.pxeinfra.command;

import java.util.List;

/**
 * 특권 외부 명령 실행의 유일 경계. 구현은 실패(타임아웃·부재·비0)를 예외가 아닌 {@link CommandResult} 상태로 흡수한다.
 */
public interface SystemCommandRunner {
    CommandResult run(AllowedCommand command, List<String> callerArgs);
}
