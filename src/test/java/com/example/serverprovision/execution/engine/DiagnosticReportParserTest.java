package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.vo.HardwareSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E1-2 CP4 — 수집 보고 관용 파서 계약: 슬롯 단위 인벤토리(사용자 확정 스펙) · placeholder 필터(V8 대비) ·
 * PCIe 종류 분류 규칙 · 부분/비정형 입력의 흡수. 이 파서가 agent.sh 보고 JSON 의 서버측 해석 SSOT 다.
 */
class DiagnosticReportParserTest {

    private final DiagnosticReportParser parser = new DiagnosticReportParser(new ObjectMapper());

    private static final String FULL = """
            { "boardSerial": "JG4P6400027", "biosVersion": "F13",
              "cpu": {"manufacturer": "Intel", "model": "Xeon Gold 6338"},
              "memoryModules": [
                {"slot": "DIMM_A1", "manufacturer": "Samsung", "size": "32 GB"},
                {"slot": "DIMM_B1", "manufacturer": "Samsung", "size": "32 GB"}],
              "disks": [
                {"device": "nvme0n1", "size": "1.9T", "rota": "0", "tran": "nvme"},
                {"device": "sda", "size": "8T", "rota": "1", "tran": "sas"}],
              "pcieRaw": [
                "01:00.0 RAID bus controller: Broadcom / LSI MegaRAID SAS-3 9560-8i",
                "02:00.0 Ethernet controller: Intel Corporation Ethernet Controller X710 for 10GbE SFP+",
                "03:00.0 Ethernet controller: Intel Corporation Ethernet Controller 10G X550T",
                "04:00.0 Fibre Channel: QLogic Corp. QLE2692 16Gb FC Adapter",
                "05:00.0 3D controller: NVIDIA Corporation GA100 [A100]"],
              "bmc": {"ip": "192.168.0.201", "mac": "b4:2e:99:aa:bb:cc"} }
            """;

    @Test
    @DisplayName("정상 전체 보고 — 슬롯 단위 인벤토리 + BMC 신원 구조화")
    void full_parsesEverything() {
        var parsed = parser.parse(FULL);

        assertThat(parsed.boardSerial()).isEqualTo("JG4P6400027");
        assertThat(parsed.softwareSpec().biosVersion()).isEqualTo("F13");
        assertThat(parsed.hardwareSpec().cpu().manufacturer()).isEqualTo("Intel");
        assertThat(parsed.hardwareSpec().memoryModules()).hasSize(2)
                .first().extracting(HardwareSpec.MemoryModule::slot).isEqualTo("DIMM_A1");
        assertThat(parsed.hardwareSpec().disks()).extracting(HardwareSpec.DiskInfo::type)
                .containsExactly("SSD", "HDD");                    // rota 0/1 → SSD/HDD
        assertThat(parsed.hardwareSpec().disks()).extracting(HardwareSpec.DiskInfo::transport)
                .containsExactly("NVME", "SAS");
        assertThat(parsed.bmcIp().value()).isEqualTo("192.168.0.201");
        assertThat(parsed.bmcMac()).isNotNull();
        assertThat(parsed.placeholderFiltered()).isEmpty();
    }

    @Test
    @DisplayName("PCIe 종류 분류 — RAID / 10G SFP+ / 10G UTP / FC 16Gb / GPU (사용자 확정 축)")
    void pcie_kindClassification() {
        var kinds = parser.parse(FULL).hardwareSpec().pcieDevices()
                .stream().map(HardwareSpec.PcieDevice::kind).toList();
        assertThat(kinds).containsExactly("RAID", "LAN_10G_SFP", "LAN_10G_UTP", "FC_16G", "GPU");
    }

    @Test
    @DisplayName("placeholder 필터 — 'To Be Filled By O.E.M.' 시리얼은 null (UNIQUE 충돌 차단, V8 대비)")
    void placeholder_serialFiltered() {
        var parsed = parser.parse("""
                { "boardSerial": "To Be Filled By O.E.M.", "biosVersion": "Default string" }
                """);
        assertThat(parsed.boardSerial()).isNull();
        assertThat(parsed.softwareSpec().biosVersion()).isNull();
        assertThat(parsed.placeholderFiltered())
                .containsExactly("boardSerial=To Be Filled By O.E.M.", "biosVersion=Default string");
    }

    @Test
    @DisplayName("부분 보고(관용) — 누락 축은 null/빈 목록, 있는 것만 구조화")
    void partial_isTolerated() {
        var parsed = parser.parse("{ \"biosVersion\": \"F14\" }");
        assertThat(parsed.boardSerial()).isNull();
        assertThat(parsed.softwareSpec().biosVersion()).isEqualTo("F14");
        assertThat(parsed.hardwareSpec().cpu()).isNull();
        assertThat(parsed.hardwareSpec().memoryModules()).isEmpty();
        assertThat(parsed.bmcIp()).isNull();
    }

    @Test
    @DisplayName("BMC 값 불량(관용) — IP 형식 오류는 null 로 흡수, 나머지는 유지")
    void bmc_invalidValues_tolerated() {
        var parsed = parser.parse("""
                { "bmc": {"ip": "not-an-ip", "mac": "b4:2e:99:aa:bb:cc"} }
                """);
        assertThat(parsed.bmcIp()).isNull();
        assertThat(parsed.bmcMac()).isNotNull();
    }

    @Test
    @DisplayName("JSON 아님 — ReportUnparsable (호출자가 '적재 없이 close 성공' 으로 처리하는 경계 신호)")
    void notJson_throwsUnparsable() {
        assertThatThrownBy(() -> parser.parse("plain text, not json"))
                .isInstanceOf(DiagnosticReportParser.ReportUnparsableException.class);
        assertThatThrownBy(() -> parser.parse("[1,2,3]"))
                .isInstanceOf(DiagnosticReportParser.ReportUnparsableException.class);
    }
}
