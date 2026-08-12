package com.example.serverprovision.execution.vo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 하드웨어 인벤토리 수집 계약(E1-2) — 요약 통계가 아니라 <b>슬롯 단위 인벤토리</b>다
 * (2026-07-19 사용자 확정 스펙). {@code guest_server_detail.hardware_spec} JSON 컬럼의 앱측 구조이며,
 * 직렬화는 Jackson 3({@code tools.jackson.*}) — 관용 원칙: 모르는 필드 무시 · 누락 필드 null.
 *
 * <p><b>U3-3 DEC-C</b> — CPU 도 메모리 · 디스크 · PCIe 와 같은 슬롯 단위로 맞췄다. 소켓이 몇 개인지는
 * 별도 count 필드가 아니라 {@link #cpuSockets} 의 <b>행 수가 말한다</b>. 스펙 그룹이 "같은 모델 1소켓" 과
 * "같은 모델 2소켓" 을 갈라야 하는데, 단수 레코드로는 그 구분을 표현할 수 없었다.</p>
 */
public record HardwareSpec(
        List<CpuSocket> cpuSockets,
        List<MemoryModule> memoryModules,
        List<DiskInfo> disks,
        List<PcieDevice> pcieDevices
) {
    /**
     * 저장된 JSON 을 읽을 때 <b>신 · 구 두 형식을 모두 받는다</b>(DEC-C 하위 호환).
     * DEC-C 이전에 적재된 행은 {@code "cpu": {...}} 단수 객체이므로 1소켓 리스트로 승급해 읽는다.
     * 덕분에 데이터 마이그레이션 없이 넘어가며, 해당 서버가 재수집되면 실제 소켓 수로 갱신된다.
     */
    @JsonCreator
    static HardwareSpec fromJson(
            @JsonProperty("cpuSockets") List<CpuSocket> cpuSockets,
            @JsonProperty("cpu") CpuSocket legacyCpu,
            @JsonProperty("memoryModules") List<MemoryModule> memoryModules,
            @JsonProperty("disks") List<DiskInfo> disks,
            @JsonProperty("pcieDevices") List<PcieDevice> pcieDevices) {

        List<CpuSocket> sockets = (cpuSockets != null && !cpuSockets.isEmpty())
                ? cpuSockets
                : (legacyCpu != null ? List.of(legacyCpu) : null);
        return new HardwareSpec(sockets, memoryModules, disks, pcieDevices);
    }

    /** CPU 소켓 1개 — 제조사 + 모델명만 수집한다(코어 수 등 불요). {@code slot} 은 수집원이 알려주면 채운다. */
    public record CpuSocket(String slot, String manufacturer, String model) {
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
