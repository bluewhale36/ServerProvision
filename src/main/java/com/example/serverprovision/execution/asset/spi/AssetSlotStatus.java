package com.example.serverprovision.execution.asset.spi;

import java.time.Instant;

/**
 * {@link SystemAssetArea#inspect}의 반환형 — 한 슬롯을 프로브한 결과. 대시보드가 슬롯 응답 1행을 조립하는 데
 * 필요한 것 전부(존재·크기·수정시각·무결성 판정)를 담는다. 진단 자산 서비스의 내부 {@code Probe}(존재·크기·
 * 수정시각·해시) + {@code IntegrityStatus} 를 영역 무관 형태로 승격한 것이다 — 해시는 마커 검증 후 판정으로
 * 흡수되므로 대시보드까지 노출하지 않고, 판정은 {@link AssetCondition} 하나로 전달한다.
 *
 * @param present    자산 본체 존재 여부
 * @param sizeBytes  자산 크기(바이트) — 부재 시 0
 * @param modifiedAt 최종 수정 시각 — 부재 시 null
 * @param condition  무결성·현황 판정(집계·배지·차단의 단일 근거)
 */
public record AssetSlotStatus(
        boolean present,
        long sizeBytes,
        Instant modifiedAt,
        AssetCondition condition
) {

    /** 자산이 없는 슬롯. 크기 0·수정시각 null·주어진 부재 판정으로 조립한다(MISSING / NOT_CONFIGURED 등). */
    public static AssetSlotStatus notPresent(AssetCondition condition) {
        return new AssetSlotStatus(false, 0L, null, condition);
    }

    /** 존재하는 슬롯. 프로브한 크기·수정시각과 봉인 판정으로 조립한다. */
    public static AssetSlotStatus present(long sizeBytes, Instant modifiedAt, AssetCondition condition) {
        return new AssetSlotStatus(true, sizeBytes, modifiedAt, condition);
    }
}
