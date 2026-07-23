package com.example.serverprovision.execution.asset.exception;

import com.example.serverprovision.global.exception.ConflictException;

/**
 * 자산 서빙이 비활성인데(무설정 {@code pxe.assets.root}) 봉인·교체처럼 자산 위치가 필요한 액션을 요청한 경우.
 * 정상 흐름은 UI 가 서빙 비활성 시 버튼을 disabled 해 1차 차단하므로, 이 예외는 direct POST · stale
 * 화면에서만 도달하는 안전망이다. base {@link ConflictException} 이 409 로 매핑한다.
 */
public class SystemAssetServingDisabledException extends ConflictException {

    private SystemAssetServingDisabledException(String message) {
        super(message);
    }

    public static SystemAssetServingDisabledException servingDisabled() {
        return new SystemAssetServingDisabledException(
                "자산 서빙이 비활성 상태입니다 (PXE_ASSETS_ROOT 미설정) — 자산 위치가 있어야 가능한 작업입니다 (조회는 가능).");
    }
}
