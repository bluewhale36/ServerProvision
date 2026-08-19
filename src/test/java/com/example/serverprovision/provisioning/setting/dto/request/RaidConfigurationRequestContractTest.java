package com.example.serverprovision.provisioning.setting.dto.request;

import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.provisioning.setting.enums.DiskGroupRole;
import com.example.serverprovision.provisioning.setting.enums.CapacityRequirementMode;
import com.example.serverprovision.provisioning.setting.enums.DiskCapacityUnit;
import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U4-1-1 v2 CP4 — RAID 구성 단계 계약({@link RaidConfigurationRequest}, Layer A Bean Validation).
 * 카드 요구 방향(D4)의 세 경우 · 구 형식 payload · 용량 · 개수 record 의 정합 · flat 판별자를 고정한다.
 */
class RaidConfigurationRequestContractTest {

    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static DiskGroupRuleRequest raid1() {
        return new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 480L, DiskCapacityUnit.GB),
                new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.BY_PRIORITY);
    }

    private static DiskGroupRuleRequest noRaidNvme() {
        return new DiskGroupRuleRequest(null, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME,
                new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                new DiskCountRequirement(DiskCountMode.EXACT, 1), DiskGroupRole.BY_PRIORITY);
    }

    private static RaidConfigurationRequest rc(Long raidCardId, List<DiskGroupRuleRequest> groups) {
        return new RaidConfigurationRequest(raidCardId, groups, VolumePriorityRuleRequest.defaults());
    }

    private static Set<String> violatedPaths(Object bean) {
        return validator.validate(bean).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ==== 카드 요구 방향(D4) ==================================================================

    @Test
    @DisplayName("RAID 묶음 + 카드 null → raidCardPresentWhenRequired 위반")
    void raidRule_withoutCard_violates() {
        assertThat(violatedPaths(rc(null, List.of(raid1())))).contains("raidCardPresentWhenRequired");
    }

    @Test
    @DisplayName("RAID 없음 묶음만 + 카드 null → 통과 · 카드만 있고 묶음 없음 → 통과(역방향은 강제하지 않는다)")
    void oneDirectionOnly() {
        assertThat(violatedPaths(rc(null, List.of(noRaidNvme())))).doesNotContain("raidCardPresentWhenRequired");
        assertThat(violatedPaths(rc(7L, List.of()))).isEmpty();
        assertThat(rc(7L, List.of()).requiresRaidCard()).isFalse();
        assertThat(rc(7L, List.of(raid1())).requiresRaidCard()).isTrue();
    }

    @Test
    @DisplayName("diskGroups 누락 → 빈 목록으로 읽히고 위반 없음 · 판별자는 RAID_CONFIGURATION")
    void missingDiskGroups_isEmptyList() {
        RaidConfigurationRequest bare = rc(null, null);
        assertThat(bare.getDiskGroups()).isEmpty();
        assertThat(violatedPaths(bare)).isEmpty();
        assertThat(bare.processType()).isEqualTo(com.example.serverprovision.provisioning.setting.enums.SettingProcessType.RAID_CONFIGURATION);
    }

    // ==== 중첩 record 정합 ======================================================================

    @Test
    @DisplayName("용량 SPECIFIED 인데 크기 · 단위 없음 / AUTO 인데 값 있음 → capacity.modeConsistent 위반")
    void capacityConsistency() {
        DiskGroupRuleRequest specifiedNoValue = new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD,
                DiskTransportRequirement.SATA, new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, null, null),
                new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.BY_PRIORITY);
        assertThat(violatedPaths(rc(1L, List.of(specifiedNoValue)))).contains("diskGroups[0].capacity.modeConsistent");

        DiskGroupRuleRequest autoWithValue = new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD,
                DiskTransportRequirement.SATA, new DiskCapacityRequirement(CapacityRequirementMode.AUTO, 480L, DiskCapacityUnit.GB),
                new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.BY_PRIORITY);
        assertThat(violatedPaths(rc(1L, List.of(autoWithValue)))).contains("diskGroups[0].capacity.modeConsistent");
    }

    @Test
    @DisplayName("개수 0(누락 포함) → count.value @Min 위반 · 종류/전송/용량/개수 null → @NotNull")
    void countAndRequiredAxes() {
        DiskGroupRuleRequest zeroCount = new DiskGroupRuleRequest(null, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME,
                new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                new DiskCountRequirement(DiskCountMode.EXACT, null), DiskGroupRole.BY_PRIORITY);
        assertThat(violatedPaths(rc(null, List.of(zeroCount)))).contains("diskGroups[0].count.value");

        DiskGroupRuleRequest bare = new DiskGroupRuleRequest(null, null, null, null, null, null);
        assertThat(violatedPaths(rc(null, List.of(bare)))).contains(
                "diskGroups[0].diskType", "diskGroups[0].transport", "diskGroups[0].capacity", "diskGroups[0].count");
    }

    @Test
    @DisplayName("표시 도우미 — capacity.toDisplay / count.toDisplay")
    void displayHelpers() {
        assertThat(raid1().capacity().toDisplay()).isEqualTo("480 GB");
        assertThat(noRaidNvme().capacity().toDisplay()).isEqualTo("자동 탐지");
        assertThat(raid1().count().toDisplay()).isEqualTo("2개");
        assertThat(new DiskCountRequirement(DiskCountMode.AT_LEAST, 3).toDisplay()).isEqualTo("3개 이상");
    }

    // ==== U4-1-2 — 역할 · 볼륨 우선순위 ==========================================================

    private static VolumePriorityRuleRequest priority(DiskTypeRequirement type, DiskTransportRequirement transport) {
        return new VolumePriorityRuleRequest(type, transport, com.example.serverprovision.provisioning.setting.enums.CapacityOrder.SMALLER_FIRST);
    }

    private static DiskGroupRuleRequest withRole(DiskGroupRuleRequest base, DiskGroupRole role) {
        return new DiskGroupRuleRequest(base.raidLevel(), base.diskType(), base.transport(), base.capacity(), base.count(), role);
    }

    @Test
    @DisplayName("역할 null → diskGroups[i].role @NotNull · volumePriorities null → @NotNull, 빈 목록은 명시적 값이라 통과")
    void roleAndPrioritiesRequired() {
        assertThat(violatedPaths(rc(1L, List.of(withRole(raid1(), null))))).contains("diskGroups[0].role");
        assertThat(violatedPaths(new RaidConfigurationRequest(1L, List.of(raid1()), null))).contains("volumePriorities");
        assertThat(violatedPaths(new RaidConfigurationRequest(1L, List.of(raid1()), List.of()))).isEmpty();
    }

    @Test
    @DisplayName("우선순위 행의 (종류, 전송) 중복 → volumePriorityDistinct 위반 · 행 자체의 위반은 volumePriorities[i].* 경로")
    void priorityDistinctAndRowValidation() {
        var dup = List.of(priority(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA),
                priority(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA));
        assertThat(violatedPaths(new RaidConfigurationRequest(1L, List.of(raid1()), dup))).contains("volumePriorityDistinct");

        var bad = List.of(priority(DiskTypeRequirement.HDD, DiskTransportRequirement.NVME),
                priority(DiskTypeRequirement.AUTO, DiskTransportRequirement.SATA));
        assertThat(violatedPaths(new RaidConfigurationRequest(1L, List.of(raid1()), bad)))
                .contains("volumePriorities[0].transportCompatible", "volumePriorities[1].concrete");
    }

    @Test
    @DisplayName("isOsVolumeDeterminable — 묶음 0 개는 항상 참 · OS 고정이면 참 · 우선순위에 따름 + 행 1 이상이면 참 · 전부 Data/없음이거나 행 0 이면 거짓")
    void osVolumeDeterminable() {
        var rows = VolumePriorityRuleRequest.defaults();
        assertThat(new RaidConfigurationRequest(1L, List.of(), List.of()).isOsVolumeDeterminable()).isTrue();
        assertThat(new RaidConfigurationRequest(1L, List.of(withRole(raid1(), DiskGroupRole.OS)), List.of()).isOsVolumeDeterminable()).isTrue();
        assertThat(new RaidConfigurationRequest(1L, List.of(raid1()), rows).isOsVolumeDeterminable()).isTrue();
        assertThat(new RaidConfigurationRequest(1L, List.of(raid1()), List.of()).isOsVolumeDeterminable()).isFalse();
        assertThat(new RaidConfigurationRequest(1L, List.of(withRole(raid1(), DiskGroupRole.DATA), withRole(noRaidNvme(), DiskGroupRole.NONE)), rows)
                .isOsVolumeDeterminable()).isFalse();
    }

    // ==== U4-1-3 — 볼륨 유효 용량 하한 =========================================================

    private static DiskGroupRuleRequest sized(RaidLevel level, long size, DiskCapacityUnit unit, DiskCountMode mode, int count, DiskGroupRole role) {
        return new DiskGroupRuleRequest(level, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, size, unit), new DiskCountRequirement(mode, count), role);
    }

    @Test
    @DisplayName("usableCapacityLowerBoundBytes — RAID1 480 GB × 2 = 480 GB · RAID5 4 TB × 3개 이상 = 8 TB(하한) · RAID 없음 = 1 장 · 자동 탐지 = empty")
    void usableCapacityLowerBound() {
        assertThat(sized(RaidLevel.RAID1, 480, DiskCapacityUnit.GB, DiskCountMode.EXACT, 2, DiskGroupRole.OS).usableCapacityLowerBoundBytes())
                .hasValue(480_000_000_000L);
        assertThat(sized(RaidLevel.RAID5, 4, DiskCapacityUnit.TB, DiskCountMode.AT_LEAST, 3, DiskGroupRole.OS).usableCapacityLowerBoundBytes())
                .hasValue(8_000_000_000_000L);
        assertThat(sized(RaidLevel.RAID10, 1, DiskCapacityUnit.TB, DiskCountMode.EXACT, 4, DiskGroupRole.OS).usableCapacityLowerBoundBytes())
                .hasValue(2_000_000_000_000L);
        assertThat(sized(null, 960, DiskCapacityUnit.GB, DiskCountMode.EXACT, 1, DiskGroupRole.OS).usableCapacityLowerBoundBytes())
                .hasValue(960_000_000_000L);
        assertThat(raid1().usableCapacityLowerBoundBytes()).hasValue(480_000_000_000L); // raid1() = 480 GB × 2
        assertThat(noRaidNvme().usableCapacityLowerBoundBytes()).isEmpty();              // 자동 탐지
    }

    @Test
    @DisplayName("osVolumeCapacityLowerBoundBytes — OS 고정이면 그 묶음만 · 없으면 우선순위에 따름의 최솟값 · 후보 중 자동 탐지가 있으면 empty · 후보 0 이면 empty")
    void osVolumeCapacityLowerBound() {
        var osFixed480 = sized(RaidLevel.RAID1, 480, DiskCapacityUnit.GB, DiskCountMode.EXACT, 2, DiskGroupRole.OS);
        var byPriority960 = sized(null, 960, DiskCapacityUnit.GB, DiskCountMode.EXACT, 1, DiskGroupRole.BY_PRIORITY);
        var byPriority8tb = sized(RaidLevel.RAID5, 4, DiskCapacityUnit.TB, DiskCountMode.AT_LEAST, 3, DiskGroupRole.BY_PRIORITY);
        var data = sized(RaidLevel.RAID1, 100, DiskCapacityUnit.GB, DiskCountMode.EXACT, 2, DiskGroupRole.DATA);

        assertThat(rc(1L, List.of(byPriority960, osFixed480, data)).osVolumeCapacityLowerBoundBytes()).hasValue(480_000_000_000L);
        assertThat(rc(1L, List.of(byPriority960, byPriority8tb, data)).osVolumeCapacityLowerBoundBytes()).hasValue(960_000_000_000L);
        assertThat(rc(1L, List.of(byPriority960, noRaidNvme())).osVolumeCapacityLowerBoundBytes()).isEmpty();
        assertThat(rc(1L, List.of(data)).osVolumeCapacityLowerBoundBytes()).isEmpty();
        assertThat(rc(1L, List.of(byPriority960, osFixed480)).osFixedRuleNo()).isEqualTo(2);
        assertThat(rc(1L, List.of(byPriority960)).osFixedRuleNo()).isZero();
    }
}
