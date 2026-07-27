package com.example.serverprovision.execution.asset.dto.response;

import java.util.List;

/**
 * 통합 시스템 자산 대시보드의 한 영역 묶음(진단 리눅스·TFTP 등). 대시보드는 영역별로 슬롯 표를 구분해
 * 보여주고 영역 헤더에 집계 배지("정상 n / 전체 m")와 봉인 버튼을 단다 — 그 조립에 필요한 것만 담는다.
 * 뷰는 도메인 {@code SystemAssetArea}/{@code AssetCondition} 을 모른 채 문자열·플래그만 받는다.
 *
 * @param areaKey      영역 라우팅 키 문자열({@code SystemAssetAreaKey.name()}) — 봉인 URL 경로변수·행 링크 분기에 쓴다
 * @param displayName  영역 표시명 (예: "진단 리눅스 부팅 자산")
 * @param availability 서빙 준비 상태 문자열({@code AreaAvailability.name()}) — 봉인 버튼 disabled 게이트
 * @param slots        이 영역의 슬롯 현황 행
 * @param okCount      이 영역에서 healthy(정상) 판정 슬롯 수
 * @param totalCount   이 영역의 전체 슬롯 수
 * @param supportsSeal 봉인 지원 여부 — false 면 뷰가 봉인 버튼 자체를 렌더하지 않는다(UI 1차 차단, 서버 가드와 동일 SSOT)
 */
public record SystemAssetAreaGroupResponse(
        String areaKey,
        String displayName,
        String availability,
        List<SystemAssetSlotResponse> slots,
        int okCount,
        int totalCount,
        boolean supportsSeal
) {
}
