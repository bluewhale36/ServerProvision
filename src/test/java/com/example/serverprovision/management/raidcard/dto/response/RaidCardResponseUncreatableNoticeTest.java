package com.example.serverprovision.management.raidcard.dto.response;

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

/** HF32 · C-23 — 카드 목록의 주의 안내: 등록 정보에 계열이 못 만드는 레벨이 남은 카드만 문구가 실린다. */
class RaidCardResponseUncreatableNoticeTest {

    private static RaidCard card(RaidChipFamily family, RaidLevel... levels) {
        RaidCard card = RaidCard.builder()
                .id(1L).vendor(RaidCardVendor.GIGABYTE).modelName("CRA3338")
                .supportedRaidLevels(SupportedRaidLevels.of(List.of(levels)))
                .cacheCapacity(CacheCapacity.NONE).chipFamily(family)
                .ownEnabled(true).ownDeprecated(false).isDeleted(false)
                .build();
        card.recomputeEffective();
        return card;
    }

    @Test
    @DisplayName("옛 MPT IR 카드(RAID5 · RAID6 등록) → 레벨을 이름 짓는 안내 한 문장 · 정상 카드 → null")
    void notice() {
        assertThat(RaidCardResponse.of(card(RaidChipFamily.MPT_IR, RaidLevel.RAID1, RaidLevel.RAID5, RaidLevel.RAID6))
                .uncreatableLevelsNotice())
                .isEqualTo("등록 정보의 RAID5, RAID6: 이 칩 계열이 만들 수 없어 정의서에서 고를 수 없습니다. 수정 화면에서 정리할 수 있습니다.");
        assertThat(RaidCardResponse.of(card(RaidChipFamily.MEGARAID, RaidLevel.RAID1, RaidLevel.RAID5))
                .uncreatableLevelsNotice()).isNull();
    }
}
