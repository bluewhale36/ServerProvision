package com.example.serverprovision.provisioning.setting.enums;

/**
 * VD 파라미터 축(E3.5-6) — MegaRAID "CONFIGURE VIRTUAL DRIVE PARAMETERS" 의 한 항목. 값은 컨트롤러 중립으로
 * 명명하고 storcli 어휘는 {@code cliToken} 이 소유한다(새 카드 계열 = 어휘 매핑만 추가). {@link #DEFAULT} 는
 * 9361-8i HII 의 기본 선택 — 정의서가 고르지 않은 축도 이 값으로 항상 명시 전송한다(2026-09-02 미지정 축 폐지).
 */
public enum VdStripSize {
    KB_8("strip=8", "8 KB"),
    KB_16("strip=16", "16 KB"),
    KB_32("strip=32", "32 KB"),
    KB_64("strip=64", "64 KB"),
    KB_128("strip=128", "128 KB"),
    KB_256("strip=256", "256 KB"),
    KB_512("strip=512", "512 KB"),
    KB_1024("strip=1024", "1 MB");

    public static final VdStripSize DEFAULT = KB_256;

    private final String cliToken;
    private final String displayName;

    VdStripSize(String cliToken, String displayName) {
        this.cliToken = cliToken;
        this.displayName = displayName;
    }

    public String cliToken() { return cliToken; }
    public String getDisplayName() { return displayName; }
    /** 폼의 selected · "(기본값)" 표기 · 배지(기본값과 다른 축 수)의 판정 재료 — 서버 · 뷰가 같은 상수를 본다. */
    public boolean isDefault() { return this == DEFAULT; }
}
