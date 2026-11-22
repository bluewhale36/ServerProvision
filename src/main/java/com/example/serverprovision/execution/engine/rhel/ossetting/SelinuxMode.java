package com.example.serverprovision.execution.engine.rhel.ossetting;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** 정의서가 요구하는 SELinux 모드. kickstart 명령과 첫 부팅의 {@code getenforce} 관측이 같은 이름(소문자)을 쓴다. */
public enum SelinuxMode {
    ENFORCING, PERMISSIVE, DISABLED;

    /** 계약 · 관측 문자열(대소문자 무관)에서 읽는다. 모르는 값이면 비어 있다. */
    public static Optional<SelinuxMode> of(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String wire = value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(mode -> mode.wire().equals(wire)).findFirst();
    }

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String kickstartCommand() {
        return "selinux --" + wire();
    }
}
