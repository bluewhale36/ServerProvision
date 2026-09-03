package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3.5-5-a D4 — 카드 대조 순수 판정의 진리표 전수. 집행(cardMatches)과 화면(예고)이 이 표 하나를 본다.
 */
class RaidCardMatchTest {

    private static RaidInventory inventoryOf(String subsystem) {
        return new RaidInventory(new DetectedRaidCard(RaidChipFamily.MEGARAID, subsystem, "9361-8i", "fw"),
                List.of(), List.of());
    }

    private static final RaidConfigurationTarget SPECIFIED = new RaidConfigurationTarget(7L, "1000:9361", "9361-8i");

    @Test
    @DisplayName("지정 카드 없음(할당 없음 · 카드 미지정) → NOT_APPLICABLE")
    void notApplicable() {
        assertThat(RaidCardMatch.judge(null, inventoryOf("1000:9361"))).isEqualTo(RaidCardMatchVerdict.NOT_APPLICABLE);
        assertThat(RaidCardMatch.judge(new RaidConfigurationTarget(null, null, null), inventoryOf("1000:9361")))
                .isEqualTo(RaidCardMatchVerdict.NOT_APPLICABLE);
    }

    @Test
    @DisplayName("자원 Subsystem 미확정 → UNVERIFIABLE (관측이 있어도 정본이 없다)")
    void unverifiable() {
        assertThat(RaidCardMatch.judge(new RaidConfigurationTarget(7L, null, "9361-8i"), inventoryOf("1000:9361")))
                .isEqualTo(RaidCardMatchVerdict.UNVERIFIABLE);
    }

    @Test
    @DisplayName("관측 카드 없음(인벤토리 null · card null · Subsystem null) → NOT_DETECTED")
    void notDetected() {
        assertThat(RaidCardMatch.judge(SPECIFIED, null)).isEqualTo(RaidCardMatchVerdict.NOT_DETECTED);
        assertThat(RaidCardMatch.judge(SPECIFIED, new RaidInventory(null, List.of(), List.of())))
                .isEqualTo(RaidCardMatchVerdict.NOT_DETECTED);
        assertThat(RaidCardMatch.judge(SPECIFIED, inventoryOf(null))).isEqualTo(RaidCardMatchVerdict.NOT_DETECTED);
    }

    @Test
    @DisplayName("Subsystem 다름 → MISMATCH · 같음(대소문자 무시) → MATCH")
    void mismatchAndMatch() {
        assertThat(RaidCardMatch.judge(SPECIFIED, inventoryOf("1458:3008"))).isEqualTo(RaidCardMatchVerdict.MISMATCH);
        assertThat(RaidCardMatch.judge(SPECIFIED, inventoryOf("1000:9361"))).isEqualTo(RaidCardMatchVerdict.MATCH);
        assertThat(RaidCardMatch.judge(SPECIFIED, inventoryOf("1000:9361".toUpperCase()))).isEqualTo(RaidCardMatchVerdict.MATCH);
    }
}
