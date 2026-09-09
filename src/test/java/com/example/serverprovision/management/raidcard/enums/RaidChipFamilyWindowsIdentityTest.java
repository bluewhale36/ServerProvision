package com.example.serverprovision.management.raidcard.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/** HF15-5 — 계열별 OS 식별자 변환(F-7)과 디스크 순서 규칙(F-6). 표본은 실기 3호(2026-09-08) 실측값. */
class RaidChipFamilyWindowsIdentityTest {

    @Test
    @DisplayName("MPT_IR · wwid 8바이트 → 600508e000000000 + 바이트 뒤집기 = Windows UniqueId(일산 상3 · 상1 · 상2 3/3)")
    void mptIr_uniqueId_isReversedWwidWithNaaPrefix() {
        assertThat(RaidChipFamily.MPT_IR.windowsUniqueIdOf("00a8209837f72f2e")).isEqualTo("600508e0000000002e2ff7379820a800");
        assertThat(RaidChipFamily.MPT_IR.windowsUniqueIdOf("023ff9af195f01b1")).isEqualTo("600508e000000000b1015f19aff93f02");
        assertThat(RaidChipFamily.MPT_IR.windowsUniqueIdOf("00F16993F80E962D")).isEqualTo("600508e0000000002d960ef89369f100");
    }

    @Test
    @DisplayName("MPT_IR · 이미 32자 NAA(lsblk WWN · 0x 접두) 는 정규화만 · 길이가 맞지 않으면 null")
    void mptIr_uniqueId_passthroughAndReject() {
        assertThat(RaidChipFamily.MPT_IR.windowsUniqueIdOf("0x600508E0000000002E2FF7379820A800")).isEqualTo("600508e0000000002e2ff7379820a800");
        assertThat(RaidChipFamily.MPT_IR.windowsUniqueIdOf("abc")).isNull();
        assertThat(RaidChipFamily.MPT_IR.windowsUniqueIdOf(null)).isNull();
        assertThat(RaidChipFamily.MPT_IR.windowsUniqueIdOf("  ")).isNull();
    }

    @Test
    @DisplayName("MEGARAID · SCSI NAA Id 가 곧 UniqueId — 소문자 · 0x 제거만")
    void megaraid_uniqueId_isNormalizedWwn() {
        assertThat(RaidChipFamily.MEGARAID.windowsUniqueIdOf("0x600605B00D18AA1E3233141A0C762E2D")).isEqualTo("600605b00d18aa1e3233141a0c762e2d");
        assertThat(RaidChipFamily.MEGARAID.windowsUniqueIdOf(null)).isNull();
    }

    @Test
    @DisplayName("MPT_IR · 순서 규칙 = 볼륨 ID 내림차순(만든 순) — [322, 323] → [323, 322] · 숫자 아닌 ID 는 뒤")
    void mptIr_order_isIdDescending() {
        List<String> ordered = RaidChipFamily.MPT_IR.windowsDiskOrder(List.of("322", "323", "x"), Function.identity());
        assertThat(ordered).containsExactly("323", "322", "x");
    }

    @Test
    @DisplayName("MEGARAID · 순서 규칙 = 나열 순 그대로")
    void megaraid_order_isListing() {
        assertThat(RaidChipFamily.MEGARAID.windowsDiskOrder(List.of("VD0", "VD1"), Function.identity()))
                .containsExactly("VD0", "VD1");
    }
}
