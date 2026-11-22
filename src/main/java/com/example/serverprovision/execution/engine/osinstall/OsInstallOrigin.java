package com.example.serverprovision.execution.engine.osinstall;

/**
 * OS 설치 원장 행을 적은 계열(E4-1-b-4-1). 원장 meta 의 {@code origin} 에는 {@link #wire()} 문자열을 그대로 적어
 * 기존 행 · {@code ProvisioningHistory.extractMeta("origin")} 와 저장 형식이 같다(DDL · 데이터 이전 없음).
 * 게스트 콘솔 문구와 로그 머리표도 계열마다 여기서 읽는다.
 */
public enum OsInstallOrigin {

    WINDOWS_INSTALL("windows-install", "windows install", "wininstall"),
    RHEL_INSTALL("rhel-install", "rhel install", "rhelinstall");

    private final String wire;
    private final String consoleLabel;
    private final String logTag;

    OsInstallOrigin(String wire, String consoleLabel, String logTag) {
        this.wire = wire;
        this.consoleLabel = consoleLabel;
        this.logTag = logTag;
    }

    /** 원장 meta 에 적는 값. */
    public String wire() {
        return wire;
    }

    /** 게스트 콘솔(iPXE echo)에 쓰는 계열 이름. */
    public String consoleLabel() {
        return consoleLabel;
    }

    /** 앱 로그의 머리표({@code [wininstall]} 등). */
    public String logTag() {
        return logTag;
    }

    /** meta 값이 이 계열인가 — 표지 없음 · 다른 표지면 false. */
    public boolean matches(Object metaOrigin) {
        return wire.equals(metaOrigin);
    }
}
