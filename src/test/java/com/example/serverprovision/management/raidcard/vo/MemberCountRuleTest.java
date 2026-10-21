package com.example.serverprovision.management.raidcard.vo;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCountRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberCountRuleTest {

    private static final DrivesPerSpan P2 = new DrivesPerSpan(2);
    /** MegaRAID RAID10 · 스팬당 2대 = 4~16대 · 2대 단위. */
    private static final MemberCountRule RAID10_P2 = new MemberCountRule(RaidLevel.RAID10, 4, 16, 2, P2);

    @Test
    @DisplayName("표기 — 범위 · 단일값 · 상한 없음 · 단위는 2 이상일 때만")
    void display() {
        assertThat(RAID10_P2.display()).isEqualTo("4~16대 · 2대 단위");
        assertThat(MemberCountRule.of(RaidLevel.RAID1, 2, 2).display()).isEqualTo("2대");
        assertThat(MemberCountRule.of(RaidLevel.RAID5, 3, null).display()).isEqualTo("3대 이상");
        assertThat(MemberCountRule.of(RaidLevel.RAID10, 3, 10).display()).isEqualTo("3~10대");
    }

    @ParameterizedTest(name = "멤버 {0}대 → 통과 {1}")
    @CsvSource({"3, false", "4, true", "6, true", "7, false", "16, true", "18, false"})
    @DisplayName("확정 멤버 수 판정 — 하한 · 상한 · 단위 전부")
    void blockReasonFor_members(int members, boolean admitted) {
        assertThat(RAID10_P2.blockReasonFor(members) == null).isEqualTo(admitted);
    }

    @Test
    @DisplayName("거절 문구 — 스팬 규칙은 스팬을 말하고, 그 밖은 구성 대수를 말한다")
    void rejectionWording() {
        assertThat(RAID10_P2.blockReasonFor(7))
                .isEqualTo("RAID10 을 스팬당 2대로 만들려면 4~16대 · 2대 단위여야 합니다 — 7대는 만들 수 없습니다");
        assertThat(MemberCountRule.of(RaidLevel.RAID1, 2, 2).blockReasonFor(3))
                .isEqualTo("RAID1 의 구성 대수는 2대입니다 — 3대는 만들 수 없습니다");
    }

    @Test
    @DisplayName("모순 규칙(상한 < 하한 · 단위 0)은 생성자가 거절")
    void rejectsContradiction() {
        assertThatThrownBy(() -> MemberCountRule.of(RaidLevel.RAID1, 4, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MemberCountRule(RaidLevel.RAID10, 4, 16, 0, P2)).isInstanceOf(IllegalArgumentException.class);
    }

    /** 개수 축 진리표 — '개' · '개씩' 은 전부, '개 이상' 은 하한 · 상한만(폼 JS countAdmitted 와 같은 표). */
    @ParameterizedTest(name = "{0} {1} → 통과 {2}")
    @CsvSource({
            "EXACT, 6, true", "EXACT, 7, false", "EACH, 7, false", "EACH, 4, true",
            "AT_LEAST, 5, true", "AT_LEAST, 3, false", "AT_LEAST, 18, false"
    })
    @DisplayName("DiskCountRequirement.memberCountBlockReason — 개수 방식별 판정")
    void countModeTruthTable(DiskCountMode mode, int n, boolean admitted) {
        assertThat(new DiskCountRequirement(mode, n).memberCountBlockReason(RAID10_P2) == null).isEqualTo(admitted);
    }
}
