package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.DiskGroupRuleRequest;
import com.example.serverprovision.provisioning.setting.dto.request.LinuxInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.dto.response.OsVolumeTarget;
import com.example.serverprovision.provisioning.setting.enums.OsVolumeTargetKind;

/**
 * OS 설치 파티션의 대상 볼륨 설명 — 의존 0 static ({@link DiskGroupRules} 옆, U4-1-3 D4). 분기는
 * {@link RaidConfigurationRequest#osVolumeTargetKind} 가 정하고(저장 가드와 같은 판정 · HF32), 여기서는 묶음 번호 · 요약 · 용량 하한을 붙인다.
 */
public final class OsVolumeTargets {

    private OsVolumeTargets() {
    }

    public static OsVolumeTarget describe(RaidConfigurationRequest raid) {
        return describe(raid, null);
    }

    /** OS 설치 단계를 함께 주면 고정 파티션 합 · grow 유무가 채워져 용량 줄이 폼과 같은 형태가 된다(CP5 F-1). */
    public static OsVolumeTarget describe(RaidConfigurationRequest raid, LinuxInstallationRequest install) {
        long fixed = install == null ? 0L : install.fixedPartitionBytes();
        boolean grow = install != null && install.hasGrowPartition();
        OsVolumeTargetKind kind = RaidConfigurationRequest.osVolumeTargetKindOf(raid);
        return switch (kind) {
            case NONE -> OsVolumeTarget.none();
            case FIXED -> {
                int fixedNo = raid.osFixedRuleNo();
                yield new OsVolumeTarget(kind, fixedNo, summarize(raid.getDiskGroups().get(fixedNo - 1)), lowerBound(raid), fixed, grow);
            }
            case BY_PRIORITY -> new OsVolumeTarget(kind, 0, null, lowerBound(raid), fixed, grow);
            case NO_CANDIDATE, NO_RAID_CANDIDATE -> new OsVolumeTarget(kind, 0, null, null, fixed, grow);
        };
    }

    private static Long lowerBound(RaidConfigurationRequest raid) {
        java.util.OptionalLong bound = raid.osVolumeCapacityLowerBoundBytes();
        return bound.isPresent() ? bound.getAsLong() : null;
    }

    /** 묶음 요약 — {@code RAID1 · SSD · SATA · 480 GB · 2개} · 스팬 지정 묶음은 {@code RAID10(스팬당 4대) · …}(R17-1). */
    public static String summarize(DiskGroupRuleRequest rule) {
        String level = !rule.buildsRaid() ? "RAID 미구성"
                : rule.hasDrivesPerSpan()
                ? rule.raidLevel().getDisplayName() + "(" + rule.drivesPerSpan().toDisplay() + ")"
                : rule.raidLevel().getDisplayName();
        return String.join(", ",
                level,
                rule.diskType() == null ? "?" : rule.diskType().getDisplayName(),
                rule.transport() == null ? "?" : rule.transport().getDisplayName(),
                rule.capacity() == null ? "?" : rule.capacity().toDisplay(),
                rule.count() == null ? "?" : rule.count().toDisplay());
    }
}
