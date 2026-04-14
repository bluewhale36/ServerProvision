package com.example.serverprovision.domain.os.model.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OS 의 상위 패밀리 분류.
 *
 * <p>설치 스크립트 포맷(Kickstart/Autoinstall/Unattend) 및 UI fragment 디스패치 기준으로 사용된다.
 * OS 패밀리는 도메인 계층에서 {@link com.example.serverprovision.domain.os.model.installation.OSInstallation}
 * 계층 구조와 1:1 대응하며, 요청 DTO 다형성 판별자({@code @JsonTypeInfo(property = "osFamily")})와
 * 프론트엔드 {@code data-os-family} 속성에 동일한 문자열이 쓰인다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum OSFamily {

    /** RHEL 계열: Rocky Linux, CentOS. Kickstart 포맷 사용. */
    RHEL_BASED("RHEL 계열"),

    /** Debian 계열: Ubuntu. Subiquity autoinstall YAML 포맷 사용. */
    DEBIAN_BASED("Debian 계열"),

    /** Windows 계열: Windows / Windows Server. unattend.xml 포맷 사용 (미구현). */
    WINDOWS_BASED("Windows 계열");

    private final String displayName;
}
