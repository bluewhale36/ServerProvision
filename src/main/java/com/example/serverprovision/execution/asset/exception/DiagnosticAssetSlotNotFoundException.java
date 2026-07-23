package com.example.serverprovision.execution.asset.exception;

import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 교체 URL 의 {@code {slot}} 경로변수가 존재하지 않는 진단 자산 이름인 경우(forging). 정상 흐름은 UI 가
 * 고정 슬롯만 링크하므로 direct POST 에서만 도달한다. base {@link NotFoundException} 이 404 로 매핑한다.
 */
public class DiagnosticAssetSlotNotFoundException extends NotFoundException {

    private DiagnosticAssetSlotNotFoundException(String message) {
        super(message);
    }

    public static DiagnosticAssetSlotNotFoundException of(String slotKey) {
        return new DiagnosticAssetSlotNotFoundException("존재하지 않는 진단 자산 슬롯입니다 : " + slotKey);
    }
}
