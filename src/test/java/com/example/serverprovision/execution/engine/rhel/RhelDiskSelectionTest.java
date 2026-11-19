package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.osinstall.OsVolume;
import com.example.serverprovision.execution.engine.raid.DetectedRaidCard;
import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.execution.engine.raid.RaidExistingVolume;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.entity.RaidVolume;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-b-4-2 — RHEL 대상 디스크: OS 영역 볼륨 WWN 을 계열 변환해 wwn-0x by-id 이름으로. 고를 수 없으면 막고, RAID 구성 전이면 미룬다. */
class RhelDiskSelectionTest {

    private static RaidVolume volume(PlannedVolumeRole role, RaidLevel level, String wwn) {
        return RaidVolume.of(null, "spvR1V1", level, "[]", 480103981056L, role, 1, "Optl", wwn);
    }

    private static RaidInventory inventory(RaidChipFamily family) {
        return new RaidInventory(new DetectedRaidCard(family, "1000:0097", "m", "f"), List.of(),
                List.of(new RaidExistingVolume("1", "RAID1", "446 GB", "Optl", "spvR1V1", List.of(), "x")));
    }

    @Test
    @DisplayName("MPT IR — sas3ircu wwid(8바이트)를 뒤집어 NAA 6 접두를 붙인 값에 wwn-0x")
    void confident_irConversion() {
        var selection = RhelDiskSelection.judge(new OsVolume.Materialized(
                List.of(volume(PlannedVolumeRole.OS, RaidLevel.RAID1, "0123456789abcdef")), inventory(RaidChipFamily.MPT_IR)));

        assertThat(selection).isInstanceOf(RhelDiskSelection.Selection.Confident.class);
        assertThat(((RhelDiskSelection.Selection.Confident) selection).disk().byIdName())
                .isEqualTo("wwn-0x600508e000000000efcdab8967452301");
    }

    @Test
    @DisplayName("카드 계열을 모르면 hex 정규화만 한다")
    void confident_unknownFamily() {
        RaidInventory noCard = new RaidInventory(null, List.of(),
                List.of(new RaidExistingVolume("1", "RAID1", "446 GB", "Optl", "spvR1V1", List.of(), "x")));
        var selection = RhelDiskSelection.judge(new OsVolume.Materialized(
                List.of(volume(PlannedVolumeRole.OS, RaidLevel.RAID1, "0x5000C500A1B2C3D4")), noCard));

        assertThat(((RhelDiskSelection.Selection.Confident) selection).disk().byIdName()).isEqualTo("wwn-0x5000c500a1b2c3d4");
    }

    @Test
    @DisplayName("막힘 — OS 볼륨 없음 · 인벤토리 없음 · 직결 디스크 · WWN 모름")
    void blocked() {
        assertThat(wire(new OsVolume.Materialized(List.of(volume(PlannedVolumeRole.DATA, RaidLevel.RAID1, "aa")), inventory(RaidChipFamily.MEGARAID))))
                .isEqualTo("os volume missing");
        assertThat(wire(new OsVolume.Materialized(List.of(volume(PlannedVolumeRole.OS, RaidLevel.RAID1, "aa")), null)))
                .isEqualTo("raid inventory missing");
        assertThat(wire(new OsVolume.Materialized(List.of(volume(PlannedVolumeRole.OS, RaidLevel.RAID1, "aa"),
                volume(PlannedVolumeRole.DATA, null, "bb")), inventory(RaidChipFamily.MEGARAID))))
                .isEqualTo("passthrough not supported");
        assertThat(wire(new OsVolume.Materialized(List.of(volume(PlannedVolumeRole.OS, RaidLevel.RAID1, null)), inventory(RaidChipFamily.MEGARAID))))
                .isEqualTo("os volume wwn missing");
    }

    @Test
    @DisplayName("RAID 구성 전(미룸)과 계획 막힘은 그대로 옮긴다")
    void pendingPassesThrough() {
        assertThat(RhelDiskSelection.judge(new OsVolume.Deferred("RAID 구성 뒤 정해집니다")))
                .isEqualTo(new RhelDiskSelection.Selection.Deferred("RAID 구성 뒤 정해집니다"));
        assertThat(RhelDiskSelection.judge(new OsVolume.Blocked("os volume missing in plan", "n")))
                .isEqualTo(new RhelDiskSelection.Selection.Blocked("os volume missing in plan", "n"));
    }

    private static String wire(OsVolume located) {
        return ((RhelDiskSelection.Selection.Blocked) RhelDiskSelection.judge(located)).wire();
    }
}
