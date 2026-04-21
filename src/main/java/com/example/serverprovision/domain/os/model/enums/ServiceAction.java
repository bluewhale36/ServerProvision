package com.example.serverprovision.domain.os.model.enums;

/**
 * OS 후처리 단계에서 systemd 서비스에 수행할 동작 유형.
 *
 * <p>Kickstart {@code %post} / Ubuntu autoinstall {@code late-commands} 에서
 * {@code systemctl enable} 혹은 {@code systemctl disable} 명령으로 변환된다.</p>
 */
public enum ServiceAction {

    /** 부팅 시 자동 시작되도록 서비스를 활성화한다. */
    ENABLE,

    /** 부팅 시 자동 시작되지 않도록 서비스를 비활성화한다. */
    DISABLE;

    /**
     * 해당 동작에 대응하는 {@code systemctl} 서브커맨드 이름을 반환한다.
     * 스크립트 생성 시 {@code systemctl <subcommand> <service>} 형태로 조립된다.
     */
    public String getSystemctlSubcommand() {
        return name().toLowerCase();
    }
}
