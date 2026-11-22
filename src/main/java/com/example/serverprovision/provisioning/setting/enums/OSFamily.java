package com.example.serverprovision.provisioning.setting.enums;

import com.example.serverprovision.management.os.enums.OSName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.List;

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

    RHEL_BASED("RHEL 계열", com.example.serverprovision.management.os.enums.OSFamily.RHEL_BASED, true),
    DEBIAN_BASED("Debian 계열", com.example.serverprovision.management.os.enums.OSFamily.DEBIAN_BASED, false),
    WINDOWS("Windows 계열", com.example.serverprovision.management.os.enums.OSFamily.WINDOWS_BASED, false);

    /** OS 후처리를 쓸 수 없는 계열을 고른 정의서의 저장 거절 문구. 폼의 잠금 안내도 같은 문장을 쓴다(E4-1-b-7). */
    public static final String OS_SETTING_UNSUPPORTED = "OS 후처리는 RHEL 계열 OS 를 설치할 때만 쓸 수 있습니다.";

    private final String displayName;
    private final com.example.serverprovision.management.os.enums.OSFamily source;

    /** OS 후처리 단계를 실행할 수 있는 계열인가. 저장 가드와 폼 잠금이 함께 읽는다(E4-1-b-7, Ubuntu 는 설치 실행이 생길 때 연다). */
    private final boolean osSettingSupported;

    /** 등록된 OS 이름이 속한 정의서 계열(요청 판별자와 같은 축). */
    public static OSFamily of(OSName osName) {
        return Arrays.stream(values())
                .filter(f -> f.source == osName.getFamily())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("정의서 계열 사상이 없는 OS 계열: " + osName.getFamily()));
    }

    /** OS 후처리를 쓸 수 있는 계열 이름들. 폼이 OS 설치 선택의 계열과 비교한다. */
    public static List<String> osSettingSupportedNames() {
        return Arrays.stream(values()).filter(OSFamily::isOsSettingSupported).map(Enum::name).toList();
    }
}
