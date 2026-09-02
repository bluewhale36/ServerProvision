package com.example.serverprovision.provisioning.assignment.service.plan;

import com.example.serverprovision.provisioning.setting.enums.VdBackgroundInit;
import com.example.serverprovision.provisioning.setting.enums.VdDriveCache;
import com.example.serverprovision.provisioning.setting.enums.VdInitialization;
import com.example.serverprovision.provisioning.setting.enums.VdWritePolicy;
import com.example.serverprovision.provisioning.setting.dto.request.VdParameters;
import com.example.serverprovision.execution.engine.raid.DetectedRaidCard;
import com.example.serverprovision.execution.engine.raid.PlannedVolume;
import com.example.serverprovision.execution.engine.raid.PlannedVolumeRole;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
import com.example.serverprovision.execution.engine.raid.RaidExistingConfigPolicy;
import com.example.serverprovision.execution.engine.raid.RaidExistingVolume;
import com.example.serverprovision.execution.engine.raid.RaidInventory;
import com.example.serverprovision.execution.engine.raid.RaidInventoryParser;
import com.example.serverprovision.execution.engine.raid.RaidPhysicalDisk;
import com.example.serverprovision.execution.engine.raid.RaidPlan;
import com.example.serverprovision.execution.engine.raid.RaidPlanOutcome;
import com.example.serverprovision.execution.engine.raid.RaidPlanRejection;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCapacityRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCountRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskGroupRuleRequest;
import com.example.serverprovision.provisioning.setting.dto.request.VolumePriorityRuleRequest;
import com.example.serverprovision.provisioning.setting.enums.CapacityOrder;
import com.example.serverprovision.provisioning.setting.enums.CapacityRequirementMode;
import com.example.serverprovision.provisioning.setting.enums.DiskCapacityUnit;
import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.example.serverprovision.provisioning.setting.enums.DiskGroupRole;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E3.5-2 — 규칙에 따라 디스크가 RAID 볼륨으로 잡히는 경우의 수 전수(CP4 승인 시 사용자 지시).
 * 규칙 간 간섭(사각 규칙)의 정적 방어는 E3.5-4 소관이라 여기서 다루지 않는다. 시나리오 진리표
 * (plan §6 T1~T17)와 축별 전수(축 필터 11조합 · 용량 · 개수 × 그룹 크기 · 레벨 · 그룹화 · 역할)를 함께 덮는다.
 */
class RaidPlannerTest {

    // ── 공통 fixture ──────────────────────────────────────────────────────────

    /** 2진 표기(계열 CLI 실측 형태) — 480 GB 급 · 960 GB 급 · 4 TB 급 · 산술 검증용 100 GiB. */
    private static final String S480 = "446.625 GB";
    private static final String S960 = "894.25 GB";
    private static final String S4T = "3.637 TB";
    private static final String S100GIB = "100 GB";
    private static final long PER_100GIB = 100L << 30;

    private static RaidPhysicalDisk disk(String slot, String type, String transport, String size) {
        return new RaidPhysicalDisk(slot, type, transport, size, "Onln", "MODEL", "SN-" + slot, null);
    }

    private static RaidInventory inv(RaidChipFamily family, List<RaidPhysicalDisk> disks,
                                     List<RaidExistingVolume> volumes) {
        DetectedRaidCard card = family == null ? null
                : new DetectedRaidCard(family, "1000:9361", "테스트 카드", "fw");
        return new RaidInventory(card, disks, volumes);
    }

    private static RaidInventory mega(RaidPhysicalDisk... disks) {
        return inv(RaidChipFamily.MEGARAID, List.of(disks), List.of());
    }

    private static DiskGroupRuleRequest rule(RaidLevel level, DiskTypeRequirement type,
                                             DiskTransportRequirement transport,
                                             DiskCapacityRequirement capacity,
                                             DiskCountMode mode, int count, DiskGroupRole role) {
        return new DiskGroupRuleRequest(level, type, transport, capacity,
                new DiskCountRequirement(mode, count), role);
    }

    private static DiskCapacityRequirement auto() {
        return new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null);
    }

    private static DiskCapacityRequirement gb(long size) {
        return new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, size, DiskCapacityUnit.GB);
    }

    private static DiskCapacityRequirement tb(long size) {
        return new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, size, DiskCapacityUnit.TB);
    }

    private static RaidPlan planOf(RaidPlanOutcome outcome) {
        assertThat(outcome).isInstanceOf(RaidPlan.class);
        return (RaidPlan) outcome;
    }

    private static RaidPlan plan(RaidInventory inventory, DiskGroupRuleRequest... rules) {
        return planOf(RaidPlanner.plan(List.of(rules), VolumePriorityRuleRequest.defaults(),
                inventory, RaidExistingConfigPolicy.DESTROY));
    }

    private static Set<String> allMemberSlots(RaidPlan plan) {
        return plan.volumes().stream().flatMap(v -> v.memberSlots().stream()).collect(Collectors.toSet());
    }

    // ── 축 필터 — 종류 × 전송 11 유효 조합 전수 ──────────────────────────────

    @Nested
    @DisplayName("축 필터 — 종류(SSD/HDD/AUTO) × 전송(SATA/SAS/NVMe/AUTO) 유효 11조합")
    class AxisFilter {

        /** 5개 스펙 클래스 × 2대 — 어느 조합이 어느 클래스를 잡는지의 전수 기준 풀. */
        private RaidInventory pool() {
            return mega(
                    disk("a:0", "SSD", "SATA", S480), disk("a:1", "SSD", "SATA", S480),
                    disk("b:0", "SSD", "SAS", S480), disk("b:1", "SSD", "SAS", S480),
                    disk("c:0", "SSD", "NVME", S480), disk("c:1", "SSD", "NVME", S480),
                    disk("d:0", "HDD", "SATA", S4T), disk("d:1", "HDD", "SATA", S4T),
                    disk("e:0", "HDD", "SAS", S4T), disk("e:1", "HDD", "SAS", S4T));
        }

        static Stream<org.junit.jupiter.params.provider.Arguments> combos() {
            return Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of("SSD", "SATA", 1, "a"),
                    org.junit.jupiter.params.provider.Arguments.of("SSD", "SAS", 1, "b"),
                    org.junit.jupiter.params.provider.Arguments.of("SSD", "NVME", 1, "c"),
                    org.junit.jupiter.params.provider.Arguments.of("SSD", "AUTO", 3, "abc"),
                    org.junit.jupiter.params.provider.Arguments.of("HDD", "SATA", 1, "d"),
                    org.junit.jupiter.params.provider.Arguments.of("HDD", "SAS", 1, "e"),
                    org.junit.jupiter.params.provider.Arguments.of("HDD", "AUTO", 2, "de"),
                    org.junit.jupiter.params.provider.Arguments.of("AUTO", "SATA", 2, "ad"),
                    org.junit.jupiter.params.provider.Arguments.of("AUTO", "SAS", 2, "be"),
                    org.junit.jupiter.params.provider.Arguments.of("AUTO", "NVME", 1, "c"),
                    org.junit.jupiter.params.provider.Arguments.of("AUTO", "AUTO", 5, "abcde"));
        }

        @ParameterizedTest(name = "{0} × {1} → 클래스 {2}개 · 볼륨 {2}개")
        @MethodSource("combos")
        void typeTransportCombination_capturesExactClasses(String type, String transport,
                                                           int expectedVolumes, String classes) {
            RaidPlan plan = plan(pool(), rule(RaidLevel.RAID1, DiskTypeRequirement.valueOf(type),
                    DiskTransportRequirement.valueOf(transport), auto(),
                    DiskCountMode.AT_LEAST, 2, DiskGroupRole.DATA));

            assertThat(plan.volumes()).hasSize(expectedVolumes);
            Set<String> expected = classes.chars().mapToObj(c -> (char) c)
                    .flatMap(c -> Stream.of(c + ":0", c + ":1")).collect(Collectors.toSet());
            assertThat(allMemberSlots(plan)).isEqualTo(expected);
            assertThat(plan.unassigned()).hasSize(10 - expectedVolumes * 2);
        }
    }

    // ── 용량 축 — 지정 매칭 · 비매칭 · AUTO ─────────────────────────────────

    @Nested
    @DisplayName("용량 축 — 지정값 ±3% 매칭과 AUTO")
    class CapacityAxis {

        @Test
        @DisplayName("480 GB 지정은 480 급만 잡는다 — 960 급 · 4 TB 급은 미매칭(T5 의 planner 판)")
        void specified_capturesOnlyMatchingClass() {
            RaidPlan plan = plan(
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480),
                            disk("s:2", "SSD", "SATA", S960), disk("s:3", "SSD", "SATA", S960)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            gb(480), DiskCountMode.EXACT, 2, DiskGroupRole.DATA));

            assertThat(plan.volumes()).hasSize(1);
            assertThat(plan.volumes().get(0).memberSlots()).containsExactly("s:0", "s:1");
            assertThat(plan.ruleOutcomes().get(0).matchedDisks()).isEqualTo(2);   // 960 급은 축 필터 밖
        }

        @Test
        @DisplayName("4 TB 지정 — MegaRAID 표기(3.637 TB)와 sas3ircu 표기(3815447 MB)를 같은 계급으로 잡는다(T4)")
        void specified_matchesBothCliNotations() {
            RaidPlan plan = plan(
                    mega(disk("h:0", "HDD", "SAS", S4T), disk("h:1", "HDD", "SAS", "3815447 MB")),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.HDD, DiskTransportRequirement.AUTO,
                            tb(4), DiskCountMode.EXACT, 2, DiskGroupRole.DATA));

            assertThat(plan.volumes()).hasSize(1);
            assertThat(plan.volumes().get(0).memberSlots()).containsExactly("h:0", "h:1");
        }

        @Test
        @DisplayName("AUTO 용량은 모든 계급을 잡되 계급별로 그룹이 갈린다")
        void autoCapacity_matchesAllButGroupsByClass() {
            RaidPlan plan = plan(
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480),
                            disk("s:2", "SSD", "SATA", S960), disk("s:3", "SSD", "SATA", S960)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EACH, 2, DiskGroupRole.DATA));

            assertThat(plan.volumes()).extracting(PlannedVolume::name)
                    .containsExactly("spvR1V1", "spvR1V2");
            assertThat(plan.volumes().get(0).memberSlots()).containsExactly("s:0", "s:1");
            assertThat(plan.volumes().get(1).memberSlots()).containsExactly("s:2", "s:3");
        }
    }

    // ── 개수 축 — 개(EXACT) · 개씩(EACH) · 개 이상(AT_LEAST) × 그룹 크기 전수(E3.5-7-a D2) ───────

    @Nested
    @DisplayName("개수 축 — 그룹 크기별 소비 여부 전수 (개 · 개씩 · 개 이상 — E3.5-7-a)")
    class CountAxis {

        private RaidPlan planWithPool(int poolSize, DiskCountMode mode, int count) {
            RaidPhysicalDisk[] disks = new RaidPhysicalDisk[poolSize];
            for (int i = 0; i < poolSize; i++) {
                disks[i] = disk("p:" + i, "SSD", "SATA", S480);
            }
            return plan(mega(disks), rule(RaidLevel.RAID1, DiskTypeRequirement.SSD,
                    DiskTransportRequirement.AUTO, auto(), mode, count, DiskGroupRole.DATA));
        }

        @ParameterizedTest(name = "EACH 2 × 그룹 {0}대 → 볼륨 {1} · 미배정 {2}")
        @CsvSource({"1, 0, 1", "2, 1, 0", "3, 0, 3", "4, 2, 0", "6, 3, 0"})
        void each_consumesWhenGroupSizeIsMultipleOfN(int poolSize, int volumes, int unassigned) {
            RaidPlan plan = planWithPool(poolSize, DiskCountMode.EACH, 2);
            assertThat(plan.volumes()).hasSize(volumes);
            assertThat(plan.unassigned()).hasSize(unassigned);
            assertThat(plan.ruleOutcomes().get(0).consumedDisks()).isEqualTo(volumes * 2);
        }

        @ParameterizedTest(name = "AT_LEAST 2 × 그룹 {0}대 → 볼륨 {1}(멤버 {2})")
        @CsvSource({"1, 0, 0", "2, 1, 2", "3, 1, 3", "5, 1, 5"})
        void atLeast_absorbsWholeGroupFromN(int poolSize, int volumes, int members) {
            RaidPlan plan = planWithPool(poolSize, DiskCountMode.AT_LEAST, 2);
            assertThat(plan.volumes()).hasSize(volumes);
            if (volumes > 0) {
                assertThat(plan.volumes().get(0).memberSlots()).hasSize(members);
            }
        }

        @Test
        @DisplayName("T2 — 6대 · EACH 2(개씩) 는 2개씩 3볼륨으로 분할 소비한다(배수 분할 — 2026-09-01 뜻의 이관)")
        void eachSix_multipleOfN_splitsIntoThreeVolumes() {
            RaidPlan plan = planWithPool(6, DiskCountMode.EACH, 2);
            assertThat(plan.volumes()).hasSize(3);
            assertThat(plan.volumes()).allSatisfy(v -> assertThat(v.memberSlots()).hasSize(2));
            assertThat(plan.volumes().get(2).name()).isEqualTo("spvR1V3");
            assertThat(plan.ruleOutcomes().get(0).consumedDisks()).isEqualTo(6);
        }

        @Test
        @DisplayName("E3.5-6 — 규칙의 VD 파라미터는 배수 분할된 모든 볼륨에 같은 조립값으로 실린다")
        void vdParameters_propagateToEverySplitVolume() {
            RaidPhysicalDisk[] disks = new RaidPhysicalDisk[4];
            for (int i = 0; i < 4; i++) {
                disks[i] = disk("p:" + i, "SSD", "SATA", S480);
            }
            VdParameters vd = new VdParameters(VdWritePolicy.WRITE_BACK, null, null, null,
                    null, VdDriveCache.OFF, VdBackgroundInit.OFF, VdInitialization.FULL);
            RaidPlan plan = plan(mega(disks), new DiskGroupRuleRequest(RaidLevel.RAID1,
                    DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO, auto(),
                    new DiskCountRequirement(DiskCountMode.EACH, 2), DiskGroupRole.DATA, vd));

            assertThat(plan.volumes()).hasSize(2);
            assertThat(plan.volumes()).allSatisfy(v -> {
                assertThat(v.createOpts()).isEqualTo("wb ra direct strip=256 pdcache=off");
                assertThat(v.setOps()).containsExactly("bgi=off", "accesspolicy=rw");
                assertThat(v.init()).isEqualTo("full");
            });
        }

        @Test
        @DisplayName("E3.5-6 — VD 파라미터 축이 없는 규칙(구 저장본)도 MegaRAID 볼륨은 HII 기본값 8축으로 명시 조립된다")
        void vdParameters_absent_assemblesHiiDefaults() {
            RaidPlan plan = plan(mega(disk("p:0", "SSD", "SATA", S480), disk("p:1", "SSD", "SATA", S480)),
                    new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO, auto(),
                            new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.DATA));

            assertThat(plan.volumes()).singleElement().satisfies(v -> {
                assertThat(v.createOpts()).isEqualTo("wb ra direct strip=256 pdcache=default");
                assertThat(v.setOps()).containsExactly("bgi=on", "accesspolicy=rw");
                assertThat(v.init()).isEqualTo("none");
            });
        }

        @Test
        @DisplayName("E3.5-6 — 축이 없는 계열(MPT_IR)의 볼륨은 규칙이 축을 실어 왔어도 조립 3필드가 비어 있다(sas3ircu 는 쓰지 않는다)")
        void vdParameters_irFamily_carriesNothing() {
            VdParameters vd = new VdParameters(VdWritePolicy.WRITE_BACK, null, null, null, null, null, null, VdInitialization.FULL);
            RaidPlan plan = plan(inv(RaidChipFamily.MPT_IR, List.of(disk("p:0", "SSD", "SATA", S480), disk("p:1", "SSD", "SATA", S480)), List.of()),
                    new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO, auto(),
                            new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.DATA, vd));

            assertThat(plan.volumes()).singleElement().satisfies(v -> {
                assertThat(v.createOpts()).isNull();
                assertThat(v.setOps()).isEmpty();
                assertThat(v.init()).isNull();
            });
        }

        @Test
        @DisplayName("T2b — 5대 · EACH 2(개씩) 는 배수가 아니라 미소비 · 사유에 배수 아님이 남는다")
        void eachFive_notMultiple_leavesReasonAndZeroConsumption() {
            RaidPlan plan = planWithPool(5, DiskCountMode.EACH, 2);
            assertThat(plan.volumes()).isEmpty();
            assertThat(plan.ruleOutcomes().get(0).consumedNothing()).isTrue();
            assertThat(plan.unassigned()).allSatisfy(u ->
                    assertThat(u.reason()).contains("2개씩 조건에 5대(배수 아님)라 미소비"));
        }
    }

    // ── 개(EXACT) — 첫 그룹 · 슬롯 순 n 장 · 부분 소비(E3.5-7-a D2) ──────────────────────

    @Nested
    @DisplayName("개(EXACT) — 크기 ≥ n 인 첫 그룹에서 슬롯 순 n 장 한 묶음만 · 나머지는 후행으로(E3.5-7-a)")
    class ExactFirstBundle {

        private RaidPlan planWithPool(int poolSize, DiskGroupRuleRequest... rules) {
            RaidPhysicalDisk[] disks = new RaidPhysicalDisk[poolSize];
            for (int i = 0; i < poolSize; i++) {
                disks[i] = disk("p:" + i, "SSD", "SATA", S480);
            }
            return plan(mega(disks), rules);
        }

        private DiskGroupRuleRequest ssd(RaidLevel level, DiskCountMode mode, int count, DiskGroupRole role) {
            return rule(level, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO, auto(), mode, count, role);
        }

        @ParameterizedTest(name = "EXACT 2 × 그룹 {0}대 → 볼륨 {1} · 미배정 {2}")
        @CsvSource({"1, 0, 1", "2, 1, 0", "3, 1, 1", "4, 1, 2", "6, 1, 4"})
        void exact_takesOneBundleFromFirstGroup(int poolSize, int volumes, int unassigned) {
            RaidPlan plan = planWithPool(poolSize, ssd(RaidLevel.RAID1, DiskCountMode.EXACT, 2, DiskGroupRole.DATA));
            assertThat(plan.volumes()).hasSize(volumes);
            assertThat(plan.unassigned()).hasSize(unassigned);
            assertThat(plan.ruleOutcomes().get(0).consumedDisks()).isEqualTo(volumes * 2);
            if (volumes == 1) {
                assertThat(plan.volumes().get(0).name()).isEqualTo("spvR1V1");
                assertThat(plan.volumes().get(0).memberSlots()).containsExactly("p:0", "p:1");
            }
        }

        @Test
        @DisplayName("1대 · EXACT 2 → 미소비 · 사유 '2장에 못 미침'")
        void exact_belowN_leavesReason() {
            RaidPlan plan = planWithPool(1, ssd(RaidLevel.RAID1, DiskCountMode.EXACT, 2, DiskGroupRole.DATA));
            assertThat(plan.ruleOutcomes().get(0).consumedNothing()).isTrue();
            assertThat(plan.unassigned()).singleElement()
                    .satisfies(u -> assertThat(u.reason()).contains("2개 조건에 1대(2장에 못 미침)라 미소비"));
        }

        @Test
        @DisplayName("남은 디스크의 사유 — 후행이 없으면 '한 묶음만 가져갑니다' 가 미배정 사유로 남는다")
        void exact_leftoverCarriesReason() {
            RaidPlan plan = planWithPool(4, ssd(RaidLevel.RAID1, DiskCountMode.EXACT, 2, DiskGroupRole.DATA));
            assertThat(plan.unassigned()).hasSize(2).allSatisfy(u ->
                    assertThat(u.reason()).contains("규칙 1 · 2개는 한 묶음만 가져갑니다"));
        }

        @Test
        @DisplayName("두 스펙 그룹(480 × 2 · 960 × 2) · EXACT 2 → 첫 그룹만 소비, 둘째 그룹은 '이미 소비' 사유로 후행에 남는다")
        void exact_touchesFirstGroupOnly() {
            RaidPlan plan = plan(
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480),
                            disk("m:0", "SSD", "SATA", S960), disk("m:1", "SSD", "SATA", S960)),
                    ssd(RaidLevel.RAID1, DiskCountMode.EXACT, 2, DiskGroupRole.DATA));
            assertThat(plan.volumes()).singleElement()
                    .satisfies(v -> assertThat(v.memberSlots()).containsExactly("s:0", "s:1"));
            assertThat(plan.unassigned()).extracting(u -> u.slot()).containsExactly("m:0", "m:1");
            assertThat(plan.unassigned()).allSatisfy(u -> assertThat(u.reason()).contains("이미 소비"));
        }

        @Test
        @DisplayName("RAID 없음 · EXACT 1 → 첫 1장만 패스스루(사용자 제기 (a) — '개씩' 1 은 전부)")
        void raidNone_exactOne_firstDiskOnly() {
            RaidPlan once = planWithPool(4, ssd(null, DiskCountMode.EXACT, 1, DiskGroupRole.NONE));
            assertThat(once.passthroughs()).extracting(p -> p.slot()).containsExactly("p:0");
            assertThat(once.unassigned()).hasSize(3);

            RaidPlan each = planWithPool(4, ssd(null, DiskCountMode.EACH, 1, DiskGroupRole.NONE));
            assertThat(each.passthroughs()).hasSize(4);
        }

        @Test
        @DisplayName("사례 (c) — 같은 SSD 6장 · [RAID1 개 2 (OS), RAID5 개 이상 3] → RAID1(2) + RAID5(4) · OS 는 RAID1")
        void caseC_osRaid1_thenRaid5Remainder() {
            RaidPlan plan = planWithPool(6,
                    ssd(RaidLevel.RAID1, DiskCountMode.EXACT, 2, DiskGroupRole.OS),
                    ssd(RaidLevel.RAID5, DiskCountMode.AT_LEAST, 3, DiskGroupRole.BY_PRIORITY));
            assertThat(plan.volumes()).extracting(PlannedVolume::name).containsExactly("spvR1V1", "spvR2V1");
            assertThat(plan.volumes().get(0).memberSlots()).containsExactly("p:0", "p:1");
            assertThat(plan.volumes().get(0).role()).isEqualTo(PlannedVolumeRole.OS);
            assertThat(plan.volumes().get(1).memberSlots()).containsExactly("p:2", "p:3", "p:4", "p:5");
            assertThat(plan.volumes().get(1).role()).isEqualTo(PlannedVolumeRole.DATA);
            assertThat(plan.unassigned()).isEmpty();
        }

        @Test
        @DisplayName("사례 (b) — SSD 4장 · [RAID1 개 2, RAID5 개 이상 3] 은 RAID1 + 2장 미배정(3장 미만), 순서를 뒤집으면 RAID5(4)")
        void caseB_orderDecides() {
            RaidPlan asGiven = planWithPool(4,
                    ssd(RaidLevel.RAID1, DiskCountMode.EXACT, 2, DiskGroupRole.BY_PRIORITY),
                    ssd(RaidLevel.RAID5, DiskCountMode.AT_LEAST, 3, DiskGroupRole.BY_PRIORITY));
            assertThat(asGiven.volumes()).singleElement()
                    .satisfies(v -> assertThat(v.level()).isEqualTo(RaidLevel.RAID1));
            assertThat(asGiven.unassigned()).hasSize(2).allSatisfy(u ->
                    assertThat(u.reason()).contains("3개 이상 조건에 2대(3장 미만)라 미소비"));

            RaidPlan reversed = planWithPool(4,
                    ssd(RaidLevel.RAID5, DiskCountMode.AT_LEAST, 3, DiskGroupRole.BY_PRIORITY),
                    ssd(RaidLevel.RAID1, DiskCountMode.EXACT, 2, DiskGroupRole.BY_PRIORITY));
            assertThat(reversed.volumes()).singleElement().satisfies(v -> {
                assertThat(v.level()).isEqualTo(RaidLevel.RAID5);
                assertThat(v.memberSlots()).hasSize(4);
            });
            assertThat(reversed.unassigned()).isEmpty();
        }
    }

    // ── 레벨 축 — 5레벨 볼륨 + RAID 없음 패스스루 ───────────────────────────

    @Nested
    @DisplayName("레벨 축 — 레벨별 유효 용량과 RAID 없음의 개수 축 적용")
    class LevelAxis {

        @ParameterizedTest(name = "{0} × {1}대 → usable = {2} × 디스크 1대")
        @CsvSource({"RAID0, 2, 2", "RAID1, 2, 1", "RAID5, 3, 2", "RAID6, 4, 2", "RAID10, 4, 2"})
        void eachLevel_buildsVolumeWithUsableCapacity(RaidLevel level, int members, int usableFactor) {
            RaidPhysicalDisk[] disks = new RaidPhysicalDisk[members];
            for (int i = 0; i < members; i++) {
                disks[i] = disk("p:" + i, "SSD", "SATA", S100GIB);
            }
            RaidPlan plan = plan(mega(disks), rule(level, DiskTypeRequirement.SSD,
                    DiskTransportRequirement.AUTO, auto(), DiskCountMode.EXACT, members, DiskGroupRole.DATA));

            assertThat(plan.volumes()).hasSize(1);
            PlannedVolume volume = plan.volumes().get(0);
            assertThat(volume.name()).isEqualTo("spvR1V1");
            assertThat(volume.level()).isEqualTo(level);
            assertThat(volume.usableBytes()).isEqualTo(usableFactor * PER_100GIB);
        }

        @Test
        @DisplayName("T15 — RAID 없음 · EXACT 2(개) 는 첫 그룹에서 슬롯 순 2장만 패스스루 — 3대면 2장 + 1장 미배정(E3.5-7-a D2)")
        void raidNone_exactTakesFirstBundleOnly() {
            RaidPlan matched = plan(
                    mega(disk("p:0", "SSD", "SATA", S480), disk("p:1", "SSD", "SATA", S480)),
                    rule(null, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.DATA));
            assertThat(matched.volumes()).isEmpty();
            assertThat(matched.passthroughs()).hasSize(2);

            RaidPlan partial = plan(
                    mega(disk("p:0", "SSD", "SATA", S480), disk("p:1", "SSD", "SATA", S480),
                            disk("p:2", "SSD", "SATA", S480)),
                    rule(null, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.DATA));
            assertThat(partial.passthroughs()).extracting(p -> p.slot()).containsExactly("p:0", "p:1");
            assertThat(partial.unassigned()).singleElement()
                    .satisfies(u -> assertThat(u.reason()).contains("한 묶음만"));
        }

        @Test
        @DisplayName("RAID 없음 · AT_LEAST 1 — 그룹 전체가 각각 단독 디스크로 보장된다")
        void raidNone_atLeast_selectsWholeGroup() {
            RaidPlan plan = plan(
                    mega(disk("p:0", "SSD", "SATA", S480), disk("p:1", "SSD", "SATA", S480),
                            disk("p:2", "SSD", "SATA", S480)),
                    rule(null, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.AT_LEAST, 1, DiskGroupRole.NONE));
            assertThat(plan.passthroughs()).hasSize(3);
            assertThat(plan.passthroughs()).allSatisfy(p ->
                    assertThat(p.role()).isEqualTo(PlannedVolumeRole.NONE));
        }
    }

    // ── 흐름 — 규칙 순서 소비와 후행 흘림 ────────────────────────────────────

    @Nested
    @DisplayName("흐름 — 규칙 순서 소비 · 후행 흘림(T1 · T17)")
    class FlowThrough {

        @Test
        @DisplayName("T1 — 실측형: SSD 480 × 2 + HDD 4TB × 4 → 볼륨 2 · 미배정 0")
        void realisticTwoRules_consumeAll() {
            RaidPlan plan = plan(
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480),
                            disk("h:0", "HDD", "SAS", S4T), disk("h:1", "HDD", "SAS", S4T),
                            disk("h:2", "HDD", "SAS", S4T), disk("h:3", "HDD", "SAS", S4T)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            gb(480), DiskCountMode.EXACT, 2, DiskGroupRole.OS),
                    rule(RaidLevel.RAID5, DiskTypeRequirement.HDD, DiskTransportRequirement.AUTO,
                            tb(4), DiskCountMode.AT_LEAST, 3, DiskGroupRole.DATA));

            assertThat(plan.volumes()).extracting(PlannedVolume::name)
                    .containsExactly("spvR1V1", "spvR2V1");
            assertThat(plan.unassigned()).isEmpty();
        }

        @Test
        @DisplayName("T17 — CP1 검수 시나리오: 960 그룹(3대)은 규칙 1 미소비 후 규칙 2가 온전히 받는다")
        void reviewScenario_groupFlowsToLaterRule() {
            RaidPlan plan = plan(
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480),
                            disk("m:0", "SSD", "SATA", S960), disk("m:1", "SSD", "SATA", S960),
                            disk("m:2", "SSD", "SATA", S960),
                            disk("h:0", "HDD", "SAS", S4T), disk("h:1", "HDD", "SAS", S4T),
                            disk("h:2", "HDD", "SAS", S4T)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.BY_PRIORITY),
                    rule(RaidLevel.RAID5, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.AT_LEAST, 3, DiskGroupRole.BY_PRIORITY));

            assertThat(plan.volumes()).extracting(PlannedVolume::name)
                    .containsExactly("spvR1V1", "spvR2V1", "spvR2V2");
            assertThat(plan.volumes().get(0).memberSlots()).containsExactly("s:0", "s:1");
            assertThat(plan.volumes().get(1).memberSlots()).containsExactly("m:0", "m:1", "m:2");
            assertThat(plan.volumes().get(2).memberSlots()).containsExactly("h:0", "h:1", "h:2");
            assertThat(plan.unassigned()).isEmpty();
            // OS — rank 동률(SATA SSD)에서 SMALLER_FIRST 라 RAID1(480 급)이 이긴다
            assertThat(plan.volumes().get(0).role()).isEqualTo(PlannedVolumeRole.OS);
            assertThat(plan.volumes().get(1).role()).isEqualTo(PlannedVolumeRole.DATA);
        }

        @Test
        @DisplayName("T6 — 매칭 0대 규칙은 건너뛰고(D-4) 다음 규칙은 그대로 적용된다")
        void zeroMatch_skipsRuleWithoutSideEffect() {
            RaidPlan plan = plan(
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.DATA),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.OS));

            assertThat(plan.ruleOutcomes().get(0).matchedDisks()).isZero();
            assertThat(plan.volumes()).singleElement()
                    .satisfies(v -> assertThat(v.name()).isEqualTo("spvR2V1"));
        }
    }

    // ── 정책 — 기존 볼륨 × 보존 · 파괴 (T9 · T10) ───────────────────────────

    @Nested
    @DisplayName("정책 — 기존 볼륨과 보존 · 파괴(결정 D-7)")
    class ExistingPolicy {

        private final List<RaidExistingVolume> existing = List.of(
                new RaidExistingVolume("VD0", "RAID1", "3.637 TB", "Optl", "", List.of("h:0", "h:1"), null));

        @Test
        @DisplayName("T9 — 보존 + 기존 볼륨 존재 = EXISTING_CONFIG 거절")
        void preserve_withExistingVolumes_rejected() {
            RaidPlanOutcome outcome = RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.DATA)),
                    VolumePriorityRuleRequest.defaults(),
                    inv(RaidChipFamily.MEGARAID,
                            List.of(disk("h:0", "HDD", "SAS", S4T), disk("h:1", "HDD", "SAS", S4T)), existing),
                    RaidExistingConfigPolicy.PRESERVE);

            assertThat(outcome).isInstanceOf(RaidPlanRejection.class);
            assertThat(((RaidPlanRejection) outcome).code()).isEqualTo(RaidPlanRejection.EXISTING_CONFIG);
        }

        @Test
        @DisplayName("T10 — 파괴 + 기존 볼륨 존재 = 선행 삭제 플래그 + 전 디스크로 계획")
        void destroy_withExistingVolumes_plansWithDeleteFirst() {
            RaidPlan plan = planOf(RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.DATA)),
                    VolumePriorityRuleRequest.defaults(),
                    inv(RaidChipFamily.MEGARAID,
                            List.of(disk("h:0", "HDD", "SAS", S4T), disk("h:1", "HDD", "SAS", S4T)), existing),
                    RaidExistingConfigPolicy.DESTROY));

            assertThat(plan.deleteExistingFirst()).isTrue();
            assertThat(plan.volumes()).hasSize(1);
        }

        @Test
        @DisplayName("W13 — 보존 + spvR 잔여만 = 계획 성립 + 선행 삭제(외부 기준 정밀화, E3.5-4 Q1)")
        void preserve_withOurResidueOnly_plansWithDeleteFirst() {
            List<RaidExistingVolume> residue = List.of(new RaidExistingVolume(
                    "VD0", "RAID1", "3.637 TB", "Optl", "spvR1V1", List.of("h:0", "h:1"), null));
            RaidPlan plan = planOf(RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.DATA)),
                    VolumePriorityRuleRequest.defaults(),
                    inv(RaidChipFamily.MEGARAID,
                            List.of(disk("h:0", "HDD", "SAS", S4T), disk("h:1", "HDD", "SAS", S4T)), residue),
                    RaidExistingConfigPolicy.PRESERVE));

            assertThat(plan.deleteExistingFirst()).isTrue();   // 잔여는 정책 불문 재구성 대상
            assertThat(plan.volumes()).hasSize(1);
        }

        @Test
        @DisplayName("기존 볼륨이 없으면 보존 정책도 파괴와 같은 계획을 낸다(선행 삭제 없음)")
        void preserve_withoutExistingVolumes_plansNormally() {
            RaidPlan plan = planOf(RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.DATA)),
                    VolumePriorityRuleRequest.defaults(),
                    mega(disk("h:0", "HDD", "SAS", S4T), disk("h:1", "HDD", "SAS", S4T)),
                    RaidExistingConfigPolicy.PRESERVE));

            assertThat(plan.deleteExistingFirst()).isFalse();
            assertThat(plan.volumes()).hasSize(1);
        }
    }

    // ── 칩 계열 한계 (T7 · T8) ───────────────────────────────────────────────

    @Nested
    @DisplayName("칩 계열 한계 — 위반은 계획 전체 거절")
    class ChipLimits {

        @Test
        @DisplayName("T8 — MPT_IR 의 RAID1 은 정확히 2대: EXACT 3 볼륨은 MEMBER_COUNT 거절")
        void ir_raid1WithThreeMembers_rejected() {
            RaidPlanOutcome outcome = RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.HDD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 3, DiskGroupRole.DATA)),
                    VolumePriorityRuleRequest.defaults(),
                    inv(RaidChipFamily.MPT_IR, List.of(
                            disk("1:0", "HDD", "SAS", S4T), disk("1:1", "HDD", "SAS", S4T),
                            disk("1:2", "HDD", "SAS", S4T)), List.of()),
                    RaidExistingConfigPolicy.DESTROY);

            assertThat(outcome).isInstanceOf(RaidPlanRejection.class);
            RaidPlanRejection rejection = (RaidPlanRejection) outcome;
            assertThat(rejection.code()).isEqualTo(RaidPlanRejection.MEMBER_COUNT);
            assertThat(rejection.detail()).contains("spvR1V1").contains("정확히 2대");
        }

        @Test
        @DisplayName("T7 — MPT_IR 볼륨 3개 계획은 VOLUME_LIMIT 거절(한계 2)")
        void ir_threeVolumes_rejected() {
            RaidPlanOutcome outcome = RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EACH, 2, DiskGroupRole.DATA)),
                    VolumePriorityRuleRequest.defaults(),
                    inv(RaidChipFamily.MPT_IR, List.of(
                            disk("1:0", "SSD", "SATA", S480), disk("1:1", "SSD", "SATA", S480),
                            disk("1:2", "SSD", "SATA", S960), disk("1:3", "SSD", "SATA", S960),
                            disk("1:4", "HDD", "SAS", S4T), disk("1:5", "HDD", "SAS", S4T)), List.of()),
                    RaidExistingConfigPolicy.DESTROY);

            assertThat(outcome).isInstanceOf(RaidPlanRejection.class);
            assertThat(((RaidPlanRejection) outcome).code()).isEqualTo(RaidPlanRejection.VOLUME_LIMIT);
        }

        @Test
        @DisplayName("MEGARAID 는 RAID1 3대 · 볼륨 3개 전부 허용(수량 제약은 실측 표본 후)")
        void megaRaid_permitsSameShapes() {
            RaidPlan threeMember = plan(
                    mega(disk("p:0", "SSD", "SATA", S480), disk("p:1", "SSD", "SATA", S480),
                            disk("p:2", "SSD", "SATA", S480)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 3, DiskGroupRole.DATA));
            assertThat(threeMember.volumes()).hasSize(1);

            RaidPlan threeVolumes = plan(
                    mega(disk("1:0", "SSD", "SATA", S480), disk("1:1", "SSD", "SATA", S480),
                            disk("1:2", "SSD", "SATA", S960), disk("1:3", "SSD", "SATA", S960),
                            disk("1:4", "HDD", "SAS", S4T), disk("1:5", "HDD", "SAS", S4T)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EACH, 2, DiskGroupRole.DATA));
            assertThat(threeVolumes.volumes()).hasSize(3);
        }

        @Test
        @DisplayName("카드 미감지(null)면 한계 검증을 건너뛴다 — 관용")
        void noCard_skipsLimitValidation() {
            RaidPlan plan = planOf(RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.HDD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 3, DiskGroupRole.DATA)),
                    VolumePriorityRuleRequest.defaults(),
                    inv(null, List.of(disk("1:0", "HDD", "SAS", S4T), disk("1:1", "HDD", "SAS", S4T),
                            disk("1:2", "HDD", "SAS", S4T)), List.of()),
                    RaidExistingConfigPolicy.DESTROY));
            assertThat(plan.volumes()).hasSize(1);
        }
    }

    // ── 역할 · OS 판정 (T11 · T12 · T16) ─────────────────────────────────────

    @Nested
    @DisplayName("역할 — OS 고정 · 우선순위 · 강하 없음")
    class RoleAssignment {

        @Test
        @DisplayName("T11 — OS 고정 규칙이 볼륨 둘을 내면 첫 볼륨만 OS · 둘째는 DATA")
        void osFixedRule_firstVolumeOnly() {
            RaidPlan plan = plan(
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480),
                            disk("s:2", "SSD", "SATA", S960), disk("s:3", "SSD", "SATA", S960)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EACH, 2, DiskGroupRole.OS));

            assertThat(plan.volumes()).extracting(PlannedVolume::role)
                    .containsExactly(PlannedVolumeRole.OS, PlannedVolumeRole.DATA);
            assertThat(plan.osAbsenceReason()).isNull();
        }

        @Test
        @DisplayName("T16 — OS 고정 규칙이 0대 건너뜀이면 OS 지정 없음 + 사유 · BY_PRIORITY 강하 없음(Q3)")
        void osFixedRuleEmpty_noFallback() {
            RaidPlan plan = plan(
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.OS),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.BY_PRIORITY));

            assertThat(plan.osAbsenceReason()).contains("규칙 1");
            assertThat(plan.volumes()).singleElement()
                    .satisfies(v -> assertThat(v.role()).isEqualTo(PlannedVolumeRole.DATA));
        }

        @Test
        @DisplayName("T12 — BY_PRIORITY: 기본 우선순위에서 NVMe SSD 가 SATA SSD 를 이긴다(rankOf)")
        void byPriority_rankDecidesOs() {
            RaidPlan plan = plan(
                    mega(disk("t:0", "SSD", "SATA", S480), disk("t:1", "SSD", "SATA", S480),
                            disk("n:0", "SSD", "NVME", S480), disk("n:1", "SSD", "NVME", S480)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EACH, 2, DiskGroupRole.BY_PRIORITY));

            PlannedVolume os = plan.volumes().stream()
                    .filter(v -> v.role() == PlannedVolumeRole.OS).findFirst().orElseThrow();
            assertThat(os.memberSlots()).containsExactly("n:0", "n:1");
        }

        @Test
        @DisplayName("동순위는 그 행의 용량 순서로 — LARGER_FIRST 면 960 급이 OS")
        void samRank_capacityOrderBreaksTie() {
            List<VolumePriorityRuleRequest> largerFirst = List.of(new VolumePriorityRuleRequest(
                    DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, CapacityOrder.LARGER_FIRST));
            RaidPlan plan = planOf(RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EACH, 2, DiskGroupRole.BY_PRIORITY)),
                    largerFirst,
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480),
                            disk("m:0", "SSD", "SATA", S960), disk("m:1", "SSD", "SATA", S960)),
                    RaidExistingConfigPolicy.DESTROY));

            PlannedVolume os = plan.volumes().stream()
                    .filter(v -> v.role() == PlannedVolumeRole.OS).findFirst().orElseThrow();
            assertThat(os.memberSlots()).containsExactly("m:0", "m:1");
        }

        @Test
        @DisplayName("우선순위 행이 빈 목록이면 전부 무순위 — 열거 순 첫 항목이 OS")
        void emptyPriorities_enumerationOrderWins() {
            RaidPlan plan = planOf(RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.BY_PRIORITY)),
                    List.of(),
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480),
                            disk("n:0", "SSD", "NVME", S480), disk("n:1", "SSD", "NVME", S480)),
                    RaidExistingConfigPolicy.DESTROY));

            assertThat(plan.volumes().get(0).memberSlots()).containsExactly("s:0", "s:1");
            assertThat(plan.volumes().get(0).role()).isEqualTo(PlannedVolumeRole.OS);
        }

        @Test
        @DisplayName("RAID 없음 단독 디스크도 BY_PRIORITY 로 OS 가 될 수 있다")
        void passthrough_canBeOs() {
            RaidPlan plan = plan(
                    mega(disk("p:0", "SSD", "NVME", S480)),
                    rule(null, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME,
                            auto(), DiskCountMode.AT_LEAST, 1, DiskGroupRole.BY_PRIORITY));

            assertThat(plan.passthroughs()).singleElement()
                    .satisfies(p -> assertThat(p.role()).isEqualTo(PlannedVolumeRole.OS));
        }

        @Test
        @DisplayName("역할 NONE 규칙의 볼륨은 NONE — OS 후보 자체가 아니다")
        void noneRole_neverOs() {
            RaidPlan plan = plan(
                    mega(disk("s:0", "SSD", "SATA", S480), disk("s:1", "SSD", "SATA", S480)),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.NONE));

            assertThat(plan.volumes().get(0).role()).isEqualTo(PlannedVolumeRole.NONE);
            assertThat(plan.osAbsenceReason()).isNull();
        }
    }

    // ── 디스크 제외 (T13) ────────────────────────────────────────────────────

    @Nested
    @DisplayName("디스크 제외 — 비가용 상태 · 해석 불가는 사유와 함께 미배정")
    class DiskExclusion {

        @ParameterizedTest(name = "제외 사유 — {1}")
        @MethodSource("badDisks")
        void unusableDisk_excludedWithReason(RaidPhysicalDisk bad, String reasonPart) {
            RaidPlan plan = plan(
                    inv(RaidChipFamily.MEGARAID,
                            List.of(bad, disk("ok:0", "SSD", "SATA", S480), disk("ok:1", "SSD", "SATA", S480)),
                            List.of()),
                    rule(RaidLevel.RAID1, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.EXACT, 2, DiskGroupRole.DATA));

            assertThat(plan.volumes()).hasSize(1);   // 정상 2대는 그대로 볼륨이 된다
            assertThat(plan.unassigned()).singleElement().satisfies(u -> {
                assertThat(u.slot()).isEqualTo("bad:0");
                assertThat(u.reason()).contains(reasonPart);
            });
        }

        static Stream<org.junit.jupiter.params.provider.Arguments> badDisks() {
            return Stream.of(
                    org.junit.jupiter.params.provider.Arguments.of(
                            new RaidPhysicalDisk("bad:0", "SSD", "SATA", S480, "UBad", "M", "S", null), "상태 UBad"),
                    org.junit.jupiter.params.provider.Arguments.of(
                            new RaidPhysicalDisk("bad:0", "SSD", "SATA", S480, null, "M", "S", null), "상태 미상"),
                    org.junit.jupiter.params.provider.Arguments.of(
                            new RaidPhysicalDisk("bad:0", "TAPE", "SATA", S480, "Onln", "M", "S", null), "종류"),
                    org.junit.jupiter.params.provider.Arguments.of(
                            new RaidPhysicalDisk("bad:0", "SSD", "FC", S480, "Onln", "M", "S", null), "전송"),
                    org.junit.jupiter.params.provider.Arguments.of(
                            new RaidPhysicalDisk("bad:0", "SSD", "SATA", "n/a", "Onln", "M", "S", null), "환산"));
        }
    }

    // ── 실측 종단 (T14) — 파서 → planner 통관 ───────────────────────────────

    @Nested
    @DisplayName("실측 종단 — E3.5-1 픽스처 원문을 파서 → planner 로 통관")
    class RealFixtureEndToEnd {

        private final RaidInventoryParser parser = new RaidInventoryParser(new ObjectMapper());

        private static String fixture(String name) {
            try (var in = RaidPlannerTest.class.getResourceAsStream("/raid/" + name)) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException | NullPointerException e) {
                throw new UncheckedIOException(new IOException("픽스처 없음: " + name, e));
            }
        }

        private static String b64(String raw) {
            return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        private RaidInventory megaRaidInventory() {
            return parser.parse("{\"tool\":\"storcli64\",\"lspci_b64\":\"" + b64(fixture("mr-lspci-nnvv.txt"))
                    + "\",\"pd_b64\":\"" + b64(fixture("mr-pd-all.json"))
                    + "\",\"vd_b64\":\"" + b64(fixture("mr-vd-all.json"))
                    + "\",\"c0_b64\":\"" + b64(fixture("mr-c0-show-all.json")) + "\"}");
        }

        @Test
        @DisplayName("T14a — MegaRAID 실측(SSD 480 × 2 + HDD 4TB × 6 · 기존 VD 2): 파괴 정책으로 볼륨 2 계획")
        void megaRaidFixture_destroyPlan() {
            RaidPlan plan = planOf(RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO,
                                    gb(480), DiskCountMode.EXACT, 2, DiskGroupRole.OS),
                            rule(RaidLevel.RAID5, DiskTypeRequirement.HDD, DiskTransportRequirement.AUTO,
                                    tb(4), DiskCountMode.AT_LEAST, 3, DiskGroupRole.DATA)),
                    VolumePriorityRuleRequest.defaults(), megaRaidInventory(),
                    RaidExistingConfigPolicy.DESTROY));

            assertThat(plan.deleteExistingFirst()).isTrue();   // 실측 카드에 VD 2가 남아 있다
            assertThat(plan.volumes()).hasSize(2);
            PlannedVolume os = plan.volumes().get(0);
            assertThat(os.name()).isEqualTo("spvR1V1");
            assertThat(os.memberSlots()).containsExactly("252:0", "252:1");
            assertThat(os.role()).isEqualTo(PlannedVolumeRole.OS);
            PlannedVolume data = plan.volumes().get(1);
            assertThat(data.memberSlots()).hasSize(6);
            long perHdd = RaidReportedSize.parse("3.637 TB").orElseThrow();
            assertThat(data.usableBytes()).isEqualTo(RaidLevel.RAID5.usableDisks(6) * perHdd);
            assertThat(plan.unassigned()).isEmpty();
        }

        @Test
        @DisplayName("T14b — MegaRAID 실측 + 보존 정책: 기존 VD 2 때문에 EXISTING_CONFIG 거절(T9 실측판)")
        void megaRaidFixture_preserveRejected() {
            RaidPlanOutcome outcome = RaidPlanner.plan(List.of(),
                    VolumePriorityRuleRequest.defaults(), megaRaidInventory(),
                    RaidExistingConfigPolicy.PRESERVE);
            assertThat(outcome).isInstanceOf(RaidPlanRejection.class);
            assertThat(((RaidPlanRejection) outcome).code()).isEqualTo(RaidPlanRejection.EXISTING_CONFIG);
        }

        @Test
        @DisplayName("T14c — CRA3338 실측(HDD 4TB × 2 · IR 볼륨 1): 파괴 정책으로 RAID1 1 볼륨 계획")
        void craFixture_destroyPlan() {
            RaidInventory inventory = parser.parse("{\"tool\":\"sas3ircu\",\"lspci_b64\":\""
                    + b64(fixture("cra-lspci-nnvv.txt"))
                    + "\",\"display_b64\":\"" + b64(fixture("cra-display.txt")) + "\"}");

            RaidPlan plan = planOf(RaidPlanner.plan(
                    List.of(rule(RaidLevel.RAID1, DiskTypeRequirement.HDD, DiskTransportRequirement.AUTO,
                            auto(), DiskCountMode.AT_LEAST, 2, DiskGroupRole.OS)),
                    VolumePriorityRuleRequest.defaults(), inventory,
                    RaidExistingConfigPolicy.DESTROY));

            assertThat(plan.deleteExistingFirst()).isTrue();   // IR 볼륨 323 이 남아 있다
            assertThat(plan.volumes()).singleElement().satisfies(v -> {
                assertThat(v.name()).isEqualTo("spvR1V1");
                assertThat(v.level()).isEqualTo(RaidLevel.RAID1);
                assertThat(v.memberSlots()).hasSize(2);
                assertThat(v.role()).isEqualTo(PlannedVolumeRole.OS);
            });
            assertThat(plan.unassigned()).isEmpty();
        }
    }
}
