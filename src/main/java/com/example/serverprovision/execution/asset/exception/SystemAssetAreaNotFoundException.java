package com.example.serverprovision.execution.asset.exception;

import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.global.exception.NotFoundException;

/**
 * 통합 대시보드의 영역 라우팅 키가 등록된 {@code SystemAssetArea} 중 어디에도 없을 때. 정상 흐름에서는
 * 대시보드가 실재하는 영역만 링크하므로, 이 예외는 존재하지 않는 {@code /system/asset/{area}/seal} 키를
 * 직접 요청한 주소 위조 경로에서만 도달한다. base {@link NotFoundException} 이 404 로 매핑한다.
 */
public class SystemAssetAreaNotFoundException extends NotFoundException {

    private SystemAssetAreaNotFoundException(String message) {
        super(message);
    }

    /** 유효한 enum 상수이나 등록된 영역 구현이 없을 때. */
    public static SystemAssetAreaNotFoundException of(SystemAssetAreaKey areaKey) {
        return new SystemAssetAreaNotFoundException(
                "등록된 시스템 자산 영역이 아닙니다 : " + areaKey);
    }

    /** URL 경로 변수 문자열이 enum 상수로 파싱조차 되지 않을 때(forging). */
    public static SystemAssetAreaNotFoundException of(String rawKey) {
        return new SystemAssetAreaNotFoundException(
                "존재하지 않는 시스템 자산 영역 키입니다 : " + rawKey);
    }
}
