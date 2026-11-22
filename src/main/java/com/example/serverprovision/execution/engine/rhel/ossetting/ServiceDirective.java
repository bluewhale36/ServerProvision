package com.example.serverprovision.execution.engine.rhel.ossetting;

import java.util.Objects;

/** kickstart {@code services} 명령에 실을 서비스 하나와 그 지시. */
public record ServiceDirective(String name, ServiceIntent intent) {

    public ServiceDirective {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(intent, "intent");
    }
}
