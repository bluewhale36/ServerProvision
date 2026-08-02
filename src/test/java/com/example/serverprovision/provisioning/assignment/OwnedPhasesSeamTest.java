package com.example.serverprovision.provisioning.assignment;

import com.example.serverprovision.execution.engine.PhaseSequence;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.mapper.SettingProcessPhaseMapper;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhasesConverter;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * seam(DB 불요) — 저장 · 복원된 {@link OwnedPhases} 를 {@link PhaseSequence#nextAfter} 에 주입해 엔진의
 * 다음 phase 순회를 재현한다(소비 재현). U3-1 은 공급까지이고 실제 {@code Set.of()} 배선은 ES-1 이월이라,
 * 여기서 "공급된 ownedPhases 로 순회가 어떻게 흐르는지" 를 못박는다.
 */
class OwnedPhasesSeamTest {

    private final OwnedPhasesConverter converter = new OwnedPhasesConverter();

    /** 저장 → 복원 왕복으로 영속을 시뮬레이션한 소유 집합. */
    private Set<ProvisioningPhase> persistedThenRestored(SettingProcessType... types) {
        OwnedPhases owned = SettingProcessPhaseMapper.toOwnedPhases(EnumSet.copyOf(Set.of(types)));
        return converter.convertToEntityAttribute(converter.convertToDatabaseColumn(owned)).asSet();
    }

    @Test
    @DisplayName("펌웨어 업데이트 + OS 설치 소유 → BOOTSTRAPPING→DIAGNOSE→FIRMWARE_UPDATING→OS_INSTALLING→종단")
    void traversal_reproducesSequence() {
        Set<ProvisioningPhase> owned = persistedThenRestored(
                SettingProcessType.BASIC_UPDATE, SettingProcessType.OS_INSTALLATION);

        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.BOOTSTRAPPING, owned))
                .contains(ProvisioningPhase.DIAGNOSE_LINUX);
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.DIAGNOSE_LINUX, owned))
                .contains(ProvisioningPhase.FIRMWARE_UPDATING);
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.FIRMWARE_UPDATING, owned))
                .contains(ProvisioningPhase.OS_INSTALLING);
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.OS_INSTALLING, owned))
                .isEmpty();   // 보유 마지막 phase 완주 = 종단
    }

    @Test
    @DisplayName("빈 정의서 할당(ownedPhases ∅) → 진단 후 종단이 정상(고장 아님)")
    void emptyOwned_diagnoseThenTerminal() {
        Set<ProvisioningPhase> empty = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(OwnedPhases.empty())).asSet();

        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.BOOTSTRAPPING, empty))
                .contains(ProvisioningPhase.DIAGNOSE_LINUX);
        assertThat(PhaseSequence.nextAfter(ProvisioningPhase.DIAGNOSE_LINUX, empty))
                .isEmpty();
    }
}
