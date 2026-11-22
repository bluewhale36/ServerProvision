package com.example.serverprovision.execution.engine.rhel.kickstart;

import java.util.regex.Pattern;

/**
 * kickstart 줄에 끼워 넣는 값의 마지막 방어선. 값은 정의서 검증(마운트포인트 · 사용자명 · IANA 시간대 패턴)과
 * comps 코드에서 오므로 여기에 걸리면 프로그램 오류다 — 공백 · 따옴표 · # · 역슬래시 · 개행이 줄을 쪼개거나 주석으로 만든다.
 */
final class KickstartToken {

    private static final Pattern UNSAFE = Pattern.compile("[\\s'\"#\\\\]");

    private KickstartToken() {
    }

    static String check(String value) {
        if (value == null || value.isEmpty() || UNSAFE.matcher(value).find()) {
            throw new IllegalStateException("kickstart 에 넣을 수 없는 값: [" + value + "]");
        }
        return value;
    }
}
