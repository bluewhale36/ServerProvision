package com.example.serverprovision.management.raidcard.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** R17-2 — 계열이 만들 수 있는 레벨(카드 등록 잠금 · 요청 검증의 SSOT)과 정의서가 고를 수 있는 레벨. */
class RaidChipFamilyCreatableLevelsTest {

    @ParameterizedTest(name = "{0} {1} → {2}")
    @CsvSource({
            "MPT_IR, RAID0, true", "MPT_IR, RAID1, true", "MPT_IR, RAID5, false", "MPT_IR, RAID6, false",
            "MPT_IR, RAID10, true", "MPT_IR, RAID00, false", "MPT_IR, RAID50, false", "MPT_IR, RAID60, false",
            "MEGARAID, RAID0, true", "MEGARAID, RAID1, true", "MEGARAID, RAID5, true", "MEGARAID, RAID6, true",
            "MEGARAID, RAID10, true", "MEGARAID, RAID00, true", "MEGARAID, RAID50, true", "MEGARAID, RAID60, true"
    })
    @DisplayName("진리표 — IR 은 RAID0 · 1 · 10, MegaRAID 는 전부")
    void truthTable(RaidChipFamily family, RaidLevel level, boolean creatable) {
        assertThat(family.canCreate(level)).isEqualTo(creatable);
    }

    @Test
    @DisplayName("canCreateAll — 하나라도 못 만들면 거짓 · 빈 목록은 참")
    void canCreateAll() {
        assertThat(RaidChipFamily.MPT_IR.canCreateAll(List.of(RaidLevel.RAID0, RaidLevel.RAID10))).isTrue();
        assertThat(RaidChipFamily.MPT_IR.canCreateAll(List.of(RaidLevel.RAID0, RaidLevel.RAID5))).isFalse();
        assertThat(RaidChipFamily.MPT_IR.canCreateAll(List.of())).isTrue();
    }

    @Test
    @DisplayName("안내 — 잠그는 레벨이 있는 계열만 문장, MegaRAID 는 null")
    void hint() {
        assertThat(RaidChipFamily.MPT_IR.creatableLevelsHint())
                .isEqualTo("MPT IR (sas3ircu 계열)은 RAID0 · RAID1 · RAID10 만 만들 수 있습니다");
        assertThat(RaidChipFamily.MEGARAID.creatableLevelsHint()).isNull();
    }

    @Test
    @DisplayName("정의서 레벨 — RAID00 · 50 · 60 은 카드 정보로만, 디스크 수 규칙을 물으면 프로그램 오류")
    void definable() {
        assertThat(RaidLevel.definableLevels())
                .containsExactly(RaidLevel.RAID0, RaidLevel.RAID1, RaidLevel.RAID5, RaidLevel.RAID6, RaidLevel.RAID10);
        assertThat(RaidLevel.RAID50.isDefinable()).isFalse();
        assertThatThrownBy(() -> RaidChipFamily.MEGARAID.memberCountRule(RaidLevel.RAID60, true, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
