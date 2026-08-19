package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.provisioning.setting.enums.DiskGroupRole;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCapacityRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCountRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskGroupRuleRequest;
import com.example.serverprovision.provisioning.setting.enums.CapacityRequirementMode;
import com.example.serverprovision.provisioning.setting.enums.DiskCapacityUnit;
import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import com.example.serverprovision.provisioning.setting.exception.InvalidDiskGroupException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * U4-1-1 CP4 — {@link DiskGroupRules} 값 규칙 5 개, 규칙마다 통과 1 + 위반 1.
 * 판정 재료는 카드가 준다 — 만들 수 있는 레벨({@code SupportedRaidLevels}) · 캐시 유무({@code RaidLevel.minimumDisks}).
 */
class DiskGroupRulesTest {

    /** 캐시 없음 · RAID0/1 만 — 주력 카드 CRA3338 사양. */
    private static final RaidCard CRA3338 = card(List.of(RaidLevel.RAID0, RaidLevel.RAID1), 0);
    /** 캐시 2GB · RAID0/1/5/6/10 — 주력 카드 9361-8i 사양. */
    private static final RaidCard AVAGO_9361 = card(List.of(RaidLevel.values()), 2);

    private static RaidCard card(List<RaidLevel> levels, int cacheGb) {
        RaidCard card = RaidCard.builder()
                .id(1L).vendor(RaidCardVendor.GIGABYTE).modelName("card")
                .supportedRaidLevels(SupportedRaidLevels.of(levels))
                .cacheCapacity(CacheCapacity.ofGigabytes(cacheGb))
                .ownEnabled(true).ownDeprecated(false).isDeleted(false)
                .build();
        card.recomputeEffective();
        return card;
    }

    private static DiskGroupRuleRequest rule(RaidLevel level, int count) {
        return rule(level, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 480L, DiskCapacityUnit.GB),
                new DiskCountRequirement(DiskCountMode.EXACT, count));
    }

    private static DiskGroupRuleRequest rule(RaidLevel level, DiskTypeRequirement type, DiskTransportRequirement transport,
                                             DiskCapacityRequirement capacity, DiskCountRequirement count) {
        return new DiskGroupRuleRequest(level, type, transport, capacity, count, DiskGroupRole.BY_PRIORITY);
    }

    // ==== 규칙 1 — 카드가 만들 수 없는 레벨 =================================================

    @Test
    @DisplayName("규칙 1 — 카드가 지원하는 레벨은 통과, 못 만드는 레벨은 blockReasonFor 문구로 400")
    void unsupportedLevel() {
        assertThatCode(() -> DiskGroupRules.validate(List.of(rule(RaidLevel.RAID1, 2)), CRA3338))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> DiskGroupRules.validate(List.of(rule(RaidLevel.RAID5, 3)), CRA3338))
                .isInstanceOf(InvalidDiskGroupException.class)
                .hasMessageContaining("1번 묶음")
                .hasMessageContaining("RAID5 를 만들 수 없는 카드입니다")
                .hasMessageContaining("RAID0 · RAID1");
    }

    // ==== 규칙 2 — 최소 디스크 수(카드 캐시 유무 반영) ======================================

    @Test
    @DisplayName("규칙 2 — RAID0 1개: 캐시 없는 카드는 최소 2 라 400, 캐시 카드는 최소 1 이라 통과 (minimumDisks 경로)")
    void tooFewDisks_dependsOnCardCache() {
        assertThatThrownBy(() -> DiskGroupRules.validate(List.of(rule(RaidLevel.RAID0, 1)), CRA3338))
                .isInstanceOf(InvalidDiskGroupException.class)
                .hasMessageContaining("RAID0 을 구성하려면 디스크 2개 이상")
                .hasMessageContaining("지정: 1개");

        assertThatCode(() -> DiskGroupRules.validate(List.of(rule(RaidLevel.RAID0, 1)), AVAGO_9361))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("규칙 2 — RAID5 2개는 캐시 카드라도 최소 3 미달로 400, 3개는 통과")
    void tooFewDisks_raid5() {
        assertThatThrownBy(() -> DiskGroupRules.validate(List.of(rule(RaidLevel.RAID5, 2)), AVAGO_9361))
                .isInstanceOf(InvalidDiskGroupException.class)
                .hasMessageContaining("RAID5 를 구성하려면 디스크 3개 이상");
        assertThatCode(() -> DiskGroupRules.validate(List.of(rule(RaidLevel.RAID5, 3)), AVAGO_9361))
                .doesNotThrowAnyException();
    }

    // ==== 규칙 3 — RAID 없음 묶음의 개수 하한 ================================================

    @Test
    @DisplayName("규칙 3 — RAID 없음 묶음은 카드 없이 통과하고, 개수 0 은 400")
    void singleDiskCount() {
        assertThatCode(() -> DiskGroupRules.validate(List.of(rule(null, 1)), null))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> DiskGroupRules.validate(List.of(rule(null, 0)), null))
                .isInstanceOf(InvalidDiskGroupException.class)
                .hasMessageContaining("RAID 없음 묶음도 디스크 개수는 1 이상");
    }

    // ==== 규칙 4 — 동일 규칙 중복 ==============================================================

    @Test
    @DisplayName("규칙 4 — 다섯 축이 전부 같은 규칙 둘은 400(번호 명시), 개수만 달라도 다른 규칙")
    void duplicateRule() {
        DiskGroupRuleRequest a = rule(RaidLevel.RAID1, 2);
        assertThatThrownBy(() -> DiskGroupRules.validate(List.of(a, rule(null, 1), rule(RaidLevel.RAID1, 2)), CRA3338))
                .isInstanceOf(InvalidDiskGroupException.class)
                .hasMessageContaining("3번 묶음이 1번 묶음과 같은 규칙");

        assertThatCode(() -> DiskGroupRules.validate(List.of(a, rule(RaidLevel.RAID1, 4)), CRA3338))
                .doesNotThrowAnyException();
    }

    // ==== 규칙 5 — 직접 지정 용량 하한 =========================================================

    @Test
    @DisplayName("규칙 5 — 용량 SPECIFIED 인데 크기 0 은 400, AUTO 는 크기 없이 통과")
    void invalidCapacity() {
        DiskGroupRuleRequest zero = rule(RaidLevel.RAID1, DiskTypeRequirement.HDD, DiskTransportRequirement.SAS,
                new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 0L, DiskCapacityUnit.TB),
                new DiskCountRequirement(DiskCountMode.AT_LEAST, 2));
        assertThatThrownBy(() -> DiskGroupRules.validate(List.of(zero), CRA3338))
                .isInstanceOf(InvalidDiskGroupException.class)
                .hasMessageContaining("직접 지정한 용량은 1 이상");

        DiskGroupRuleRequest auto = rule(RaidLevel.RAID1, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                new DiskCountRequirement(DiskCountMode.AT_LEAST, 2));
        assertThatCode(() -> DiskGroupRules.validate(List.of(auto), CRA3338)).doesNotThrowAnyException();
    }

    // ==== 경계 =================================================================================

    @Test
    @DisplayName("빈 목록 · null 은 아무것도 판정하지 않는다(구 형식 저장본) · RAID 묶음 + 카드 null 은 @AssertTrue 몫이라 여기선 통과")
    void emptyAndNullCard() {
        assertThatCode(() -> DiskGroupRules.validate(List.of(), null)).doesNotThrowAnyException();
        assertThatCode(() -> DiskGroupRules.validate(null, CRA3338)).doesNotThrowAnyException();
        assertThatCode(() -> DiskGroupRules.validate(List.of(rule(RaidLevel.RAID5, 3)), null)).doesNotThrowAnyException();
    }

    // ==== 규칙 6 — 종류 ↔ 전송 정합 (CP7 검수) ================================================

    @Test
    @DisplayName("규칙 6 — HDD × NVMe 는 400, SSD × SAS(실재하는 사양) · AUTO × NVMe 는 통과")
    void incompatibleTransport() {
        DiskGroupRuleRequest hddNvme = rule(null, DiskTypeRequirement.HDD, DiskTransportRequirement.NVME,
                new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                new DiskCountRequirement(DiskCountMode.AT_LEAST, 1));
        assertThatThrownBy(() -> DiskGroupRules.validate(List.of(hddNvme), null))
                .isInstanceOf(InvalidDiskGroupException.class)
                .hasMessageContaining("HDD 에는 NVMe 전송 방식이 없습니다");

        DiskGroupRuleRequest ssdSas = rule(null, DiskTypeRequirement.SSD, DiskTransportRequirement.SAS,
                new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                new DiskCountRequirement(DiskCountMode.AT_LEAST, 1));
        DiskGroupRuleRequest autoNvme = rule(null, DiskTypeRequirement.AUTO, DiskTransportRequirement.NVME,
                new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                new DiskCountRequirement(DiskCountMode.AT_LEAST, 1));
        assertThatCode(() -> DiskGroupRules.validate(List.of(ssdSas, autoNvme), null)).doesNotThrowAnyException();
    }

    // ==== 규칙 7 — OS 영역 고정은 한 묶음만 (U4-1-2) =========================================

    private static DiskGroupRuleRequest withRole(DiskGroupRuleRequest base, DiskGroupRole role) {
        return new DiskGroupRuleRequest(base.raidLevel(), base.diskType(), base.transport(), base.capacity(), base.count(), role);
    }

    @Test
    @DisplayName("규칙 7 — OS 고정 묶음이 둘이면 두 번째 묶음 번호와 첫 번째 번호를 함께 말하는 400, 하나면 통과, 전부 Data/없음이어도 통과")
    void multipleOsRules() {
        DiskGroupRuleRequest osA = withRole(rule(RaidLevel.RAID1, 2), DiskGroupRole.OS);
        DiskGroupRuleRequest osB = withRole(rule(null, DiskTypeRequirement.SSD, DiskTransportRequirement.NVME,
                new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                new DiskCountRequirement(DiskCountMode.EXACT, 1)), DiskGroupRole.OS);
        assertThatThrownBy(() -> DiskGroupRules.validate(List.of(osA, osB), AVAGO_9361))
                .isInstanceOf(InvalidDiskGroupException.class)
                .hasMessageContaining("2번 묶음: 1번 묶음이 이미 OS 영역으로 고정되어 있습니다");

        DiskGroupRuleRequest data = withRole(rule(RaidLevel.RAID5, 3), DiskGroupRole.DATA);
        DiskGroupRuleRequest none = withRole(rule(null, DiskTypeRequirement.HDD, DiskTransportRequirement.SATA,
                new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                new DiskCountRequirement(DiskCountMode.AT_LEAST, 1)), DiskGroupRole.NONE);
        assertThatCode(() -> DiskGroupRules.validate(List.of(osA, data, none), AVAGO_9361)).doesNotThrowAnyException();
        assertThatCode(() -> DiskGroupRules.validate(List.of(data, none), AVAGO_9361)).doesNotThrowAnyException();
    }
}
