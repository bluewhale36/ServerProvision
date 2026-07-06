package com.example.serverprovision.provisioning.setting.enums;

/**
 * systemd 서비스 지시 동작 (계약측 enum). {@code systemctl enable/disable} 로의 변환은
 * 실행 도메인 편입 슬라이스의 책임이므로 여기서는 의미 상수만 둔다.
 */
public enum ServiceAction {

    /** 부팅 시 자동 시작되도록 서비스를 활성화한다. */
    ENABLE,
    /** 부팅 시 자동 시작되지 않도록 서비스를 비활성화한다. */
    DISABLE
}
