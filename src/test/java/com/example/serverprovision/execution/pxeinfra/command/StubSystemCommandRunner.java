package com.example.serverprovision.execution.pxeinfra.command;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 테스트용 {@link SystemCommandRunner} 스텁 — 실제 프로세스를 spawn 하지 않고 명령별로 미리 지정한
 * {@link CommandResult} 를 돌려준다. 호출 이력을 기록해 "미구성이면 특권 명령을 spawn 하지 않는다" 같은
 * 게이팅 계약을 호출 횟수로 검증할 수 있게 한다. 미지정 명령은 기본 {@code notFound()} 로 흡수한다.
 */
public class StubSystemCommandRunner implements SystemCommandRunner {

    private final Map<AllowedCommand, CommandResult> stubs = new EnumMap<>(AllowedCommand.class);
    private final List<Invocation> invocations = new ArrayList<>();

    /** 특정 명령에 대해 돌려줄 결과를 등록한다(빌더식 체이닝). */
    public StubSystemCommandRunner stub(AllowedCommand command, CommandResult result) {
        stubs.put(command, result);
        return this;
    }

    @Override
    public CommandResult run(AllowedCommand command, List<String> callerArgs) {
        invocations.add(new Invocation(command, List.copyOf(callerArgs)));
        return stubs.getOrDefault(command, CommandResult.notFound());
    }

    public List<Invocation> invocations() {
        return invocations;
    }

    public int callCount() {
        return invocations.size();
    }

    /** 한 번의 {@code run} 호출 — 어떤 명령이 어떤 caller 인자로 실행됐는지. */
    public record Invocation(AllowedCommand command, List<String> callerArgs) {
    }
}
