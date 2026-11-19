package com.example.serverprovision.execution.engine.osinstall;

/**
 * OS 설치 원장 행을 적은 계열(E4-1-b-4-1). 원장 meta 의 {@code origin} 에는 {@link #wire()} 문자열을 그대로 적어
 * 기존 행 · {@code ProvisioningHistory.extractMeta("origin")} 와 저장 형식이 같다(DDL · 데이터 이전 없음).
 */
public enum OsInstallOrigin {

    WINDOWS_INSTALL("windows-install");

    private final String wire;

    OsInstallOrigin(String wire) {
        this.wire = wire;
    }

    /** 원장 meta 에 적는 값. */
    public String wire() {
        return wire;
    }

    /** meta 값이 이 계열인가 — 표지 없음 · 다른 표지면 false. */
    public boolean matches(Object metaOrigin) {
        return wire.equals(metaOrigin);
    }
}
