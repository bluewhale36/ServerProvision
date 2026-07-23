package com.example.serverprovision.execution.asset.exception;

import com.example.serverprovision.execution.asset.enums.DiagnosticAsset;
import com.example.serverprovision.global.exception.ConflictException;

/**
 * 조립 자산(apkovl · repo)처럼 UI 교체 대상이 아닌 슬롯에 교체를 요청한 경우. 정상 흐름은 UI 가 비대상
 * 슬롯에 교체 버튼을 붙이지 않아 1차 차단하므로 direct POST 안전망이다. base {@link ConflictException} 이 409 매핑.
 */
public class DiagnosticAssetNotReplaceableException extends ConflictException {

    private DiagnosticAssetNotReplaceableException(String message) {
        super(message);
    }

    public static DiagnosticAssetNotReplaceableException of(DiagnosticAsset slot) {
        return new DiagnosticAssetNotReplaceableException(
                slot.label() + " 는 조립 자산이라 UI 교체 대상이 아닙니다 — build-assets.sh 로 교체합니다.");
    }
}
