package com.example.serverprovision.execution.asset.exception;

import com.example.serverprovision.execution.asset.spi.SystemAssetAreaKey;
import com.example.serverprovision.global.exception.ConflictException;

/**
 * 무결성 봉인을 지원하지 않는 영역에 봉인을 요청한 경우. 봉인은 정적 파일의 해시를 기준선으로 기록하는
 * 영역(진단 자산·TFTP 부팅 파일)에만 의미가 있고, 편집 대상 설정·서비스 상태로 판정하는 영역(예: 향후
 * dhcpd 설정 영역)은 봉인 개념이 없다 — 그런 영역은 {@code SystemAssetArea.supportsSeal()} 을 false 로
 * 두고, 기본 {@code seal()} 이 이 예외를 던진다. 정상 흐름은 UI 가 봉인 버튼을 그 영역에 노출하지 않아
 * 1차 차단하므로, 이 예외는 direct POST 안전망이다. base {@link ConflictException} 이 409 로 매핑한다.
 */
public class SystemAssetSealNotSupportedException extends ConflictException {

    private SystemAssetSealNotSupportedException(String message) {
        super(message);
    }

    public static SystemAssetSealNotSupportedException of(SystemAssetAreaKey areaKey) {
        return new SystemAssetSealNotSupportedException(
                "이 자산 영역은 무결성 봉인을 지원하지 않습니다 : " + areaKey);
    }
}
