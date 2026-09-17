package com.example.serverprovision.execution.pxeinfra.spi;

/**
 * 임대의 바인딩 상태. dnsmasq 임대 파일(R16)은 상태를 적지 않아 파서가 ACTIVE 로 두고, 만료는 관측 시각과 만료 시각의
 * 비교로 판정한다. 나머지 값은 ISC dhcpd 시절의 어휘로 남겨 두되 파서가 만들지는 않는다.
 */
public enum LeaseBindingState {

    ACTIVE, FREE, EXPIRED, RELEASED, ABANDONED, BACKUP, RESET, UNKNOWN;

    public static LeaseBindingState from(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;
        try {
            return valueOf(raw.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
