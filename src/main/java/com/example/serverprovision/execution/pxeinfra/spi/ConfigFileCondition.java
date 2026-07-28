package com.example.serverprovision.execution.pxeinfra.spi;

import com.example.serverprovision.execution.asset.spi.AssetCondition;
import com.example.serverprovision.execution.pxeinfra.command.CommandResult;

/**
 * dhcpd 조각 파일 슬롯의 무결성 판정 — 파일 봉인 사다리가 아니라 존재 + 전체 구성 문법 결과로 판정한다.
 * {@link AssetCondition} 을 구현해 대시보드 집계·배지에 진단·TFTP 영역과 동일하게 합류한다.
 */
public enum ConfigFileCondition implements AssetCondition {

    NOT_CONFIGURED("서빙 비활성", "n-badge-gray"),
    ABSENT("조각 파일 없음", "n-badge-gray"),
    SYNTAX_ERROR("구성 문법 오류", "n-badge-red"),   // dhcpd -t 는 조각이 아닌 dhcpd.conf 전체 검사 — 조각 무관 오류도 포함
    UNKNOWN("문법 미검사", "n-badge-orange"),
    SYNTAX_OK("구성 유효", "n-badge-green");

    private final String label;
    private final String badgeClass;

    ConfigFileCondition(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String badgeClass() {
        return badgeClass;
    }

    @Override
    public boolean healthy() {
        return this == SYNTAX_OK;
    }

    /** 조각 존재 + 전체 구성 문법 결과로 판정. NOT_CONFIGURED 는 호출자(area, 미구성)가 직접 쓴다. */
    public static ConfigFileCondition of(boolean fragmentPresent, CommandResult syntaxCheck) {
        if (!fragmentPresent) return ABSENT;
        return switch (syntaxCheck.outcome()) {
            case NOT_FOUND, TIMED_OUT -> UNKNOWN;
            case COMPLETED -> syntaxCheck.exitedZero() ? SYNTAX_OK : SYNTAX_ERROR;
        };
    }
}
