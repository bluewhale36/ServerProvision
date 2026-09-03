package com.example.serverprovision.provisioning.setting.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OS 계열 — 상수명 = 요청 JSON 의 2단 판별자({@code "osFamily"}) 문자열.
 *
 * <p>{@code WINDOWS} 는 U2-1 에서 자리만 예약했다가 E4-1-a-2 에서 실체화했다 — 소비자
 * ({@code WindowsInstallationRequest} · 계열 검사기 · 폼 분기)가 생긴 시점이 곧 추가 시점이라는 원칙 그대로다.
 * management 쪽 {@code OSFamily.WINDOWS_BASED} 와의 사상은 {@code JpaSettingQueryService.FAMILY_MAPPING}.</p>
 */
@RequiredArgsConstructor
@Getter
public enum OSFamily {

    RHEL_BASED("RHEL 계열"),
    DEBIAN_BASED("Debian 계열"),
    WINDOWS("Windows 계열");

    private final String displayName;
}
