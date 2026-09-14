package com.example.serverprovision.management.subprogram.exception;

import com.example.serverprovision.global.exception.FieldBoundBadRequestException;

/**
 * 변형 행의 규칙 위반(R15-1) — 진입점 누락 · 허용되지 않는 확장자 · 같은 OS 버전 중복 · 전 버전 변형 2 행.
 * 폼은 같은 규칙({@code SubprogramVariantRules})으로 먼저 거르므로 이 예외는 direct POST 의 안전망이다.
 */
public class InvalidSubprogramVariantException extends FieldBoundBadRequestException {

    public InvalidSubprogramVariantException(String message, String fieldName) {
        super(message, fieldName);
    }
}
