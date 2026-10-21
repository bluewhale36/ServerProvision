package com.example.serverprovision.provisioning.setting.dto.response;

import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** R17-1 — 폼 재료(memberRules · spanMemberRules)가 서버의 같은 규칙으로 계산되어 실리는가. */
class SettingRaidCardOptionResponseTest {

    private static RaidCard card(RaidChipFamily family, int cacheGb, RaidLevel... levels) {
        RaidCard card = RaidCard.builder()
                .id(1L).vendor(RaidCardVendor.GIGABYTE).modelName("card")
                .supportedRaidLevels(SupportedRaidLevels.of(List.of(levels)))
                .cacheCapacity(CacheCapacity.ofGigabytes(cacheGb))
                .chipFamily(family)
                .ownEnabled(true).ownDeprecated(false).isDeleted(false)
                .build();
        card.recomputeEffective();
        return card;
    }

    @Test
    @DisplayName("MegaRAID — 지원 레벨마다 규칙 · RAID10 은 스팬 16개(2~32)마다 규칙")
    void megaRaid_carriesSpanRules() {
        var option = SettingRaidCardOptionResponse.of(
                card(RaidChipFamily.MEGARAID, 2, RaidLevel.RAID1, RaidLevel.RAID10), null);

        assertThat(option.memberRules()).containsOnlyKeys(RaidLevel.RAID1, RaidLevel.RAID10);
        assertThat(option.memberRules().get(RaidLevel.RAID1).display()).isEqualTo("2~32대 · 2대 단위");
        assertThat(option.spanMemberRules()).containsOnlyKeys(RaidLevel.RAID10);
        assertThat(option.spanMemberRules().get(RaidLevel.RAID10)).hasSize(16);
        var p4 = option.spanMemberRules().get(RaidLevel.RAID10).get(4);
        assertThat(p4.min()).isEqualTo(8);
        assertThat(p4.max()).isEqualTo(32);
        assertThat(p4.step()).isEqualTo(4);
    }

    @Test
    @DisplayName("MPT_IR — RAID10 은 3~10대 · 스팬 규칙 없음(폼은 스팬 줄을 내지 않는다)")
    void ir_noSpanRules() {
        var option = SettingRaidCardOptionResponse.of(
                card(RaidChipFamily.MPT_IR, 0, RaidLevel.RAID0, RaidLevel.RAID1, RaidLevel.RAID10), null);

        assertThat(option.memberRules().get(RaidLevel.RAID10).display()).isEqualTo("3~10대");
        assertThat(option.memberRules().get(RaidLevel.RAID0).min()).isEqualTo(2);
        assertThat(option.spanMemberRules()).isEmpty();
    }
}
