package com.example.serverprovision.management.raidcard.enums;

import com.example.serverprovision.management.raidcard.vo.DrivesPerSpan;
import com.example.serverprovision.management.raidcard.vo.MemberCountRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** R17-1 — 계열 × 레벨 디스크 수 규칙 표(plan §4 D3). */
class RaidChipFamilyMemberCountTest {

    @ParameterizedTest(name = "{0} {1} 캐시 {2} → {3}")
    @CsvSource({
            "MPT_IR, RAID0, false, 2~10대",
            "MPT_IR, RAID1, false, 2대",
            "MPT_IR, RAID10, false, 3~10대",
            "MEGARAID, RAID0, true, 1~32대",
            "MEGARAID, RAID0, false, 2~32대",
            "MEGARAID, RAID1, true, 2~32대 · 2대 단위",
            "MEGARAID, RAID5, true, 3~32대",
            "MEGARAID, RAID6, true, 4~32대",
            "MEGARAID, RAID10, true, 4~16대 · 2대 단위"
    })
    @DisplayName("규칙 표 — 스팬 미지정이면 MegaRAID RAID10 은 기본 스팬 2")
    void table(RaidChipFamily family, RaidLevel level, boolean cache, String display) {
        assertThat(family.memberCountRule(level, cache, null).display()).isEqualTo(display);
    }

    @Test
    @DisplayName("MegaRAID RAID10 은 스팬 2~8개 × 스팬당 p — 36대는 p=6 만 통과(p=2 → 18 스팬, p=4 → 9 스팬)")
    void megaRaidRaid10_spanBounds() {
        assertThat(rule(2).blockReasonFor(36)).isNotNull();
        assertThat(rule(4).blockReasonFor(36)).isNotNull();
        assertThat(rule(6).blockReasonFor(36)).isNull();
        assertThat(rule(4).blockReasonFor(6)).contains("8~32대 · 4대 단위");
    }

    @Test
    @DisplayName("MPT_IR RAID10 은 3대(홀수 · IR 방식)부터 — R17-1 이전 폼 하한 4 와의 불일치 해소")
    void irRaid10_acceptsThree() {
        assertThat(RaidChipFamily.MPT_IR.memberCountRule(RaidLevel.RAID10, false, null).blockReasonFor(3)).isNull();
    }

    @Test
    @DisplayName("스팬을 받는 것은 MegaRAID RAID10 뿐 — 지정 없음이면 기본 2, IR · 다른 레벨은 null")
    void spanOf() {
        assertThat(RaidChipFamily.MEGARAID.spanOf(RaidLevel.RAID10, null)).isEqualTo(DrivesPerSpan.DEFAULT);
        assertThat(RaidChipFamily.MEGARAID.spanOf(RaidLevel.RAID10, new DrivesPerSpan(4))).isEqualTo(new DrivesPerSpan(4));
        assertThat(RaidChipFamily.MEGARAID.spanOf(RaidLevel.RAID1, new DrivesPerSpan(4))).isNull();
        assertThat(RaidChipFamily.MPT_IR.spanOf(RaidLevel.RAID10, new DrivesPerSpan(4))).isNull();
        assertThat(RaidChipFamily.MEGARAID.acceptsSpan(RaidLevel.RAID10)).isTrue();
        assertThat(RaidChipFamily.MPT_IR.acceptsSpan(RaidLevel.RAID10)).isFalse();
    }

    private static MemberCountRule rule(int drivesPerSpan) {
        return RaidChipFamily.MEGARAID.memberCountRule(RaidLevel.RAID10, true, new DrivesPerSpan(drivesPerSpan));
    }
}
