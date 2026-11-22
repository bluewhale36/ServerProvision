package com.example.serverprovision.execution.engine.rhel.ossetting;

import java.util.Arrays;

/** 첫 부팅에서 {@code systemctl is-enabled} 가 답한 서비스 상태. 모르는 답은 {@link #UNKNOWN} 으로 읽고 원문은 따로 남긴다. */
public enum ServiceEnablement {
    ENABLED("enabled"), DISABLED("disabled"), MASKED("masked"), STATIC("static"), NOT_FOUND("not-found"), UNKNOWN("unknown");

    private final String wire;

    ServiceEnablement(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static ServiceEnablement of(String value) {
        return Arrays.stream(values()).filter(state -> state.wire.equals(value)).findFirst().orElse(UNKNOWN);
    }
}
