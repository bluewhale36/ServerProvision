package com.example.serverprovision.global.marker;

/**
 * `.provision.json` 마커 파일의 위치 규칙.
 * <ul>
 *   <li>{@code IN_TREE} — 디렉토리 자원의 트리 루트 안에 {@code .provision.json} 으로 1 개. BIOS 번들과 같이 디렉토리 자체가 자원인 경우.</li>
 *   <li>{@code SIDECAR} — 단일 파일 자원의 형제로 {@code <basename>.provision.json}. ISO {@code .iso} 파일과 같이 파일 1 개가 자원의 전부인 경우.</li>
 * </ul>
 * <p>구체적인 위치 계산은 {@code ProvisionMarkerService.resolveMarkerFile(Path, MarkerLayout)} 로 위임.</p>
 */
public enum MarkerLayout {
    IN_TREE,
    SIDECAR
}
