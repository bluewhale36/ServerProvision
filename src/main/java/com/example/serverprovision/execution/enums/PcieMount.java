package com.example.serverprovision.execution.enums;

/**
 * PCIe 장치의 장착 구분(HF15-6) — BMC 시스템 인벤토리({@code pci_info} 의 {@code iOnBoard} · {@code strSlotDesignation})로
 * 가른다. 진단 리눅스의 lspci 만으로는 온보드 NIC 와 추가 장착 카드가 같은 클래스로 섞인다(실기 4호).
 * 구 저장본과 BMC 조회 전 · 실패는 UNKNOWN — 그룹 키는 UNKNOWN 을 종전대로 세고 ONBOARD 만 뺀다.
 */
public enum PcieMount {
    ONBOARD("온보드"),
    ADD_IN("추가 장착"),
    UNKNOWN("미확인");

    private final String label;

    PcieMount(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
