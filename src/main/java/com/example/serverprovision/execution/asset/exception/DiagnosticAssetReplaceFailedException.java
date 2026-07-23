package com.example.serverprovision.execution.asset.exception;

import com.example.serverprovision.global.exception.DomainException;

/**
 * 교체 스왑의 IO 실패(임시 파일 쓰기 / 원자적 rename 실패 등). 진짜 비정상이라 {@code @ResponseStatus} 없이
 * advice 의 handleDomain 이 500 으로 매핑하고 원 예외를 로그에 남긴다(silent 흡수 금지).
 */
public class DiagnosticAssetReplaceFailedException extends DomainException {

    public DiagnosticAssetReplaceFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
