package com.example.serverprovision.global.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * S5-2-2 — hard-delete (purge) 시 사용자가 입력한 자원명이 기대값과 일치하지 않을 때 던진다.
 *
 * <p>frontend 의 confirm-action.js 가 사용자 입력 검증을 수행하지만,
 * 백엔드에서도 우회 / 직접 POST 공격을 대비해 다시 검증한다. 이중 검증의 백엔드 측.</p>
 *
 * <p>{@code @ResponseStatus(BAD_REQUEST)} 로 자동 매핑되어 400 응답.</p>
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class TypedNameMismatchException extends DomainException {

    private final String expected;
    private final String typed;

    public TypedNameMismatchException(String expected, String typed) {
        super("자원명이 일치하지 않습니다. (입력값='" + typed + "', 기대='" + expected + "')");
        this.expected = expected;
        this.typed = typed;
    }

    public String getExpected() {
        return expected;
    }

    public String getTyped() {
        return typed;
    }
}
