package com.example.serverprovision.execution.pxeinfra.spi;

/**
 * ISC dhcpd.leases 의 {@code binding state} 값. 미지·손상 값은 예외 대신 UNKNOWN 으로 흡수한다.
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
