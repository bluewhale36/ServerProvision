package com.example.serverprovision.management.raidcard.entity;

import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** HF32 · C-23 — 정의서가 고를 수 있는 레벨 = 카드 등록 정보 ∩ 칩 계열 creatableLevels. 폼 잠금과 저장 가드가 같은 메서드를 부른다. */
class RaidCardDefinableLevelsTest {

    static RaidCard card(RaidChipFamily family, RaidLevel... levels) {
        RaidCard card = RaidCard.builder()
                .id(1L).vendor(RaidCardVendor.GIGABYTE).modelName("card")
                .supportedRaidLevels(SupportedRaidLevels.of(List.of(levels)))
                .cacheCapacity(CacheCapacity.NONE).chipFamily(family)
                .ownEnabled(true).ownDeprecated(false).isDeleted(false)
                .build();
        card.recomputeEffective();
        return card;
    }

    @Test
    @DisplayName("정상 카드 — 등록 레벨이 전부 계열 안이면 definableLevels = 등록 정보 · 주의 대상 없음 · 등록 레벨은 통과")
    void consistentCard() {
        RaidCard ir = card(RaidChipFamily.MPT_IR, RaidLevel.RAID0, RaidLevel.RAID1);

        assertThat(ir.definableLevels()).containsExactly(RaidLevel.RAID0, RaidLevel.RAID1);
        assertThat(ir.uncreatableRegisteredLevels()).isEmpty();
        assertThat(ir.definableLevelBlockReason(RaidLevel.RAID1)).isNull();
    }

    @Test
    @DisplayName("R17-2 이전 MPT IR 카드(RAID5 등록) — RAID5 는 계열 문구로 막히고 선택지 · 카드 문구의 레벨 목록에서 빠진다")
    void legacyIrCard() {
        RaidCard legacy = card(RaidChipFamily.MPT_IR, RaidLevel.RAID0, RaidLevel.RAID1, RaidLevel.RAID5);

        assertThat(legacy.definableLevels()).containsExactly(RaidLevel.RAID0, RaidLevel.RAID1);
        assertThat(legacy.uncreatableRegisteredLevels()).containsExactly(RaidLevel.RAID5);
        assertThat(legacy.definableLevelBlockReason(RaidLevel.RAID5))
                .isEqualTo("MPT IR (sas3ircu 계열)은 RAID0, RAID1, RAID10 만 만들 수 있습니다.");
        // 계열은 만들 수 있지만 등록하지 않은 RAID10 — 카드 문구, 보이는 레벨은 고를 수 있는 것만
        assertThat(legacy.definableLevelBlockReason(RaidLevel.RAID10))
                .isEqualTo("RAID10 을 만들 수 없는 카드입니다. 지원하는 RAID 레벨은 RAID0, RAID1 입니다.");
    }

    @Test
    @DisplayName("계열이 못 만드는 레벨만 등록한 옛 카드 — definableLevels 는 빈 집합(SupportedRaidLevels 불변식에 걸리지 않는다)")
    void onlyUncreatableLevels() {
        RaidCard odd = card(RaidChipFamily.MPT_IR, RaidLevel.RAID5, RaidLevel.RAID6);

        assertThat(odd.definableLevels()).isEmpty();
        assertThat(odd.uncreatableRegisteredLevels()).containsExactlyInAnyOrder(RaidLevel.RAID5, RaidLevel.RAID6);
        assertThat(odd.definableLevelBlockReason(RaidLevel.RAID1)).contains("지원하는 RAID 레벨은 - 입니다.");
    }
}
