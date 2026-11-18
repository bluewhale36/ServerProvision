package com.example.serverprovision.provisioning.setting.enums;

import com.example.serverprovision.management.os.enums.OSName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

/**
 * OS 계열 — 상수명 = 요청 JSON 의 2단 판별자({@code "osFamily"}) 문자열.
 *
 * <p>{@code WINDOWS} 는 U2-1 에서 자리만 예약했다가 E4-1-a-2 에서 실체화했다 — 소비자
 * ({@code WindowsInstallationRequest} · 계열 검사기 · 폼 분기)가 생긴 시점이 곧 추가 시점이라는 원칙 그대로다.
 * management 쪽 계열과의 사상은 상수마다 짝지은 {@link #source} 하나다(옵션 조립 · 지원 판정이 함께 읽는다).</p>
 */
@RequiredArgsConstructor
@Getter
public enum OSFamily {

    RHEL_BASED("RHEL 계열", com.example.serverprovision.management.os.enums.OSFamily.RHEL_BASED),
    DEBIAN_BASED("Debian 계열", com.example.serverprovision.management.os.enums.OSFamily.DEBIAN_BASED),
    WINDOWS("Windows 계열", com.example.serverprovision.management.os.enums.OSFamily.WINDOWS_BASED);

    private final String displayName;
    private final com.example.serverprovision.management.os.enums.OSFamily source;

    /** 등록된 OS 이름이 속한 정의서 계열(요청 판별자와 같은 축). */
    public static OSFamily of(OSName osName) {
        return Arrays.stream(values())
                .filter(f -> f.source == osName.getFamily())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("정의서 계열 사상이 없는 OS 계열: " + osName.getFamily()));
    }
}
