package com.example.serverprovision.global.redfish;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HF20 CP4 — 부트 항목 분류. 2호기(MD72-HB3 · F44) 실측 모양의 BootOptions 로 네 그룹과 이름표 정규화를 고정한다. */
class BootEntriesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    static final String OPTIONS = """
            {"Members":[
              {"Id":"0001","DisplayName":"UEFI: Built-in EFI Shell","UefiDevicePath":"VenMedia(7C04A583-9E3E-4F1C-AD65-E05268D0B4D1)"},
              {"@odata.id":"/redfish/v1/Systems/Self/BootOptions/0002","DisplayName":"Windows Boot Manager (LSI Logical Volume 3000, Partition 1)",
               "UefiDevicePath":"HD(1,GPT,3A2B6C1E-0000-0000-0000-000000000000,0x800,0x32000)/\\\\EFI\\\\Microsoft\\\\Boot\\\\bootmgfw.efi"},
              {"Id":"0003","DisplayName":"UEFI: PXE IPv4 Intel(R) I350 Gigabit Network Connection",
               "UefiDevicePath":"PciRoot(0x0)/Pci(0x1C,0x0)/Pci(0x0,0x0)/MAC(B42E99A0B1C2,0x1)/IPv4(0.0.0.0)"},
              {"Id":"0004","DisplayName":"UEFI: SanDisk Cruzer, Partition 1","UefiDevicePath":"PciRoot(0x0)/Pci(0x14,0x0)/USB(0x1,0x0)/HD(1,MBR,0x0,0x800,0x1000)"},
              {"Id":"0005","DisplayName":"Something Odd","UefiDevicePath":""},
              {"DisplayName":"이름표 없는 항목"}
            ]}
            """;

    @Test
    @DisplayName("VenMedia/EFI Shell → SHELL · HD( → DISK · MAC( → NETWORK · 그 밖 → OTHER · Id 없으면 @odata.id 꼬리, 둘 다 없으면 버린다")
    void classifiesAndNormalizes() {
        List<BootEntries.Entry> entries = BootEntries.parse(JSON.readTree(OPTIONS));

        assertThat(entries).extracting(BootEntries.Entry::id)
                .containsExactly("Boot0001", "Boot0002", "Boot0003", "Boot0004", "Boot0005");
        assertThat(entries).extracting(BootEntries.Entry::kind).containsExactly(
                BootEntries.Kind.SHELL, BootEntries.Kind.DISK, BootEntries.Kind.NETWORK, BootEntries.Kind.DISK, BootEntries.Kind.OTHER);
        assertThat(entries.get(1).isWindowsBootManager()).isTrue();
        assertThat(entries.get(3).isWindowsBootManager()).isFalse();   // USB 디스크는 DISK 지만 Windows 항목이 아니다
    }

    @Test
    @DisplayName("표시명만으로도 분류한다(장치 경로 없는 BMC) · null 전문은 빈 목록")
    void classifiesByNameOnly() {
        assertThat(BootEntries.classify("UEFI: Built-in EFI Shell", null)).isEqualTo(BootEntries.Kind.SHELL);
        assertThat(BootEntries.classify("Windows Boot Manager", "")).isEqualTo(BootEntries.Kind.DISK);
        assertThat(BootEntries.classify("UEFI: PXE IPv4 Intel", "")).isEqualTo(BootEntries.Kind.NETWORK);
        assertThat(BootEntries.classify("", "")).isEqualTo(BootEntries.Kind.OTHER);
        assertThat(BootEntries.parse(null)).isEmpty();
        assertThat(BootEntries.normalizeId("0007")).isEqualTo("Boot0007");
        assertThat(BootEntries.normalizeId("Boot0007")).isEqualTo("Boot0007");
    }
}
