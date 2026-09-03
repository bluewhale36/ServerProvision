package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.engine.phase.ProvisioningPhaseExecutor;

import java.util.Optional;

/**
 * dispatch 판정의 결과(E4-1-a-3 D-1) — 스크립트와 "누구의 스크립트를 내주기로 했는가". 8행 위임에서만 실행기가
 * 채워지고, 호출자({@code BootService})가 그 실행기에 {@link ProvisioningPhaseExecutor#onBootScriptServed} 훅을 건다.
 * dispatcher 자신은 여전히 상태를 바꾸지 않는다(DEC-2) — 사실을 반환값에 실을 뿐이다.
 */
public record BootDispatch(String script, Optional<ProvisioningPhaseExecutor> delegated) {

    public static BootDispatch plain(String script) {
        return new BootDispatch(script, Optional.empty());
    }

    public static BootDispatch delegated(String script, ProvisioningPhaseExecutor executor) {
        return new BootDispatch(script, Optional.of(executor));
    }
}
