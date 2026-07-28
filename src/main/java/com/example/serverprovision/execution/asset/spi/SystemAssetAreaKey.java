package com.example.serverprovision.execution.asset.spi;

/**
 * 통합 시스템 자산 대시보드가 집계하는 자산 영역의 라우팅 키. 각 상수는 {@link SystemAssetArea} 구현
 * 하나에 대응하며, 영역 단위 봉인 URL({@code /system/asset/{area}/seal})의 경로 변수로 노출된다.
 *
 * <p>기존 {@code global.marker.ResourceType} 을 재사용하지 않는다 — 그 enum 은 reconciliation·orphan
 * 스윕 대상 자원(OS/BIOS/BMC/Subprogram)의 종류를 표현하며, 진단 자산·TFTP 영역은 의도적으로 그 스윕에서
 * 격리돼 있다(자체 verify 만 갖는다). 스윕 대상 enum 에 비대상 키를 섞으면 라우팅·격리 관계가 다시 얽힌다.</p>
 */
public enum SystemAssetAreaKey {

    /** 진단 리눅스 부팅 자산 6종(커널·initramfs·modloop·apkovl·repo·agent). */
    DIAGNOSTIC,

    /** TFTP/PXE 부팅 인프라 자산. */
    TFTP,

    /** dhcpd(DHCP 서버) PXE 인프라 관측 영역. 파일 봉인이 아닌 존재·문법·서비스 상태·임대로 판정. */
    DHCPD
}
