package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.DiskGroupRuleRequest;
import com.example.serverprovision.provisioning.setting.dto.request.LinuxInstallationRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.dto.response.OsVolumeTarget;
import com.example.serverprovision.provisioning.setting.enums.OsVolumeTargetKind;

/**
 * OS 설치 파티션의 대상 볼륨 판정 — 의존 0 static ({@link DiskGroupRules} 옆, U4-1-3 D4). 네 분기:
 * RAID 구성 없음 · 묶음 0 → NONE / OS 고정 → FIXED / 우선순위에 따름 → BY_PRIORITY / 그 외 NO_CANDIDATE.
 * {@code SettingSaveRequest.isOsVolumeDeterminable} · 용량 하한과 같은 재료({@link RaidConfigurationRequest} 의 메서드)를 읽는다.
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
        if (raid == null || raid.getDiskGroups().isEmpty()) {
            return OsVolumeTarget.none();
        }
        Long bound = raid.osVolumeCapacityLowerBoundBytes().isPresent() ? raid.osVolumeCapacityLowerBoundBytes().getAsLong() : null;
        int fixedNo = raid.osFixedRuleNo();
        if (fixedNo > 0) {
            return new OsVolumeTarget(OsVolumeTargetKind.FIXED, fixedNo, summarize(raid.getDiskGroups().get(fixedNo - 1)), bound, fixed, grow);
        }
        if (raid.hasByPriorityRule()) {
            return new OsVolumeTarget(OsVolumeTargetKind.BY_PRIORITY, 0, null, bound, fixed, grow);
        }
        return new OsVolumeTarget(OsVolumeTargetKind.NO_CANDIDATE, 0, null, null, fixed, grow);
    }

    /** 묶음 요약 — {@code RAID1 · SSD · SATA · 480 GB · 2개}(폼 · 상세 · 데모가 같은 형태). */
    public static String summarize(DiskGroupRuleRequest rule) {
        return String.join(" · ",
                rule.buildsRaid() ? rule.raidLevel().getDisplayName() : "RAID 없음",
                rule.diskType() == null ? "?" : rule.diskType().getDisplayName(),
                rule.transport() == null ? "?" : rule.transport().getDisplayName(),
                rule.capacity() == null ? "?" : rule.capacity().toDisplay(),
                rule.count() == null ? "?" : rule.count().toDisplay());
    }
}
