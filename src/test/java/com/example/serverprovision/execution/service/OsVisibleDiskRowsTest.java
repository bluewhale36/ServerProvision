package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.engine.raid.DetectedRaidCard;
import com.example.serverprovision.execution.engine.raid.RaidExistingVolume;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.engine.raid.RaidPhysicalDisk;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2026-09-17 — RAID 볼륨 행의 종류 · 전송은 lsblk(tran=sas · 카드가 내미는 값)가 아니라 멤버 물리 디스크(CLI 보고)에서.
 * 픽스처는 사내망 실기 2호기(CRA3338 · MPT_IR)의 저장본 그대로 — IR 볼륨 WWN 08292285cf79311b ↔ lsblk 600508e0…1b3179cf85222908.
 */
class OsVisibleDiskRowsTest {

    private static RaidInventory irInventory() {
        DetectedRaidCard card = new DetectedRaidCard(RaidChipFamily.MPT_IR, "1458:3008", "CRA3338", null);
        List<RaidPhysicalDisk> disks = List.of(
                new RaidPhysicalDisk("1:0", "SSD", "SATA", "223.570 GB", "Optimal", "MZ7L3240", "S1", "323"),
                new RaidPhysicalDisk("1:1", "HDD", "SATA", "14.553 TB", "Optimal", "ST16000", "S2", "322"),
                new RaidPhysicalDisk("1:2", "SSD", "SATA", "223.570 GB", "Optimal", "MZ7L3240", "S3", "323"),
                new RaidPhysicalDisk("1:3", "HDD", "SATA", "14.553 TB", "Optimal", "ST16000", "S4", "322"),
                new RaidPhysicalDisk("1:4", "HDD", "SAS", "1.8 TB", "Ready", "HUC", "S5", null));
        List<RaidExistingVolume> volumes = List.of(
                new RaidExistingVolume("322", "RAID1", "14.553 TB", "Optimal", "spvR1V2", List.of("1:1", "1:3"), "02fc2e36da043ac8"),
                new RaidExistingVolume("323", "RAID1", "223.570 GB", "Optimal", "spvR1V1", List.of("1:0", "1:2"), "08292285cf79311b"));
        return new RaidInventory(card, disks, volumes);
    }

    private static HardwareSpec specWith(List<HardwareSpec.DiskInfo> disks) {
        return new HardwareSpec(null, null, disks, null);
    }

    @Test
    @DisplayName("IR 볼륨과 짝이 맞는 lsblk 장치 — 종류 · 전송은 멤버(SSD · SATA), 이름은 볼륨 이름 (lsblk 의 SAS 는 버린다)")
    void volumeRowUsesMemberDisks() {
        List<GuestServerDetailResponse.OsVisibleDisk> rows = OsVisibleDiskRows.of(specWith(List.of(
                new HardwareSpec.DiskInfo("sde", "SSD", "SAS", "222.6G", "600508e0000000001b3179cf85222908"),
                new HardwareSpec.DiskInfo("sdb", "HDD", "SAS", "14.6T", "600508e000000000c83a04da362efc02"))), irInventory());

        assertThat(rows).extracting(GuestServerDetailResponse.OsVisibleDisk::device).containsExactly("sde", "sdb");
        assertThat(rows.get(0).raidVolumeName()).isEqualTo("spvR1V1");
        assertThat(rows.get(0).type()).isEqualTo("SSD");
        assertThat(rows.get(0).transport()).isEqualTo("SATA");
        assertThat(rows.get(1).raidVolumeName()).isEqualTo("spvR1V2");
        assertThat(rows.get(1).type()).isEqualTo("HDD");
        assertThat(rows.get(1).transport()).isEqualTo("SATA");
    }

    @Test
    @DisplayName("카드 뒤인데 볼륨과 짝이 없는 장치 — lsblk 전송은 버리고, 카드 전체가 한 전송이 아니면 비운다")
    void unmatchedBehindCardDropsLsblkTransport() {
        List<GuestServerDetailResponse.OsVisibleDisk> rows = OsVisibleDiskRows.of(specWith(List.of(
                new HardwareSpec.DiskInfo("sdz", "HDD", "SAS", "1.8T", "6000000000000000ffffffffffffffff"))), irInventory());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).raidVolumeName()).isNull();
        assertThat(rows.get(0).type()).isNull();
        assertThat(rows.get(0).transport()).isNull();   // SATA 4 + SAS 1 → 카드 전체가 한 전송이 아니다
    }

    @Test
    @DisplayName("카드가 없으면 lsblk 값 그대로(NVMe 직결 등)")
    void noCardKeepsLsblk() {
        List<GuestServerDetailResponse.OsVisibleDisk> rows = OsVisibleDiskRows.of(specWith(List.of(
                new HardwareSpec.DiskInfo("nvme0n1", "SSD", "NVME", "1.9T", "eui.1"))), null);

        assertThat(rows.get(0).type()).isEqualTo("SSD");
        assertThat(rows.get(0).transport()).isEqualTo("NVME");
    }
}
