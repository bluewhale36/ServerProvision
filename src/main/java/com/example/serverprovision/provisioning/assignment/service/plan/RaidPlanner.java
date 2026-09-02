package com.example.serverprovision.provisioning.assignment.service.plan;

import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.example.serverprovision.execution.engine.raid.PlannedPassthrough;
import com.example.serverprovision.execution.engine.raid.PlannedVolume;
import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.execution.engine.raid.RaidExistingConfigPolicy;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.engine.raid.RaidPhysicalDisk;
import com.example.serverprovision.execution.engine.raid.RaidPlan;
import com.example.serverprovision.execution.engine.raid.RaidPlanOutcome;
import com.example.serverprovision.execution.engine.raid.RaidPlanRejection;
import com.example.serverprovision.execution.engine.raid.RaidRuleOutcome;
import com.example.serverprovision.execution.engine.raid.UnassignedDisk;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.dto.request.VdParameters;
import com.example.serverprovision.provisioning.setting.dto.request.DiskGroupRuleRequest;
import com.example.serverprovision.provisioning.setting.dto.request.VolumePriorityRuleRequest;
import com.example.serverprovision.provisioning.setting.enums.CapacityOrder;
import com.example.serverprovision.provisioning.setting.enums.DiskGroupRole;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import com.example.serverprovision.provisioning.setting.service.reference.os.OsVolumeTargets;
import com.example.serverprovision.provisioning.setting.service.reference.os.VolumePriorityRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * 규칙 × 인벤토리 매칭 엔진(E3.5-2) — 의존 0 순수 static. 규칙 해석의 SSOT({@code DiskGroupRuleRequest} ·
 * {@code VolumePriorityRules} · {@code OsVolumeTargets})가 사는 provisioning 쪽에 두고, 산출물(계획)만
 * execution 소유 중립 모델로 낸다({@code BiosSettingTarget} 방향 원칙 — plan 결정 1).
 *
 * <p>소비 규칙(결정 6 · 실기 2026-09-01 배수 분할 개정): 어떤 규칙도 같은 스펙 그룹을 부분 소비하지 않는다 —
 * {@code EXACT n} 은 그룹 크기가 n 의 배수일 때 n 개씩 나눠 볼륨 여러 개로 소비하고(6대 · EXACT 3 → 3+3 두 볼륨),
 * {@code AT_LEAST n} 은 n 이상일 때 그룹 전체를 한 볼륨으로 소비한다. 소비하지 못한 그룹은 온전히 후행 규칙으로 흐른다.</p>
 */
public final class RaidPlanner {

    private RaidPlanner() {
    }

    /**
     * 볼륨에 실을 VD 파라미터(E3.5-6) — 지원 계열(MegaRAID)은 규칙이 축을 실어 오지 않았어도(E3.5-6 이전 저장본)
     * HII 기본값 {@link VdParameters#DEFAULTS} 로 항상 명시 조립한다. 그 밖의 계열은 축 자체가 없어 null.
     */
    private static VdParameters vdParametersOf(DiskGroupRuleRequest rule, RaidChipFamily family) {
        if (family == null || !family.supportsVdParameters()) {
            return null;
        }
        return rule.vdParameters() == null ? VdParameters.DEFAULTS : rule.vdParameters();
    }

    public static RaidPlanOutcome plan(List<DiskGroupRuleRequest> rules,
                                       List<VolumePriorityRuleRequest> priorities,
                                       RaidInventory inventory,
                                       RaidExistingConfigPolicy policy) {
        long foreignVolumes = inventory.volumes().stream()
                .filter(v -> !v.isProvisionOwned()).count();
        if (policy == RaidExistingConfigPolicy.PRESERVE && foreignVolumes > 0) {
            // 보존의 대상은 외부 구성(이전 데이터)이다 — 우리 잔여(spvR*)는 재구성 대상이라 거절 사유가
            // 아니다(E3.5-4 Q1 확정 · 판별 SSOT = RaidExistingVolume.isProvisionOwned).
            return new RaidPlanRejection(RaidPlanRejection.EXISTING_CONFIG,
                    "카드에 외부 기존 볼륨 " + foreignVolumes
                            + "개가 남아 있습니다 — 보존 정책에서는 새 구성을 계획할 수 없습니다.");
        }
        // 볼륨이 남아 있으면 정책 불문 선행 삭제 — 보존으로 여기 도달했다면 남은 것은 우리 잔여뿐이다.
        boolean deleteExistingFirst = !inventory.volumes().isEmpty();

        List<UnassignedDisk> unassigned = new ArrayList<>();
        List<Candidate> pool = new ArrayList<>();
        for (RaidPhysicalDisk disk : inventory.disks()) {
            Candidate candidate = Candidate.of(disk);
            if (candidate.exclusionReason != null) {
                unassigned.add(new UnassignedDisk(disk.slot(), disk.size(), candidate.exclusionReason));
            } else {
                pool.add(candidate);
            }
        }

        List<Entry> entries = new ArrayList<>();
        List<RaidRuleOutcome> ruleOutcomes = new ArrayList<>();
        for (int i = 0; i < rules.size(); i++) {
            DiskGroupRuleRequest rule = rules.get(i);
            int ruleNo = i + 1;
            List<Candidate> matched = pool.stream().filter(c -> axisMatch(rule, c)).toList();
            int consumed = 0;
            int volumeCount = 0;
            int volumeSeq = 0;
            for (List<Candidate> group : groupByClass(rule, matched)) {
                boolean selected = switch (rule.count().mode()) {
                    // 배수 분할(실기 2026-09-01) — n 의 배수면 n 개씩 나눠 소비, 아니면 미소비 · 후행 흘림
                    case EXACT -> group.size() % rule.count().value() == 0;
                    case AT_LEAST -> group.size() >= rule.count().value();
                };
                if (!selected) {
                    for (Candidate c : group) {
                        c.lastMissReason = "규칙 " + ruleNo + " · " + rule.count().toDisplay()
                                + " 조건에 " + group.size() + "대(배수 아님)라 미소비";
                    }
                    continue;
                }
                if (rule.buildsRaid()) {
                    // EXACT 는 n 개씩 슬라이스해 볼륨 여러 개, AT_LEAST 는 그룹 전체가 한 볼륨
                    int sliceSize = rule.count().mode() == DiskCountMode.EXACT
                            ? rule.count().value() : group.size();
                    for (int from = 0; from < group.size(); from += sliceSize) {
                        List<Candidate> slice = group.subList(from, from + sliceSize);
                        volumeSeq++;
                        volumeCount++;
                        long perDisk = slice.stream().mapToLong(c -> c.bytes).min().orElse(0L);
                        long usable = rule.raidLevel().usableDisks(slice.size()) * perDisk;
                        entries.add(Entry.volume("spvR" + ruleNo + "V" + volumeSeq,
                                rule.raidLevel(), rule, ruleNo, slice, usable, entries.size()));
                    }
                } else {
                    for (Candidate c : group) {
                        entries.add(Entry.passthrough(rule, ruleNo, c, entries.size()));
                    }
                }
                consumed += group.size();
                pool.removeAll(group);
            }
            ruleOutcomes.add(new RaidRuleOutcome(ruleNo, OsVolumeTargets.summarize(rule),
                    matched.size(), consumed, volumeCount));
        }

        // 칩 계열 한계 — 위반이면 계획 전체 거절(일부 집행은 의도와 다른 상태를 실물에 남긴다)
        RaidChipFamily family = inventory.card() == null ? null : inventory.card().chipFamily();
        List<Entry> volumes = entries.stream().filter(e -> e.volume).toList();
        if (family != null) {
            for (Entry v : volumes) {
                String reason = family.memberCountBlockReason(v.level, v.members.size());
                if (reason != null) {
                    return new RaidPlanRejection(RaidPlanRejection.MEMBER_COUNT, v.name + " — " + reason);
                }
            }
            if (volumes.size() > family.maxVolumes()) {
                return new RaidPlanRejection(RaidPlanRejection.VOLUME_LIMIT,
                        "계획 볼륨 " + volumes.size() + "개가 " + family.name()
                                + " 계열의 한계 " + family.maxVolumes() + "개를 넘습니다.");
            }
        }

        // 역할 · 우선순위 — OS 고정 규칙이 있으면 그것만 후보(osCandidateRules 계약 · §8 Q3 판정)
        Integer osFixedRuleNo = null;
        for (int i = 0; i < rules.size(); i++) {
            if (rules.get(i).isOsFixed()) {
                osFixedRuleNo = i + 1;
                break;
            }
        }
        Entry os = null;
        String osAbsenceReason = null;
        if (osFixedRuleNo != null) {
            final int fixedNo = osFixedRuleNo;
            os = entries.stream().filter(e -> e.ruleNo == fixedNo).findFirst().orElse(null);
            if (os == null) {
                osAbsenceReason = "OS 고정 규칙(규칙 " + osFixedRuleNo
                        + ")이 볼륨을 내지 못해 OS 영역 지정이 없습니다.";
            }
        } else {
            os = entries.stream()
                    .filter(e -> e.rule.role() == DiskGroupRole.BY_PRIORITY)
                    .min((a, b) -> comparePriority(priorities, a, b))
                    .orElse(null);
        }
        for (Entry e : entries) {
            e.plannedRole = e == os ? PlannedVolumeRole.OS
                    : e.rule.role() == DiskGroupRole.NONE ? PlannedVolumeRole.NONE : PlannedVolumeRole.DATA;
        }

        for (Candidate c : pool) {
            unassigned.add(new UnassignedDisk(c.slot, c.sizeDisplay,
                    c.lastMissReason != null ? c.lastMissReason : "어느 규칙의 조건에도 맞지 않습니다"));
        }

        List<PlannedVolume> plannedVolumes = volumes.stream()
                .map(e -> {
                    // VD 파라미터(E3.5-6) — 조립 SSOT 는 VdParameters(폼 데모 JS 와 같은 진리표)
                    VdParameters vd = vdParametersOf(e.rule, family);
                    return new PlannedVolume(e.name, e.level,
                            e.members.stream().map(c -> c.slot).toList(), e.usableBytes, e.plannedRole, e.ruleNo,
                            vd == null ? null : vd.createOpts(),
                            vd == null ? java.util.List.of() : vd.setOps(),
                            vd == null ? null : vd.initToken());
                })
                .toList();
        List<PlannedPassthrough> passthroughs = entries.stream()
                .filter(e -> !e.volume)
                .map(e -> new PlannedPassthrough(e.members.get(0).slot, e.usableBytes, e.plannedRole, e.ruleNo))
                .toList();
        return new RaidPlan(deleteExistingFirst, plannedVolumes, passthroughs,
                unassigned, ruleOutcomes, osAbsenceReason);
    }

    private static boolean axisMatch(DiskGroupRuleRequest rule, Candidate c) {
        if (rule.diskType() != DiskTypeRequirement.AUTO && rule.diskType() != c.type) {
            return false;
        }
        if (rule.transport() != DiskTransportRequirement.AUTO && rule.transport() != c.transport) {
            return false;
        }
        if (rule.capacity().isAuto()) {
            return true;
        }
        return RaidReportedSize.matches(c.bytes, rule.capacity().unit().toBytes(rule.capacity().size()));
    }

    /** (종류, 전송, 용량 계급) 그룹 — 열거(슬롯) 순 첫 등장이 그룹 순서. 지정 계급은 필터 통과가 곧 같은 계급. */
    private static List<List<Candidate>> groupByClass(DiskGroupRuleRequest rule, List<Candidate> matched) {
        List<List<Candidate>> groups = new ArrayList<>();
        List<Candidate> reps = new ArrayList<>();
        boolean specified = !rule.capacity().isAuto();
        for (Candidate c : matched) {
            List<Candidate> hit = null;
            for (int g = 0; g < groups.size(); g++) {
                Candidate rep = reps.get(g);
                if (rep.type == c.type && rep.transport == c.transport
                        && (specified || RaidReportedSize.matches(c.bytes, rep.bytes))) {
                    hit = groups.get(g);
                    break;
                }
            }
            if (hit != null) {
                hit.add(c);
            } else {
                groups.add(new ArrayList<>(List.of(c)));
                reps.add(c);
            }
        }
        return groups;
    }

    /** rankOf → 같은 순위면 그 행의 용량 순서 → 그래도 같으면 열거 순(U4-1-2 정의 그대로). */
    private static int comparePriority(List<VolumePriorityRuleRequest> priorities, Entry a, Entry b) {
        int rankA = VolumePriorityRules.rankOf(priorities, a.type, a.transport);
        int rankB = VolumePriorityRules.rankOf(priorities, b.type, b.transport);
        if (rankA != rankB) {
            return Integer.compare(rankA, rankB);
        }
        if (rankA != VolumePriorityRules.NO_RANK && a.usableBytes != b.usableBytes) {
            CapacityOrder order = priorities.get(rankA).capacityOrder();
            return order == CapacityOrder.LARGER_FIRST
                    ? Long.compare(b.usableBytes, a.usableBytes)
                    : Long.compare(a.usableBytes, b.usableBytes);
        }
        return Integer.compare(a.seq, b.seq);
    }

    /** 계획 후보 디스크 — 인벤토리 원문을 매칭 축(enum · 바이트)으로 정규화한 작업 모델. */
    private static final class Candidate {
        final String slot;
        final String sizeDisplay;
        final DiskTypeRequirement type;
        final DiskTransportRequirement transport;
        final long bytes;
        final String exclusionReason;
        String lastMissReason;

        private Candidate(String slot, String sizeDisplay, DiskTypeRequirement type,
                          DiskTransportRequirement transport, long bytes, String exclusionReason) {
            this.slot = slot;
            this.sizeDisplay = sizeDisplay;
            this.type = type;
            this.transport = transport;
            this.bytes = bytes;
            this.exclusionReason = exclusionReason;
        }

        static Candidate of(RaidPhysicalDisk disk) {
            String state = disk.state() == null ? null : disk.state().toLowerCase(Locale.ROOT);
            boolean healthy = state != null && (state.contains("onln") || state.contains("ugood")
                    || state.contains("jbod") || state.contains("ready") || state.contains("optimal"));
            if (!healthy) {
                return excluded(disk, "상태 " + (disk.state() == null ? "미상" : disk.state())
                        + " — 가용 상태가 아니라 제외");
            }
            DiskTypeRequirement type = switch (disk.type() == null ? "" : disk.type().toUpperCase(Locale.ROOT)) {
                case "SSD" -> DiskTypeRequirement.SSD;
                case "HDD" -> DiskTypeRequirement.HDD;
                default -> null;
            };
            if (type == null) {
                return excluded(disk, "종류 \"" + disk.type() + "\" 를 해석하지 못해 제외");
            }
            DiskTransportRequirement transport = switch (disk.transport() == null ? ""
                    : disk.transport().toUpperCase(Locale.ROOT)) {
                case "SATA" -> DiskTransportRequirement.SATA;
                case "SAS" -> DiskTransportRequirement.SAS;
                case "NVME" -> DiskTransportRequirement.NVME;
                default -> null;
            };
            if (transport == null) {
                return excluded(disk, "전송 \"" + disk.transport() + "\" 를 해석하지 못해 제외");
            }
            OptionalLong bytes = RaidReportedSize.parse(disk.size());
            if (bytes.isEmpty()) {
                return excluded(disk, "표기 \"" + disk.size() + "\" 를 바이트로 환산하지 못해 제외");
            }
            return new Candidate(disk.slot(), disk.size(), type, transport, bytes.getAsLong(), null);
        }

        private static Candidate excluded(RaidPhysicalDisk disk, String reason) {
            return new Candidate(disk.slot(), disk.size(), null, null, 0L, reason);
        }
    }

    /** 계획 항목(볼륨 또는 단독 디스크) — 역할 판정 전의 중간 모델. */
    private static final class Entry {
        final boolean volume;
        final String name;
        final RaidLevel level;
        final List<Candidate> members;
        final long usableBytes;
        final DiskGroupRuleRequest rule;
        final int ruleNo;
        final int seq;
        final DiskTypeRequirement type;
        final DiskTransportRequirement transport;
        PlannedVolumeRole plannedRole;

        private Entry(boolean volume, String name, RaidLevel level, List<Candidate> members,
                      long usableBytes, DiskGroupRuleRequest rule, int ruleNo, int seq) {
            this.volume = volume;
            this.name = name;
            this.level = level;
            this.members = members;
            this.usableBytes = usableBytes;
            this.rule = rule;
            this.ruleNo = ruleNo;
            this.seq = seq;
            this.type = members.get(0).type;
            this.transport = members.get(0).transport;
        }

        static Entry volume(String name, RaidLevel level, DiskGroupRuleRequest rule, int ruleNo,
                            List<Candidate> members, long usableBytes, int seq) {
            return new Entry(true, name, level, List.copyOf(members), usableBytes, rule, ruleNo, seq);
        }

        static Entry passthrough(DiskGroupRuleRequest rule, int ruleNo, Candidate disk, int seq) {
            return new Entry(false, null, null, List.of(disk), disk.bytes, rule, ruleNo, seq);
        }
    }
}
