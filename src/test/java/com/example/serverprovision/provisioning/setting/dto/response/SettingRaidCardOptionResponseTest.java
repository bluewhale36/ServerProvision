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
        assertThat(option.memberRules().get(RaidLevel.RAID1).display()).isEqualTo("2~32대, 2대 단위");
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

    @Test
    @DisplayName("R17-2 — 카드가 RAID50 · RAID60 을 지원해도 정의서 폼 재료(규칙 · 차단 사유)에는 실리지 않는다")
    void spannedLevels_notInFormMaterial() {
        var option = SettingRaidCardOptionResponse.of(
                card(RaidChipFamily.MEGARAID, 2, RaidLevel.RAID1, RaidLevel.RAID50, RaidLevel.RAID60), null);

        assertThat(option.memberRules()).containsOnlyKeys(RaidLevel.RAID1);
        assertThat(option.blockReasons().keySet()).allMatch(RaidLevel::isDefinable);
    }

    @Test
    @DisplayName("HF32 · C-23 — 옛 MPT_IR 카드가 RAID5 를 등록해 뒀어도 폼 재료는 칩 계열 교집합: 선택지 · 규칙에서 빠지고 차단 사유는 계열 문구")
    void legacyIrCard_uncreatableLevelExcluded() {
        var option = SettingRaidCardOptionResponse.of(
                card(RaidChipFamily.MPT_IR, 0, RaidLevel.RAID0, RaidLevel.RAID1, RaidLevel.RAID5), null);

        assertThat(option.supportedLevels()).containsExactly(RaidLevel.RAID0, RaidLevel.RAID1);
        assertThat(option.supportedLevelsDisplay()).isEqualTo("RAID0, RAID1");
        assertThat(option.memberRules()).containsOnlyKeys(RaidLevel.RAID0, RaidLevel.RAID1);
        assertThat(option.blockReasons().get(RaidLevel.RAID5))
                .isEqualTo("MPT IR (sas3ircu 계열)은 RAID0, RAID1, RAID10 만 만들 수 있습니다.");
        // 계열은 만들 수 있지만 카드가 등록하지 않은 RAID10 은 카드 문구 · 안내의 레벨 목록도 고를 수 있는 것만(RAID5 없음)
        assertThat(option.blockReasons().get(RaidLevel.RAID10))
                .isEqualTo("RAID10 을 만들 수 없는 카드입니다. 지원하는 RAID 레벨은 RAID0, RAID1 입니다.");
    }

    @Test
    @DisplayName("HF32 · D-6 — 볼륨 한계(maxVolumes)는 칩 계열 값이 실린다(폼 안내 재료, 저장은 막지 않는다)")
    void carriesMaxVolumes() {
        assertThat(SettingRaidCardOptionResponse.of(card(RaidChipFamily.MPT_IR, 0, RaidLevel.RAID1), null).maxVolumes())
                .isEqualTo(RaidChipFamily.MPT_IR.maxVolumes());
        assertThat(SettingRaidCardOptionResponse.of(card(RaidChipFamily.MEGARAID, 2, RaidLevel.RAID1), null).maxVolumes())
                .isEqualTo(RaidChipFamily.MEGARAID.maxVolumes());
    }
}
