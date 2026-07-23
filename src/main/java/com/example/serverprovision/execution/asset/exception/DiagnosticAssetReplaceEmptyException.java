package com.example.serverprovision.execution.asset.exception;

import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.global.exception.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 교체 업로드에 파일이 없거나 빈(0바이트) 경우. {@code @ResponseStatus(400)} 을 advice 의 handleDomain 이
 * 흡수한다(필드 직결 400 이 아니라 단일 파일 파라미터라 FieldBound 계열 대신 평문 400).
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DiagnosticAssetReplaceEmptyException extends DomainException {

    private DiagnosticAssetReplaceEmptyException(String message) {
        super(message);
    }

    public static DiagnosticAssetReplaceEmptyException of(DiagnosticAsset slot) {
        return new DiagnosticAssetReplaceEmptyException("교체할 파일이 비어 있습니다 : " + slot.label());
    }
}
