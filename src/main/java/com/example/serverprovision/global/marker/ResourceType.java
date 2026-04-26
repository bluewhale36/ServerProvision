package com.example.serverprovision.global.marker;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 마커가 부착될 수 있는 자원 종류. 각 상수는 default {@link MarkerLayout} 을 보유한다.
 * <ul>
 *   <li>{@code BIOS_BUNDLE} — BIOS 번들 디렉토리. IN_TREE 마커.</li>
 *   <li>{@code OS_ISO} — OS ISO 단일 파일. SIDECAR 마커.</li>
 *   <li>{@code BMC_FIRMWARE} — BMC 펌웨어 번들 디렉토리 (MA4). IN_TREE 마커.</li>
 *   <li>{@code DRIVER} — Driver 패키지 (MA5). 형식 결정 후 layout 확정. 임시로 SIDECAR.</li>
 * </ul>
 * <p>새 자원 종류 추가 시 본 enum 에 상수 + 도메인의 {@code MarkableScanner} 구현 1 종을 함께 등록한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum ResourceType {
    BIOS_BUNDLE(MarkerLayout.IN_TREE),
    OS_ISO(MarkerLayout.SIDECAR),
    BMC_FIRMWARE(MarkerLayout.IN_TREE),
    DRIVER(MarkerLayout.SIDECAR);

    private final MarkerLayout defaultLayout;
}
