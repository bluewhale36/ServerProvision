package com.example.serverprovision.execution.asset.spi;

/**
 * 영역 헤더 관측 chip 의 배지 severity — n-badge-* 배지 어휘의 단일 출처(SSOT). {@link AssetCondition#badgeClass()} 와
 * 같은 어휘를 쓰나 매핑축이 다르다(상태→색). 관측치는 okCount 집계용 healthy() 가 없어 {@link AssetCondition} 을
 * 구현하지 않는다.
 */
public enum ObservationSeverity {
    OK("n-badge-green"), INFO("n-badge-gray"), WARN("n-badge-orange"), CRITICAL("n-badge-red");

    private final String badgeClass;

    ObservationSeverity(String badgeClass) {
        this.badgeClass = badgeClass;
    }

    public String badgeClass() {
        return badgeClass;
    }
}
