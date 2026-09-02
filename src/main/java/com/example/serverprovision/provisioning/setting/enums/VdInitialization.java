package com.example.serverprovision.provisioning.setting.enums;

/**
 * VD 파라미터 축(E3.5-6) — 생성 직후 초기화 방식(HII "Default Initialization"). {@link #DEFAULT} 는 HII 기본 선택
 * No — 소거가 필요한 정의서는 FAST 를 고른다(2026-09-02 미지정 축 폐지 · 사용자 결정). storcli 어휘는 agent 가
 * {@code start init [full] force} 로 옮긴다(OS/FS 감지 거부 · Failure-exit 0 대응, 2026-09-01 실기).
 */
public enum VdInitialization {
    /** HII "Default Initialization: No"(기본) — 집행이 init 을 생략해 디스크의 기존 데이터가 남는다. */
    NONE("none", "No (초기화 안 함)"),
    /** 선두 · 말미 메타데이터만 지운다 — 2026-09-01 실기의 데이터 소거는 이 값(force 동반)으로 한다. */
    FAST("fast", "Fast Initialization"),
    /** 전 영역 0 기록 — 대용량 HDD 배열은 수 시간, 완료 전 VD 사용 가능 여부는 실기 확인 대상. */
    FULL("full", "Full Initialization");

    public static final VdInitialization DEFAULT = NONE;

    private final String cliToken;
    private final String displayName;

    VdInitialization(String cliToken, String displayName) {
        this.cliToken = cliToken;
        this.displayName = displayName;
    }

    public String cliToken() { return cliToken; }
    public String getDisplayName() { return displayName; }
    /** 폼의 selected · "(기본값)" 표기 · 배지(기본값과 다른 축 수)의 판정 재료 — 서버 · 뷰가 같은 상수를 본다. */
    public boolean isDefault() { return this == DEFAULT; }
}
