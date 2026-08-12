package com.example.serverprovision.execution.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스펙 그룹 키 (U3-3 DEC-D) — 같은 구성을 같은 키로, 다른 구성을 다른 키로 본다.
 * 특히 <b>나열 순서와 슬롯 위치가 키를 바꾸지 않는 것</b>과 <b>CPU 소켓 수가 키를 가르는 것</b>이 핵심이다.
 */
class SpecGroupKeyTest {

    private static HardwareSpec.CpuSocket cpu(String slot, String model) {
        return new HardwareSpec.CpuSocket(slot, "Intel", model);
    }

    private static HardwareSpec.MemoryModule mem(String slot, String size) {
        return new HardwareSpec.MemoryModule(slot, "Samsung", size);
    }

    private static HardwareSpec.PcieDevice pcie(String slot, String model) {
        return new HardwareSpec.PcieDevice(slot, "RAID", "Broadcom", model);
    }

    private static HardwareSpec.DiskInfo disk(String device, String size) {
        return new HardwareSpec.DiskInfo(device, "SSD", "nvme", size);
    }

    @Test
    @DisplayName("나열 순서가 달라도 구성이 같으면 같은 키다 — 다중집합 비교")
    void sameCompositionDifferentOrderProducesSameKey() {
        HardwareSpec a = new HardwareSpec(
                List.of(cpu("CPU1", "6338"), cpu("CPU2", "6338")),
                List.of(mem("A1", "32 GB"), mem("A2", "64 GB")),
                List.of(disk("nvme0n1", "1.9T"), disk("nvme1n1", "3.8T")),
                List.of(pcie("01:00.0", "9560-8i"), pcie("02:00.0", "X710")));
        HardwareSpec b = new HardwareSpec(
                List.of(cpu("CPU2", "6338"), cpu("CPU1", "6338")),
                List.of(mem("B2", "64 GB"), mem("B1", "32 GB")),
                List.of(disk("nvme1n1", "3.8T"), disk("nvme0n1", "1.9T")),
                List.of(pcie("02:00.0", "X710"), pcie("01:00.0", "9560-8i")));

        assertThat(SpecGroupKey.of("MS03-CE0", a)).isEqualTo(SpecGroupKey.of("MS03-CE0", b));
    }

    @Test
    @DisplayName("CPU 소켓 수만 달라도 다른 키다 — DEC-C 가 필요했던 이유")
    void socketCountSplitsGroup() {
        HardwareSpec one = new HardwareSpec(List.of(cpu("CPU1", "6338")), null, null, null);
        HardwareSpec two = new HardwareSpec(List.of(cpu("CPU1", "6338"), cpu("CPU2", "6338")), null, null, null);

        assertThat(SpecGroupKey.of("MS03-CE0", one)).isNotEqualTo(SpecGroupKey.of("MS03-CE0", two));
    }

    @Test
    @DisplayName("보드 모델이 다르면 나머지가 같아도 다른 키다")
    void boardModelSplitsGroup() {
        HardwareSpec spec = new HardwareSpec(List.of(cpu("CPU1", "6338")), null, null, null);

        assertThat(SpecGroupKey.of("MS03-CE0", spec)).isNotEqualTo(SpecGroupKey.of("MZ32-AR0", spec));
    }

    @Test
    @DisplayName("메모리 용량 구성이 다르면 다른 키다 — 개수는 행 수로 센다")
    void memoryCompositionSplitsGroup() {
        HardwareSpec four = new HardwareSpec(null,
                List.of(mem("A1", "32 GB"), mem("A2", "32 GB"), mem("A3", "32 GB"), mem("A4", "32 GB")),
                null, null);
        HardwareSpec two = new HardwareSpec(null,
                List.of(mem("A1", "32 GB"), mem("A2", "32 GB")), null, null);

        assertThat(SpecGroupKey.of("MS03-CE0", four)).isNotEqualTo(SpecGroupKey.of("MS03-CE0", two));
    }

    @Test
    @DisplayName("일부 축이 비어도 키를 만든다 — 파싱 실패 서버가 그룹 조립을 깨뜨리지 않는다")
    void partiallyMissingSpecStillProducesKey() {
        assertThat(SpecGroupKey.of("MS03-CE0", null)).isNotNull();
        assertThat(SpecGroupKey.of(null, new HardwareSpec(null, null, null, null))).isNotNull();
        // 같은 결측 상태끼리는 한 그룹으로 묶인다 — "알 수 없음" 도 하나의 구성이다
        assertThat(SpecGroupKey.of("MS03-CE0", null)).isEqualTo(SpecGroupKey.of("MS03-CE0", null));
    }
}
