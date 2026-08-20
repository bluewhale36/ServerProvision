package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCapacityRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCountRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskGroupRuleRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.VolumePriorityRuleRequest;
import com.example.serverprovision.provisioning.setting.dto.response.OsVolumeTarget;
import com.example.serverprovision.provisioning.setting.enums.CapacityRequirementMode;
import com.example.serverprovision.provisioning.setting.enums.DiskCapacityUnit;
import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.example.serverprovision.provisioning.setting.enums.DiskGroupRole;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import com.example.serverprovision.provisioning.setting.enums.OsVolumeTargetKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U4-1-3 CP4 — OS 설치 파티션의 대상 볼륨 판정({@link OsVolumeTargets#describe}) 네 분기 · 묶음 요약 · 용량 하한 표기.
 * 폼 JS 와 데모가 같은 분기를 재현하므로 여기가 드리프트의 기준이다.
 */
class OsVolumeTargetsTest {

    private static DiskGroupRuleRequest rule(RaidLevel level, DiskCapacityRequirement cap, DiskCountRequirement cnt, DiskGroupRole role) {
        return new DiskGroupRuleRequest(level, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, cap, cnt, role);
    }
    private static DiskCapacityRequirement gb(long size) {
        return new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, size, DiskCapacityUnit.GB);
    }
    private static final DiskCapacityRequirement AUTO = new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null);
    private static RaidConfigurationRequest raid(DiskGroupRuleRequest... rules) {
        return new RaidConfigurationRequest(1L, List.of(rules), VolumePriorityRuleRequest.defaults());
    }

    @Test
    @DisplayName("RAID 구성 null · 묶음 0 → NONE (설치기 자동 선택), 용량 줄 없음")
    void none() {
        assertThat(OsVolumeTargets.describe(null).kind()).isEqualTo(OsVolumeTargetKind.NONE);
        OsVolumeTarget empty = OsVolumeTargets.describe(raid());
        assertThat(empty.kind()).isEqualTo(OsVolumeTargetKind.NONE);
        assertThat(empty.toDisplay()).isEqualTo("RAID 구성 단계가 없어 설치기가 디스크를 자동 선택합니다");
        assertThat(empty.capacityDisplay()).isNull();
    }

    @Test
    @DisplayName("OS 고정 → FIXED(묶음 번호 · 요약 · 하한 = usableDisks(n) × 1 장) — RAID1 480 GB × 2 → 480 GB")
    void fixed() {
        OsVolumeTarget t = OsVolumeTargets.describe(raid(
                rule(null, AUTO, new DiskCountRequirement(DiskCountMode.EXACT, 1), DiskGroupRole.BY_PRIORITY),
                rule(RaidLevel.RAID1, gb(480), new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.OS)));
        assertThat(t.kind()).isEqualTo(OsVolumeTargetKind.FIXED);
        assertThat(t.ruleNo()).isEqualTo(2);
        assertThat(t.ruleSummary()).isEqualTo("RAID1 · SSD · SATA · 480 GB · 2개");
        assertThat(t.capacityLowerBoundBytes()).isEqualTo(480_000_000_000L);
        assertThat(t.toDisplay()).isEqualTo("RAID 구성 2번 묶음(RAID1 · SSD · SATA · 480 GB · 2개)이 OS 영역입니다");
        assertThat(t.capacityDisplay()).isEqualTo("OS 영역 최소 용량 480 GB · 고정 파티션 합 0 GiB");
    }

    @Test
    @DisplayName("우선순위에 따름 → BY_PRIORITY, 하한 = 후보 최솟값(RAID5 4 TB × 3 = 8 TB vs RAID 없음 960 GB → 960 GB) · 자동 탐지가 섞이면 실행 시 확인")
    void byPriority() {
        OsVolumeTarget t = OsVolumeTargets.describe(raid(
                rule(null, new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 960L, DiskCapacityUnit.GB),
                        new DiskCountRequirement(DiskCountMode.EXACT, 1), DiskGroupRole.BY_PRIORITY),
                rule(RaidLevel.RAID5, new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 4L, DiskCapacityUnit.TB),
                        new DiskCountRequirement(DiskCountMode.AT_LEAST, 3), DiskGroupRole.BY_PRIORITY),
                rule(RaidLevel.RAID1, gb(480), new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.DATA)));
        assertThat(t.kind()).isEqualTo(OsVolumeTargetKind.BY_PRIORITY);
        assertThat(t.capacityLowerBoundBytes()).isEqualTo(960_000_000_000L);
        assertThat(t.capacityDisplay()).isEqualTo("OS 영역 최소 용량 960 GB · 고정 파티션 합 0 GiB");

        OsVolumeTarget unknown = OsVolumeTargets.describe(raid(
                rule(RaidLevel.RAID1, AUTO, new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.BY_PRIORITY)));
        assertThat(unknown.kind()).isEqualTo(OsVolumeTargetKind.BY_PRIORITY);
        assertThat(unknown.capacityLowerBoundBytes()).isNull();
        assertThat(unknown.capacityDisplay()).isEqualTo("OS 영역 용량은 실행 시 확인됩니다(자동 탐지)");
    }

    @Test
    @DisplayName("전부 Data / 영역 할당 없음 → NO_CANDIDATE — 용량 줄 없음")
    void noCandidate() {
        OsVolumeTarget t = OsVolumeTargets.describe(raid(
                rule(RaidLevel.RAID1, gb(480), new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.DATA),
                rule(null, AUTO, new DiskCountRequirement(DiskCountMode.EXACT, 1), DiskGroupRole.NONE)));
        assertThat(t.kind()).isEqualTo(OsVolumeTargetKind.NO_CANDIDATE);
        assertThat(t.capacityDisplay()).isNull();
    }

    @Test
    @DisplayName("표기 — 십진(GB/TB) 과 이진(GiB) 변환, .0 생략")
    void formats() {
        assertThat(OsVolumeTarget.formatDecimal(480_000_000_000L)).isEqualTo("480 GB");
        assertThat(OsVolumeTarget.formatDecimal(8_000_000_000_000L)).isEqualTo("8 TB");
        assertThat(OsVolumeTarget.formatDecimal(1_500_000_000L)).isEqualTo("1.5 GB");
        assertThat(OsVolumeTarget.formatBinary(18L * 1_073_741_824L)).isEqualTo("18 GiB");
    }

    @Test
    @DisplayName("설치 단계를 함께 주면 용량 줄에 고정 파티션 합 · (+ grow) · 초과 접미사가 붙는다 — 폼과 같은 문구(CP5 F-1)")
    void withInstall() {
        var raid = raid(rule(RaidLevel.RAID1, gb(480), new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.OS));
        var install = new com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest(1L, 1L,
                new com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest("Asia/Seoul", true),
                List.of(new com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest("swap", com.example.serverprovision.provisioning.setting.enums.FileSystem.SWAP, 16L, com.example.serverprovision.provisioning.setting.enums.SizeUnit.GB, false),
                        new com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest("/", com.example.serverprovision.provisioning.setting.enums.FileSystem.XFS, 0L, com.example.serverprovision.provisioning.setting.enums.SizeUnit.GB, true)),
                new com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest("pw", false, false), List.of(), 1L, List.of(), true, null);
        OsVolumeTarget t = OsVolumeTargets.describe(raid, install);
        assertThat(t.capacityDisplay()).isEqualTo("OS 영역 최소 용량 480 GB · 고정 파티션 합 16 GiB(+ grow)");
        assertThat(t.over()).isFalse();

        var big = new com.example.serverprovision.provisioning.setting.dto.request.RHELInstallationRequest(1L, 1L,
                new com.example.serverprovision.provisioning.setting.dto.request.TimezoneRequest("Asia/Seoul", true),
                List.of(new com.example.serverprovision.provisioning.setting.dto.request.PartitionRequest("swap", com.example.serverprovision.provisioning.setting.enums.FileSystem.SWAP, 500L, com.example.serverprovision.provisioning.setting.enums.SizeUnit.GB, false)),
                new com.example.serverprovision.provisioning.setting.dto.request.RootPasswordRequest("pw", false, false), List.of(), 1L, List.of(), true, null);
        OsVolumeTarget o = OsVolumeTargets.describe(raid, big);
        assertThat(o.over()).isTrue();
        assertThat(o.capacityDisplay()).isEqualTo("OS 영역 최소 용량 480 GB · 고정 파티션 합 500 GiB — 최소 용량을 넘습니다");
        assertThat(OsVolumeTarget.messageTemplates()).containsKeys("NONE", "FIXED", "BY_PRIORITY", "NO_CANDIDATE", "CAPACITY_FORMAT");
    }
}
