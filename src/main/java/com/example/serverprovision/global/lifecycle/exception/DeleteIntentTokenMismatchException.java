package com.example.serverprovision.global.lifecycle.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * MK3-2 (DCM3-2.6) — DeleteIntent token 의 (resourceType, resourceId) 가 호출 인자와 불일치.
 * <p>ApiExceptionHandler 가 410 Gone 으로 매핑 (만료와 동등 의미 — 호출자에 token 무효 통보).</p>
 */
public class DeleteIntentTokenMismatchException extends DomainException {

    public DeleteIntentTokenMismatchException(String tokenAsString, String expected, String actual) {
        super("DeleteIntent token 이 다른 자원에 발급되었습니다 : " + tokenAsString
                + " expected=" + expected + " actual=" + actual);
    }
}
