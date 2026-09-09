package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.raid.DetectedRaidCard;
import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.execution.engine.raid.RaidExistingVolume;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.engine.windows.WindowsDiskSelection.Basis;
import com.example.serverprovision.execution.engine.windows.WindowsDiskSelection.Confidence;
import com.example.serverprovision.execution.engine.windows.WindowsDiskSelection.DiskSelection;
import com.example.serverprovision.execution.entity.RaidVolume;
import com.example.serverprovision.execution.vo.HardwareSpec;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E4-1-a-6 CP4 · HF15-5 개정 — 디스크 선택 진리표: ① lsblk(WWN) 순서 ② 계열 순서 규칙 ③ BLOCKED. 실기 3호 F-6 의 실측값
 * (sas3ircu 는 두 번째 볼륨을 앞에 나열 · Windows 는 만든 순서)이 그대로 표본이다.
 */
class WindowsDiskSelectionTest {

    private static final long OS_BYTES = 480_103_981_056L;

    private static RaidVolume volume(String name, RaidLevel level, PlannedVolumeRole role, String wwn) {
        return RaidVolume.of(null, name, level, "[]", OS_BYTES, role, 1, "Optl", wwn);
    }

    private static RaidExistingVolume observed(String id, String name, String wwn) {
        return new RaidExistingVolume(id, "RAID1", "446 GB", "Optl", name, List.of(), wwn);
    }

    private static RaidInventory inventory(RaidExistingVolume... volumes) {
        return inventory(RaidChipFamily.MEGARAID, volumes);
    }

    private static RaidInventory inventory(RaidChipFamily family, RaidExistingVolume... volumes) {
        return new RaidInventory(new DetectedRaidCard(family, "1000:9361", "m", "f"), List.of(), List.of(volumes));
    }

    private static HardwareSpec.DiskInfo disk(String device, String size, String transport, String wwn) {
        return new HardwareSpec.DiskInfo(device, "HDD", transport, size, wwn);
    }

    // ==== ② 계열 순서 규칙(lsblk 재료 없음) ====

    @Test
    @DisplayName("MegaRAID · OS 볼륨이 첫 볼륨 — DiskID 0 · 확증 기준 = WWN 그대로 · 근거 = inventory-order")
    void megaraid_osVolumeFirst_diskZero() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "600605b0aa11");
        DiskSelection sel = WindowsDiskSelection.judge(List.of(os), inventory(observed("VD0", "spvR1V1", "600605b0aa11")), null);

        assertThat(sel.confident()).isTrue();
        assertThat(sel.confidence()).isEqualTo(Confidence.CONFIDENT);
        assertThat(sel.diskId()).isZero();
        assertThat(sel.expectedUniqueId()).isEqualTo("600605b0aa11");
        assertThat(sel.expectedBytes()).isEqualTo(OS_BYTES);
        assertThat(sel.basis()).isEqualTo(Basis.INVENTORY_ORDER);
    }

    @Test
    @DisplayName("MegaRAID · 데이터 볼륨이 먼저면 OS 볼륨은 DiskID 1 — storcli 순서가 번호를 준다(Run 3 D1)")
    void megaraid_dataFirst_diskOne() {
        RaidVolume os = volume("spvR2V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "600605b0bb22");
        DiskSelection sel = WindowsDiskSelection.judge(List.of(os),
                inventory(observed("VD0", "data", "600605b0dddd"), observed("VD1", "spvR2V1", "600605b0bb22")), List.of());

        assertThat(sel.confident()).isTrue();
        assertThat(sel.diskId()).isEqualTo(1);
    }

    @Test
    @DisplayName("SAS3008(MPT_IR) · sas3ircu 가 [322 spvR1V2, 323 spvR1V1] 로 나열해도 Windows 는 만든 순 — 상1 · 상2 표본: OS=V1 → DiskID 0")
    void mptIr_reversedListing_osIsFirstCreated() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "08169c022f61a40c");
        DiskSelection sel = WindowsDiskSelection.judge(List.of(os),
                inventory(RaidChipFamily.MPT_IR,
                        observed("322", "spvR1V2", "023ff9af195f01b1"),
                        observed("323", "spvR1V1", "08169c022f61a40c")), null);

        assertThat(sel.confident()).isTrue();
        assertThat(sel.diskId()).isZero();                                                 // 실기 3호에서 1 로 계산돼 오설치됐던 값
        assertThat(sel.expectedUniqueId()).isEqualTo("600508e0000000000ca4612f029c1608");   // NAA 6 · wwid 뒤집기(F-7)
        assertThat(sel.basis()).isEqualTo(Basis.INVENTORY_ORDER);
    }

    @Test
    @DisplayName("SAS3008 · 상3 표본: OS=V2(두 번째로 만듦) → DiskID 1")
    void mptIr_osIsSecondCreated_diskOne() {
        RaidVolume os = volume("spvR1V2", RaidLevel.RAID1, PlannedVolumeRole.OS, "06923d86adedc238");
        DiskSelection sel = WindowsDiskSelection.judge(List.of(os),
                inventory(RaidChipFamily.MPT_IR,
                        observed("322", "spvR1V2", "06923d86adedc238"),
                        observed("323", "spvR1V1", "00a8209837f72f2e")), null);

        assertThat(sel.diskId()).isEqualTo(1);
        // OS 볼륨(06923d86…)의 NAA. 실기 3호 상3 의 완료 보고는 …2e2ff7379820a800(= DATA 00a82098…)이었으므로 이 기준과 어긋남 = MISMATCH 가 옳다
        assertThat(sel.expectedUniqueId()).isEqualTo("600508e00000000038c2edad863d9206");
    }

    @Test
    @DisplayName("WWN 미노출 볼륨은 이름(spvR*)으로 찾는다 · 확증 기준은 null")
    void nameFallback() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, null);
        DiskSelection sel = WindowsDiskSelection.judge(List.of(os), inventory(observed("VD0", "spvR1V1", null)), null);

        assertThat(sel.confident()).isTrue();
        assertThat(sel.diskId()).isZero();
        assertThat(sel.expectedUniqueId()).isNull();
    }

    // ==== ① lsblk 순서(검증 재채집) ====

    @Test
    @DisplayName("lsblk 에 OS 볼륨의 SCSI 식별자가 있으면 그 순서가 우선 — 계열 규칙과 달라도 lsblk 가 이긴다 · 근거 = lsblk")
    void osVisibleDisks_win() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "600605b0aa11");
        RaidInventory inv = inventory(observed("VD0", "spvR1V1", "600605b0aa11"), observed("VD1", "data", "600605b0dddd"));
        List<HardwareSpec.DiskInfo> lsblk = List.of(
                disk("sda", "18.2T", null, "0x600605b0dddd"),
                disk("sdb", "446.6G", null, "0x600605b0aa11"));

        DiskSelection sel = WindowsDiskSelection.judge(List.of(os), inv, lsblk);

        assertThat(sel.diskId()).isEqualTo(1);
        assertThat(sel.basis()).isEqualTo(Basis.OS_VISIBLE_DISKS);
    }

    @Test
    @DisplayName("BMC 가상 미디어(USB · 0B)는 세지 않는다 — Run 3: diskpart 가 컨트롤러 디스크 뒤에 번호를 줬다")
    void virtualMedia_notCounted() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "600605b0aa11");
        List<HardwareSpec.DiskInfo> lsblk = List.of(
                disk("sda", "0B", "USB", null), disk("sdb", "0B", "USB", null),
                disk("sdc", "446.6G", null, "0x600605b0aa11"));

        DiskSelection sel = WindowsDiskSelection.judge(List.of(os), inventory(observed("VD0", "spvR1V1", "600605b0aa11")), lsblk);

        assertThat(sel.diskId()).isZero();
        assertThat(sel.basis()).isEqualTo(Basis.OS_VISIBLE_DISKS);
    }

    @Test
    @DisplayName("SAS3008 · lsblk WWN 은 NAA 형식이라 계열 변환값과 맞는다 — IR 에서도 ① 이 성립")
    void mptIr_osVisibleDisks_naaMatch() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "08169c022f61a40c");
        List<HardwareSpec.DiskInfo> lsblk = List.of(
                disk("sda", "14.6T", null, "0x600508e000000000b1015f19aff93f02"),
                disk("sdb", "223.6G", null, "0x600508e0000000000ca4612f029c1608"));

        DiskSelection sel = WindowsDiskSelection.judge(List.of(os),
                inventory(RaidChipFamily.MPT_IR, observed("322", "spvR1V2", "023ff9af195f01b1"),
                        observed("323", "spvR1V1", "08169c022f61a40c")), lsblk);

        assertThat(sel.diskId()).isEqualTo(1);
        assertThat(sel.basis()).isEqualTo(Basis.OS_VISIBLE_DISKS);
    }

    @Test
    @DisplayName("구 저장본(lsblk 에 WWN 없음)이면 ② 로 내려간다")
    void osVisibleDisksWithoutWwn_fallsBack() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "600605b0aa11");
        List<HardwareSpec.DiskInfo> lsblk = List.of(disk("sda", "446.6G", null, null));

        DiskSelection sel = WindowsDiskSelection.judge(List.of(os), inventory(observed("VD0", "spvR1V1", "600605b0aa11")), lsblk);

        assertThat(sel.diskId()).isZero();
        assertThat(sel.basis()).isEqualTo(Basis.INVENTORY_ORDER);
    }

    // ==== ③ BLOCKED · DEFERRED ====

    @Test
    @DisplayName("OS 영역 볼륨이 없으면 BLOCKED — os volume missing")
    void noOsVolume_blocked() {
        RaidVolume data = volume("data", RaidLevel.RAID5, PlannedVolumeRole.DATA, "600605b0dddd");
        DiskSelection sel = WindowsDiskSelection.judge(List.of(data), inventory(observed("VD0", "data", "600605b0dddd")), null);

        assertThat(sel.confident()).isFalse();
        assertThat(sel.blocked()).isTrue();
        assertThat(sel.wire()).isEqualTo("os volume missing");
        assertThat(sel.diskId()).isEqualTo(-1);
    }

    @Test
    @DisplayName("RAID 인벤토리가 없으면(진단 전) BLOCKED — raid inventory missing")
    void noInventory_blocked() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "600605b0aa11");

        assertThat(WindowsDiskSelection.judge(List.of(os), null, null).wire()).isEqualTo("raid inventory missing");
        assertThat(WindowsDiskSelection.judge(List.of(os), inventory(), null).wire()).isEqualTo("raid inventory missing");
    }

    @Test
    @DisplayName("OS 볼륨을 인벤토리에서 못 찾으면 BLOCKED — os volume not in inventory")
    void osVolumeNotInInventory_blocked() {
        RaidVolume os = volume("spvR9V9", RaidLevel.RAID1, PlannedVolumeRole.OS, "600605b0zzzz");
        DiskSelection sel = WindowsDiskSelection.judge(List.of(os), inventory(observed("VD0", "other", "600605b0aa11")), null);

        assertThat(sel.confident()).isFalse();
        assertThat(sel.wire()).isEqualTo("os volume not in inventory");
    }

    @Test
    @DisplayName("물리 디스크 직결(RAID 레벨 없는 볼륨)이 섞이면 BLOCKED — passthrough not supported")
    void passthroughMixed_blocked() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "600605b0aa11");
        RaidVolume passthrough = volume("252:5", null, PlannedVolumeRole.DATA, null);
        DiskSelection sel = WindowsDiskSelection.judge(List.of(os, passthrough),
                inventory(observed("VD0", "spvR1V1", "600605b0aa11")), null);

        assertThat(sel.confident()).isFalse();
        assertThat(sel.wire()).isEqualTo("passthrough not supported");
    }

    @Test
    @DisplayName("WWN 대조는 대소문자 · 0x 접두를 무시한다")
    void wwnNormalized() {
        RaidVolume os = volume("spvR1V1", RaidLevel.RAID1, PlannedVolumeRole.OS, "600605b0aa11");
        DiskSelection sel = WindowsDiskSelection.judge(List.of(os), inventory(observed("VD0", "x", "0x600605B0AA11")), null);

        assertThat(sel.confident()).isTrue();
        assertThat(sel.diskId()).isZero();
    }

    @Test
    @DisplayName("DEFERRED 는 막지 않는다 — confident false · blocked false · diskId -1")
    void deferred_isNeitherConfidentNorBlocked() {
        DiskSelection sel = DiskSelection.deferred("RAID 구성 뒤 확정 — 계획의 OS 영역 spvR1V1");

        assertThat(sel.confidence()).isEqualTo(Confidence.DEFERRED);
        assertThat(sel.confident()).isFalse();
        assertThat(sel.blocked()).isFalse();
        assertThat(sel.diskId()).isEqualTo(-1);
        assertThat(sel.note()).contains("spvR1V1");
    }
}
