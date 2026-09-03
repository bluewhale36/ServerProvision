package com.example.serverprovision.execution.wininstall.catalog;

import com.example.serverprovision.execution.asset.spi.AssetCondition;

/**
 * Windows 설치 소스 파일(boot.wim · install.wim · setup.exe) 한 슬롯의 판정. 파일 봉인 모델이 아니라
 * 존재 · 해석 가능 여부만 본다 — 소스는 운영 절차가 통째로 교체하는 것이라 해시 봉인의 의미가 없다.
 */
public enum InstallSourceCondition implements AssetCondition {

    /** 설치 소스 루트 미설정 — 어디를 봐야 할지 모른다. */
    NOT_CONFIGURED("서빙 비활성", "n-badge-gray"),

    /** 루트는 있으나 파일이 없다 — 추출 절차(런북 §14-1)가 아직 안 됐다. */
    MISSING("파일 없음", "n-badge-red"),

    /** 파일은 있으나 WIM 헤더 · XML 을 해석할 수 없다. */
    UNREADABLE("읽기 실패", "n-badge-orange"),

    /** 존재하고(install.wim 은 해석까지) 정상. */
    PRESENT("존재", "n-badge-green");

    private final String label;
    private final String badgeClass;

    InstallSourceCondition(String label, String badgeClass) {
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
        return this == PRESENT;
    }
}
