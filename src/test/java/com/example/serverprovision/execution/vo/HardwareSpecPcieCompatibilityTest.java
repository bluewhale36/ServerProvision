package com.example.serverprovision.execution.vo;

import com.example.serverprovision.execution.enums.PcieMount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** HF15-6 — 장착 구분 필드가 없는 구 저장본을 읽는 경로와 태깅본의 왕복. */
class HardwareSpecPcieCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("구 저장본(mount · slotDesignation 없음)은 UNKNOWN 으로 읽힌다")
    void legacyWithoutMountReadsAsUnknown() {
        HardwareSpec spec = objectMapper.readValue(
                "{\"pcieDevices\":[{\"slot\":\"ae:00.0\",\"kind\":\"RAID\",\"vendor\":\"Broadcom\",\"model\":\"SAS3008\"}]}",
                HardwareSpec.class);

        HardwareSpec.PcieDevice device = spec.pcieDevices().getFirst();
        assertThat(device.mount()).isEqualTo(PcieMount.UNKNOWN);
        assertThat(device.slotDesignation()).isNull();
        assertThat(device.model()).isEqualTo("SAS3008");
    }

    @Test
    @DisplayName("태깅본은 mount · slotDesignation 을 싣고 되읽으면 같다")
    void taggedRoundTrip() {
        HardwareSpec.PcieDevice tagged = HardwareSpec.PcieDevice.untagged("ae:00.0", "RAID", "Broadcom", "SAS3008")
                .tagged(PcieMount.ADD_IN, "PCIE_1");
        HardwareSpec spec = new HardwareSpec(null, null, null, java.util.List.of(tagged));

        String json = objectMapper.writeValueAsString(spec);
        HardwareSpec back = objectMapper.readValue(json, HardwareSpec.class);

        assertThat(json).contains("\"mount\":\"ADD_IN\"").contains("\"slotDesignation\":\"PCIE_1\"");
        assertThat(back.pcieDevices().getFirst()).isEqualTo(tagged);
        assertThat(HardwareSpec.PcieDevice.untagged("34:00.0", "LAN", "Intel", "I210").tagged(PcieMount.ONBOARD, "Onboard")
                .slotDesignation()).isNull();   // 온보드는 표기를 갖지 않는다
    }
}
