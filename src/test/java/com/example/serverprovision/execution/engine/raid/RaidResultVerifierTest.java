package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3.5-3 V11 — 동결 계획 × 재채집 대조의 축 전수(볼륨 수 · 레벨 · 멤버 집합 · 이름 · IR 이름 폴백).
 */
class RaidResultVerifierTest {

    private static PlannedVolume planned(String name, RaidLevel level, String... slots) {
        return new PlannedVolume(name, level, List.of(slots), 1L, PlannedVolumeRole.DATA, 1);
    }

    private static RaidPlan frozen(PlannedVolume... volumes) {
        return new RaidPlan(false, List.of(volumes), List.of(), List.of(), List.of(), null);
    }

    private static RaidExistingVolume observed(String name, String level, String... slots) {
        return new RaidExistingVolume("VD0", level, "446.625 GB", "Optl", name, List.of(slots), null);
    }

    private static RaidInventory inv(RaidExistingVolume... volumes) {
        return new RaidInventory(null, List.of(), List.of(volumes));
    }

    @Test
    @DisplayName("일치 — 이름 · 레벨 · 멤버가 계획대로면 null")
    void exactMatch_returnsNull() {
        assertThat(RaidResultVerifier.mismatchReason(
                frozen(planned("spvR1V1", RaidLevel.RAID1, "252:0", "252:1")),
                inv(observed("spvR1V1", "RAID1", "252:0", "252:1")))).isNull();
    }

    @Test
    @DisplayName("IR 이름 폴백 — 이름 미노출 볼륨은 레벨 + 멤버 집합으로 매칭한다")
    void nameless_fallsBackToLevelAndMembers() {
        assertThat(RaidResultVerifier.mismatchReason(
                frozen(planned("spvR1V1", RaidLevel.RAID1, "1:0", "1:1")),
                inv(observed(null, "RAID1", "1:0", "1:1")))).isNull();
        // 레벨 표기의 공백 관용("RAID 1")도 같은 폴백 경로로 흡수된다
        assertThat(RaidResultVerifier.mismatchReason(
                frozen(planned("spvR1V1", RaidLevel.RAID1, "1:0", "1:1")),
                inv(observed(null, "RAID 1", "1:0", "1:1")))).isNull();
    }

    @Test
    @DisplayName("볼륨 수 불일치 — 계획 1 · 실물 0")
    void volumeCountMismatch() {
        assertThat(RaidResultVerifier.mismatchReason(
                frozen(planned("spvR1V1", RaidLevel.RAID1, "252:0", "252:1")), inv()))
                .contains("볼륨 수 불일치");
    }

    @Test
    @DisplayName("레벨 불일치 — 이름은 같아도 대응 실패")
    void levelMismatch() {
        assertThat(RaidResultVerifier.mismatchReason(
                frozen(planned("spvR1V1", RaidLevel.RAID1, "252:0", "252:1")),
                inv(observed("spvR1V1", "RAID0", "252:0", "252:1"))))
                .contains("spvR1V1");
    }

    @Test
    @DisplayName("멤버 집합 불일치 — 슬롯이 다르면 대응 실패")
    void memberMismatch() {
        assertThat(RaidResultVerifier.mismatchReason(
                frozen(planned("spvR1V1", RaidLevel.RAID1, "252:0", "252:1")),
                inv(observed("spvR1V1", "RAID1", "252:0", "252:2"))))
                .contains("spvR1V1");
    }

    @Test
    @DisplayName("이름 불일치 — 외부 이름 볼륨은 이름 폴백(빈 이름 한정)에 잡히지 않는다")
    void foreignName_notMatched() {
        assertThat(RaidResultVerifier.mismatchReason(
                frozen(planned("spvR1V1", RaidLevel.RAID1, "252:0", "252:1")),
                inv(observed("legacy", "RAID1", "252:0", "252:1"))))
                .contains("spvR1V1");
    }

    @Test
    @DisplayName("다중 볼륨 — 실물 순서가 뒤바뀌어도 소비 매칭으로 전부 대응한다")
    void multipleVolumes_orderIndependent() {
        assertThat(RaidResultVerifier.mismatchReason(
                frozen(planned("spvR1V1", RaidLevel.RAID1, "252:0", "252:1"),
                        planned("spvR2V1", RaidLevel.RAID5, "252:2", "252:3", "252:4")),
                inv(observed("spvR2V1", "RAID5", "252:2", "252:3", "252:4"),
                        observed("spvR1V1", "RAID1", "252:0", "252:1")))).isNull();
    }
}
