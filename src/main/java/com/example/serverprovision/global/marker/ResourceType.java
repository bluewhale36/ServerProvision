package com.example.serverprovision.global.marker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 마커가 부착될 수 있는 자원 종류. 각 상수는 default {@link MarkerLayout} 을 보유한다.
 * <ul>
 *   <li>{@code BIOS_BUNDLE} — BIOS 번들 디렉토리. IN_TREE 마커.</li>
 *   <li>{@code OS_ISO} — OS ISO 단일 파일. SIDECAR 마커.</li>
 *   <li>{@code BMC_FIRMWARE} — BMC 펌웨어 번들 디렉토리 (MA4). IN_TREE 마커.</li>
 *   <li>{@code SUBPROGRAM} — Driver / Utility 통합 번들 디렉토리 (MA5). IN_TREE 마커. kind 는 마커 attributes 로 분기.</li>
 * </ul>
 * <p>새 자원 종류 추가 시 본 enum 에 상수 + 도메인의 {@code MarkableScanner} 구현 1 종을 함께 등록한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum ResourceType {
    BIOS_BUNDLE(MarkerLayout.IN_TREE),
    OS_ISO(MarkerLayout.SIDECAR),
    BMC_FIRMWARE(MarkerLayout.IN_TREE),
    SUBPROGRAM(MarkerLayout.IN_TREE);

    private final MarkerLayout defaultLayout;
}
