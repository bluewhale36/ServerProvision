package com.example.serverprovision.provisioning.setting.service.reference;

import com.example.serverprovision.management.raidcard.entity.RaidCard;
import com.example.serverprovision.management.raidcard.enums.RaidCardVendor;
import com.example.serverprovision.management.raidcard.enums.RaidLevel;
import com.example.serverprovision.management.raidcard.exception.RaidCardNotFoundException;
import com.example.serverprovision.management.raidcard.repository.RaidCardRepository;
import com.example.serverprovision.management.raidcard.vo.CacheCapacity;
import com.example.serverprovision.management.raidcard.vo.SupportedRaidLevels;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCapacityRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskCountRequirement;
import com.example.serverprovision.provisioning.setting.dto.request.DiskGroupRuleRequest;
import com.example.serverprovision.provisioning.setting.dto.request.RaidConfigurationRequest;
import com.example.serverprovision.provisioning.setting.enums.CapacityRequirementMode;
import com.example.serverprovision.provisioning.setting.enums.DiskCountMode;
import com.example.serverprovision.provisioning.setting.enums.DiskTransportRequirement;
import com.example.serverprovision.provisioning.setting.enums.DiskTypeRequirement;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import com.example.serverprovision.provisioning.setting.exception.DisabledResourceReferenceException;
import com.example.serverprovision.provisioning.setting.exception.InvalidDiskGroupException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * U4-1-1 v2 CP4 — RAID 구성 단계 검사기: 카드 소프트참조(404 · 409 · deprecated 서술) + 실 카드로 {@code DiskGroupRules} 판정.
 */
@ExtendWith(MockitoExtension.class)
class RaidConfigurationReferenceInspectorTest {

    private static final ProcessValidationContext CTX = new ProcessValidationContext(List.of());

    @Mock RaidCardRepository raidCardRepository;

    private RaidConfigurationReferenceInspector build() {
        return new RaidConfigurationReferenceInspector(raidCardRepository);
    }

    private static RaidConfigurationRequest request(Long raidCardId, RaidLevel level, int count) {
        var rule = new DiskGroupRuleRequest(level, DiskTypeRequirement.SSD, DiskTransportRequirement.SATA,
                new DiskCapacityRequirement(CapacityRequirementMode.AUTO, null, null),
                new DiskCountRequirement(DiskCountMode.EXACT, count));
        return new RaidConfigurationRequest(raidCardId, List.of(rule));
    }

    /** CRA3338 사양(캐시 없음 · RAID0/1) — 지원 레벨 · 캐시 판정이 실제 VO 로 돌도록 mock 대신 실 엔티티. */
    private static RaidCard card(boolean enabled, boolean deprecated) {
        RaidCard card = RaidCard.builder()
                .id(5L).vendor(RaidCardVendor.GIGABYTE).modelName("CRA3338")
                .supportedRaidLevels(SupportedRaidLevels.of(List.of(RaidLevel.RAID0, RaidLevel.RAID1)))
                .cacheCapacity(CacheCapacity.NONE)
                .ownEnabled(enabled).ownDeprecated(deprecated).isDeleted(false)
                .build();
        card.recomputeEffective();
        return card;
    }

    @Test
    @DisplayName("target 은 RAID_CONFIGURATION")
    void target() {
        assertThat(build().target()).isEqualTo(SettingProcessType.RAID_CONFIGURATION);
    }

    @Test
    @DisplayName("카드 부존재/삭제 → 404 · disabled → 409(field=raidCardId) · 카드 null 이면 저장소 미호출")
    void cardGuards() {
        var inspector = build();
        given(raidCardRepository.findByIdAndIsDeletedFalse(99L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> inspector.validateReferences(request(99L, RaidLevel.RAID1, 2), CTX))
                .isInstanceOf(RaidCardNotFoundException.class);

        var disabledCard = card(false, false);
        given(raidCardRepository.findByIdAndIsDeletedFalse(5L)).willReturn(Optional.of(disabledCard));
        assertThatThrownBy(() -> inspector.validateReferences(request(5L, RaidLevel.RAID1, 2), CTX))
                .isInstanceOf(DisabledResourceReferenceException.class)
                .satisfies(e -> assertThat(((DisabledResourceReferenceException) e).fieldName()).isEqualTo("raidCardId"));

        // 카드를 전제하지 않는 요청 — 앞의 두 호출(99L · 5L) 외에 저장소를 더 부르지 않는다(null id 조회 없음).
        assertThatCode(() -> inspector.validateReferences(request(null, null, 1), CTX)).doesNotThrowAnyException();
        verify(raidCardRepository, never()).findByIdAndIsDeletedFalse(org.mockito.ArgumentMatchers.isNull());
        verify(raidCardRepository, org.mockito.Mockito.times(2)).findByIdAndIsDeletedFalse(any());
    }

    @Test
    @DisplayName("실 카드로 DiskGroupRules 판정 — RAID1 2개 통과 · RAID5 는 InvalidDiskGroupException(field=diskGroups)")
    void cardDrivesRules() {
        var inspector = build();
        var enabledCard = card(true, false);
        given(raidCardRepository.findByIdAndIsDeletedFalse(5L)).willReturn(Optional.of(enabledCard));

        assertThatCode(() -> inspector.validateReferences(request(5L, RaidLevel.RAID1, 2), CTX)).doesNotThrowAnyException();
        assertThatThrownBy(() -> inspector.validateReferences(request(5L, RaidLevel.RAID5, 3), CTX))
                .isInstanceOf(InvalidDiskGroupException.class)
                .hasMessageContaining("RAID5 를 만들 수 없는 카드");
    }

    @Test
    @DisplayName("deprecated 카드는 저장을 막지 않고 describeDeprecatedReferences 에 이름을 싣는다 · referencedResources 는 빈 목록(메타 자원)")
    void deprecatedDescribedNotBlocked() {
        var inspector = build();
        var deprecatedCard = card(true, true);
        given(raidCardRepository.findByIdAndIsDeletedFalse(5L)).willReturn(Optional.of(deprecatedCard));

        var req = request(5L, RaidLevel.RAID1, 2);
        assertThatCode(() -> inspector.validateReferences(req, CTX)).doesNotThrowAnyException();
        assertThat(inspector.describeDeprecatedReferences(req)).contains("RAID 카드 GIGABYTE CRA3338");
        assertThat(inspector.referencedResources(req)).isEmpty();
        assertThat(inspector.describeDeprecatedReferences(request(null, null, 1))).isEmpty();
    }
}
