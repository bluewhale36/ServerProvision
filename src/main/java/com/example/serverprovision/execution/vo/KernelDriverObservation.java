package com.example.serverprovision.execution.vo;

/**
 * 진단 리눅스가 RAID 후보 장치에서 관측한 커널 드라이버 바인딩(R17) — {@code /sys/bus/pci/devices/<slot>/driver} 의 이름.
 * 이 값 자체가 없으면(PcieDevice 의 필드가 null) 관측하지 않은 옛 보고이고, 이 값의 {@code driver} 가 비어 있으면
 * 관측했지만 커널이 아무 드라이버도 붙이지 않은 것이다 — 두 경우는 화면 안내가 다르므로 구분해 담는다.
 */
public record KernelDriverObservation(String driver) {

    public KernelDriverObservation {
        driver = (driver == null || driver.isBlank()) ? null : driver.trim();
    }

    /** 커널이 어떤 드라이버든 붙였는가. */
    public boolean isBound() {
        return driver != null;
    }
}
