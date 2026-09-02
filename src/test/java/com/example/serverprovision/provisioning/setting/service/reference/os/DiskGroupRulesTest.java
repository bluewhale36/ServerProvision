package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.provisioning.setting.dto.request.VdParameters;
import com.example.serverprovision.provisioning.setting.enums.VdWritePolicy;
import com.example.serverprovision.management.raidcard.enums.RaidChipFamily;
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
        // 계열은 캐시 유무로 실측 정합(CRA3338=MPT_IR 캐시 0 · 9361-8i=MEGARAID 캐시 2GB) — 규칙 9 재료
        RaidCard card = RaidCard.builder()
                .id(1L).vendor(RaidCardVendor.GIGABYTE).modelName("card")
                .supportedRaidLevels(SupportedRaidLevels.of(levels))
                .cacheCapacity(CacheCapacity.ofGigabytes(cacheGb))
                .chipFamily(cacheGb > 0 ? RaidChipFamily.MEGARAID : RaidChipFamily.MPT_IR)
                .ownEnabled(true).ownDeprecated(false).isDeleted(false)
                .build();
        card.recomputeEffective();
        return card;
    }

    /** E3.5-6 규칙 9 — VD 파라미터 축을 실은 픽스처(Write Back 만 고르고 나머지는 HII 기본값으로 채워진다). */
    private static VdParameters vdSpecified() {
        return new VdParameters(VdWritePolicy.WRITE_BACK, null, null, null, null, null, null, null);
    }

    @org.junit.jupiter.api.Nested
    @org.junit.jupiter.api.DisplayName("규칙 9 — VD 파라미터는 지원 계열(MegaRAID) · RAID 구성 묶음에서만(E3.5-6)")
    class Rule9VdParameters {

        @org.junit.jupiter.api.Test
        @org.junit.jupiter.api.DisplayName("MegaRAID 카드 + RAID1 묶음의 지정값 → 통과")
        void megaraid_raidRule_passes() {
            DiskGroupRuleRequest r = new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD,
                    DiskTransportRequirement.SATA, new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                    new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.BY_PRIORITY, vdSpecified());
            org.assertj.core.api.Assertions.assertThatCode(() -> DiskGroupRules.validate(List.of(r), AVAGO_9361))
                    .doesNotThrowAnyException();
        }

        @org.junit.jupiter.api.Test
        @org.junit.jupiter.api.DisplayName("MPT_IR 카드 + 지정값 → 400 (계열 미지원 — UI 잠금과 같은 supportsVdParameters)")
        void mptIr_specified_rejected() {
            DiskGroupRuleRequest r = new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD,
                    DiskTransportRequirement.SATA, new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                    new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.BY_PRIORITY, vdSpecified());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> DiskGroupRules.validate(List.of(r), CRA3338))
                    .isInstanceOf(InvalidDiskGroupException.class)
                    .hasMessageContaining("MPT IR")
                    .hasMessageContaining("지원하지 않습니다");
        }

        @org.junit.jupiter.api.Test
        @org.junit.jupiter.api.DisplayName("SSD 묶음 + Drive Cache 를 Unchanged 밖(Disable)으로 → 400 (카드가 Unchanged 고정 — CP6 검수)")
        void ssd_driveCacheSpecified_rejected() {
            VdParameters dc = new VdParameters(null, null, null, null, null,
                    com.example.serverprovision.provisioning.setting.enums.VdDriveCache.OFF, null, null);
            DiskGroupRuleRequest r = new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD,
                    DiskTransportRequirement.SATA, new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                    new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.BY_PRIORITY, dc);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> DiskGroupRules.validate(List.of(r), AVAGO_9361))
                    .isInstanceOf(InvalidDiskGroupException.class)
                    .hasMessageContaining("Drive Cache");
        }

        @org.junit.jupiter.api.Test
        @org.junit.jupiter.api.DisplayName("SSD 묶음 + Drive Cache Unchanged(기본값) → 통과 (폼 잠금이 두는 값과 같다)")
        void ssd_driveCacheUnchanged_passes() {
            DiskGroupRuleRequest r = new DiskGroupRuleRequest(RaidLevel.RAID1, DiskTypeRequirement.SSD,
                    DiskTransportRequirement.SATA, new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                    new DiskCountRequirement(DiskCountMode.EXACT, 2), DiskGroupRole.BY_PRIORITY, VdParameters.DEFAULTS);
            org.assertj.core.api.Assertions.assertThatCode(() -> DiskGroupRules.validate(List.of(r), AVAGO_9361))
                    .doesNotThrowAnyException();
        }

        @org.junit.jupiter.api.Test
        @org.junit.jupiter.api.DisplayName("RAID 없음 묶음 + 지정값 → 400 (적용할 볼륨이 없다)")
        void nonRaid_specified_rejected() {
            DiskGroupRuleRequest r = new DiskGroupRuleRequest(null, DiskTypeRequirement.SSD,
                    DiskTransportRequirement.SATA, new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                    new DiskCountRequirement(DiskCountMode.AT_LEAST, 1), DiskGroupRole.DATA, vdSpecified());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> DiskGroupRules.validate(List.of(r), AVAGO_9361))
                    .isInstanceOf(InvalidDiskGroupException.class)
                    .hasMessageContaining("RAID 를 구성하지 않는 묶음");
        }
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

    // ==== 규칙 8(E3.5-4) — 사각 규칙: 선행에 완전 포섭된 후행은 도달 불가 =========================

    private static DiskGroupRuleRequest r8(DiskTypeRequirement type, DiskTransportRequirement transport,
                                           DiskCapacityRequirement capacity, DiskCountMode mode, int count) {
        return new DiskGroupRuleRequest(RaidLevel.RAID1, type, transport, capacity,
                new DiskCountRequirement(mode, count), DiskGroupRole.BY_PRIORITY);
    }

    private static final DiskCapacityRequirement CAP_AUTO =
            new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null);
    private static final DiskCapacityRequirement CAP_480 =
            new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 480L, DiskCapacityUnit.GB);
    private static final DiskCapacityRequirement CAP_960 =
            new DiskCapacityRequirement(CapacityRequirementMode.SPECIFIED, 960L, DiskCapacityUnit.GB);

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("W4 — 개수 수용집합 포섭 3형(엄격 일치 기준): AT_LEAST⊇AT_LEAST · AT_LEAST⊇EXACT · EXACT=EXACT")
    void covers_countAcceptanceForms() {
        var atLeast2 = r8(DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO, CAP_AUTO, DiskCountMode.AT_LEAST, 2);
        org.assertj.core.api.Assertions.assertThat(DiskGroupRules.covers(atLeast2,
                r8(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, CAP_AUTO, DiskCountMode.AT_LEAST, 3))).isTrue();
        org.assertj.core.api.Assertions.assertThat(DiskGroupRules.covers(atLeast2,
                r8(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, CAP_AUTO, DiskCountMode.EXACT, 3))).isTrue();
        var exact2 = r8(DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO, CAP_AUTO, DiskCountMode.EXACT, 2);
        org.assertj.core.api.Assertions.assertThat(DiskGroupRules.covers(exact2,
                r8(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, CAP_AUTO, DiskCountMode.EXACT, 2))).isTrue();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("W4 — 축 포섭: 선행 AUTO 는 덮고, 구체값은 동일해야만 덮는다(용량은 동일 지정만)")
    void covers_axisContainment() {
        var specific = r8(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, CAP_480, DiskCountMode.AT_LEAST, 2);
        // 종류가 다르면 비포섭
        org.assertj.core.api.Assertions.assertThat(DiskGroupRules.covers(specific,
                r8(DiskTypeRequirement.HDD, DiskTransportRequirement.SATA, CAP_480, DiskCountMode.AT_LEAST, 3))).isFalse();
        // 선행이 용량 지정이면 후행 AUTO 용량은 비포섭(후행이 더 넓다)
        org.assertj.core.api.Assertions.assertThat(DiskGroupRules.covers(specific,
                r8(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, CAP_AUTO, DiskCountMode.AT_LEAST, 3))).isFalse();
        // 지정 용량이 다르면 비포섭
        org.assertj.core.api.Assertions.assertThat(DiskGroupRules.covers(specific,
                r8(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, CAP_960, DiskCountMode.AT_LEAST, 3))).isFalse();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("W5 — 후행 흘림의 근거는 막지 않는다: EXACT 2 뒤 AT_LEAST 3 · 넓은 규칙이 뒤에 오는 역순")
    void covers_doesNotBlockFallThrough() {
        var exact2 = r8(DiskTypeRequirement.SSD, DiskTransportRequirement.AUTO, CAP_AUTO, DiskCountMode.EXACT, 2);
        var atLeast3 = r8(DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO, CAP_AUTO, DiskCountMode.AT_LEAST, 3);
        org.assertj.core.api.Assertions.assertThat(DiskGroupRules.covers(exact2, atLeast3)).isFalse();   // T17 구성
        // 넓은 규칙이 뒤: 선행(좁음)이 후행(넓음)을 못 덮는다 — 순서를 바꾸면 도달 가능하므로 정당
        org.assertj.core.api.Assertions.assertThat(DiskGroupRules.covers(
                r8(DiskTypeRequirement.SSD, DiskTransportRequirement.SATA, CAP_AUTO, DiskCountMode.AT_LEAST, 3),
                r8(DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO, CAP_AUTO, DiskCountMode.AT_LEAST, 2))).isFalse();
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("W4 통합 — validate 가 포섭된 후행을 unreachableRule 로 거절한다(레벨이 달라도)")
    void validate_rejectsUnreachableRule() {
        var covering = new DiskGroupRuleRequest(RaidLevel.RAID5, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                CAP_AUTO, new DiskCountRequirement(DiskCountMode.AT_LEAST, 3), DiskGroupRole.BY_PRIORITY);
        var covered = new DiskGroupRuleRequest(RaidLevel.RAID6, DiskTypeRequirement.AUTO, DiskTransportRequirement.AUTO,
                CAP_AUTO, new DiskCountRequirement(DiskCountMode.AT_LEAST, 4), DiskGroupRole.BY_PRIORITY);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        DiskGroupRules.validate(List.of(covering, covered), AVAGO_9361))
                .isInstanceOf(com.example.serverprovision.provisioning.setting.exception.InvalidDiskGroupException.class)
                .hasMessageContaining("2번 묶음은 1번 묶음에 가려 도달할 수 없습니다");
    }
}
