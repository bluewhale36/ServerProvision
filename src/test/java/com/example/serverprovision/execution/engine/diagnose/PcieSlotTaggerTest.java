package com.example.serverprovision.execution.engine.diagnose;

import com.example.serverprovision.execution.enums.PcieMount;
import com.example.serverprovision.execution.vo.HardwareSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HF15-6 D-4 — lspci 버스 · 장치 번호와 BMC pci_info 의 대응. 표본은 2026-09-10 HAR(일산 상2 · MS04-CE0). */
class PcieSlotTaggerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** HAR 정본 — SAS3008 은 PCIE_1 슬롯(iOnBoard 0), I210 두 개는 온보드(iOnBoard 1). */
    static final String PCI_INFO = """
            [
             {"hexHandle":"0x0033","strClass":"Serial Attached SCSI controller","hexDeviceID":"0x0097","hexVendorID":"0x1000",
              "strProductName":"SAS3008 PCI-Express Fusion-MPT SAS-3","iOnBoard":0,"hexSlotHandle":"0x002A","hexOnboardIndex":"0xFFFF",
              "hexBusNumber":"0x00AE","hexDeviceNum":"0x0000","strSlotDesignation":"PCIE_1"},
             {"hexHandle":"0x0034","strClass":"Ethernet controller","hexDeviceID":"0x1533","hexVendorID":"0x8086",
              "strProductName":"I210 Gigabit Network Connection","iOnBoard":1,"strSlotDesignation":"Onboard",
              "hexBusNumber":"0x0034","hexDeviceNum":"0x0000"},
             {"hexHandle":"0x0035","strClass":"Ethernet controller","hexDeviceID":"0x1533","hexVendorID":"0x8086",
              "strProductName":"I210 Gigabit Network Connection","iOnBoard":1,"strSlotDesignation":"Onboard",
              "hexBusNumber":"0x0035","hexDeviceNum":"0x0000"},
             {"hexHandle":"0x0037","strClass":"VGA compatible controller","hexVendorID":"0x1A03","iOnBoard":1,
              "strSlotDesignation":"Onboard","hexBusNumber":"0x0037","hexDeviceNum":"0x0000"}
            ]""";

    private static HardwareSpec.PcieDevice dev(String slot, String kind, String model) {
        return HardwareSpec.PcieDevice.untagged(slot, kind, "v", model);
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    @Test
    @DisplayName("버스 · 장치 번호가 맞으면 iOnBoard 로 온보드 · 추가 장착을 가르고, 추가 장착만 슬롯 표기를 갖는다")
    void tagsByBusAndDevice() {
        List<HardwareSpec.PcieDevice> tagged = PcieSlotTagger.tag(List.of(
                dev("ae:00.0", "RAID", "SAS3008"), dev("34:00.0", "LAN", "I210"), dev("35:00.0", "LAN", "I210")), json(PCI_INFO));

        assertThat(tagged).extracting(HardwareSpec.PcieDevice::mount)
                .containsExactly(PcieMount.ADD_IN, PcieMount.ONBOARD, PcieMount.ONBOARD);
        assertThat(tagged.getFirst().slotDesignation()).isEqualTo("PCIE_1");
        assertThat(tagged.get(1).slotDesignation()).isNull();
        assertThat(tagged.get(1).model()).isEqualTo("I210");   // 나머지 필드는 그대로
    }

    @Test
    @DisplayName("짝이 없는 장치는 UNKNOWN 으로 남고 순서는 보존된다 · 도메인 접두(0000:) 슬롯도 읽는다 · 다중 기능은 같은 태그")
    void unmatchedStaysUnknown_domainPrefix_multiFunction() {
        List<HardwareSpec.PcieDevice> tagged = PcieSlotTagger.tag(List.of(
                dev("01:00.0", "LAN_10G_SFP", "X710"), dev("0000:ae:00.0", "RAID", "SAS3008"),
                dev("34:00.1", "LAN", "I210 port2"), dev(null, "ETC", "raw line")), json(PCI_INFO));

        assertThat(tagged).extracting(HardwareSpec.PcieDevice::mount)
                .containsExactly(PcieMount.UNKNOWN, PcieMount.ADD_IN, PcieMount.ONBOARD, PcieMount.UNKNOWN);
        assertThat(tagged.get(1).slotDesignation()).isEqualTo("PCIE_1");
    }

    @Test
    @DisplayName("iOnBoard 가 없어도 strSlotDesignation=Onboard 면 온보드 · 표기가 비면 추가 장착의 표기는 null")
    void designationFallbacks() {
        String info = """
                [{"hexBusNumber":"0x0034","hexDeviceNum":"0x0000","strSlotDesignation":"onboard"},
                 {"hexBusNumber":"0x00AE","hexDeviceNum":"0x0000","iOnBoard":0,"strSlotDesignation":" "}]""";
        List<HardwareSpec.PcieDevice> tagged = PcieSlotTagger.tag(List.of(dev("34:00.0", "LAN", "I210"), dev("ae:00.0", "RAID", "SAS")), json(info));

        assertThat(tagged.get(0).mount()).isEqualTo(PcieMount.ONBOARD);
        assertThat(tagged.get(1).mount()).isEqualTo(PcieMount.ADD_IN);
        assertThat(tagged.get(1).slotDesignation()).isNull();
    }

    @Test
    @DisplayName("미준비 판정 — 배열이 아니거나 비어 있으면 아직 인벤토리가 없다(재시도 대상)")
    void readiness() {
        assertThat(PcieSlotTagger.looksReady(json("[]"))).isFalse();
        assertThat(PcieSlotTagger.looksReady(json("{\"error\":\"x\",\"code\":1}"))).isFalse();
        assertThat(PcieSlotTagger.looksReady(null)).isFalse();
        assertThat(PcieSlotTagger.looksReady(json(PCI_INFO))).isTrue();
    }

    @Test
    @DisplayName("깨진 항목(버스 번호 없음 · hex 아님)은 무시하고 나머지로 태깅한다")
    void malformedEntriesIgnored() {
        String info = """
                [{"strSlotDesignation":"PCIE_2"}, {"hexBusNumber":"zz","hexDeviceNum":"0x0"},
                 {"hexBusNumber":"0x00AE","hexDeviceNum":"0x0000","iOnBoard":0,"strSlotDesignation":"PCIE_1"}]""";
        List<HardwareSpec.PcieDevice> tagged = PcieSlotTagger.tag(List.of(dev("ae:00.0", "RAID", "SAS")), json(info));

        assertThat(tagged.getFirst().mount()).isEqualTo(PcieMount.ADD_IN);
        assertThat(tagged.getFirst().slotDesignation()).isEqualTo("PCIE_1");
    }
}
