package com.example.serverprovision.execution.vo;

import java.util.List;

/**
 * 하드웨어 인벤토리 수집 계약(E1-2) — 요약 통계가 아니라 <b>슬롯 단위 인벤토리</b>다
 * (2026-07-19 사용자 확정 스펙). {@code guest_server_detail.hardware_spec} JSON 컬럼의 앱측 구조이며,
 * 직렬화는 Jackson 3({@code tools.jackson.*}) — 관용 원칙: 모르는 필드 무시 · 누락 필드 null.
 */
public record HardwareSpec(
        CpuInfo cpu,
        List<MemoryModule> memoryModules,
        List<DiskInfo> disks,
        List<PcieDevice> pcieDevices
) {

    /** CPU — 제조사 + 모델명만 수집한다(코어 수 등 불요 — 사용자 확정). */
    public record CpuInfo(String manufacturer, String model) {
    }

    /** 메모리 DIMM 슬롯 1개당 1행 — 몇 개 꽂혔는지는 행 수가 말한다. */
    public record MemoryModule(String slot, String manufacturer, String size) {
    }

    /** 디스크 1개 — SSD/HDD 구분 + 전송 방식(SAS/SATA/NVMe) + 용량. RAID 카드 뒤 물리 디스크는
     *  OS 불가시 — OPEN-1(벤더 CLI 동봉) 후속에서 확장(plan §2-2 한계 명기). */
    public record DiskInfo(String device, String type, String transport, String size) {
    }

    /**
     * PCIe 슬롯 장착물 1개. {@code kind} 분류(RAID/LAN/LAN_10G_UTP/LAN_10G_SFP/FC_16G/FC_32G/GPU/ETC)는
     * lspci 모델명 기반 규칙 — 미분류는 ETC + 원문(model)이 그대로 남아 수집 유실이 없다(T3 실측으로 보강).
     */
    public record PcieDevice(String slot, String kind, String vendor, String model) {
    }
}
