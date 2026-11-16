package com.example.serverprovision.provisioning.assignment;

import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.engine.phase.PhaseSequence;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.mapper.SettingProcessPhaseMapper;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhases;
import com.example.serverprovision.provisioning.assignment.vo.OwnedPhasesConverter;
import com.example.serverprovision.provisioning.setting.enums.SettingProcessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * seam(DB 불요) — 저장 · 복원된 {@link OwnedPhases} 를 {@link PhaseSequence#nextAfter} 에 주입해 엔진의
 * 다음 phase 순회를 재현한다(소비 재현). ES-1 이 실배선을 완료해, 엔진의 {@link PhaseCursorAdvancer} 가
 * 공급된 {@code ownedPhases} 를 실제로 소비해 커서를 전진 · 종단시키는 것까지 여기서 못박는다
 * (nextAfter 직접 호출 검증 + advancer 실소비 검증 두 층).
 */
class OwnedPhasesSeamTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 2, 21, 0);

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

    // ==== ES-1 승격 — 엔진(PhaseCursorAdvancer)이 공급된 ownedPhases 를 실제 소비 ====

    /** 진단 리눅스 커서(수집 step)에서 시작한, 개시된 진행 상태(전진 가드 통과 전제). */
    private ProvisioningProgress diagnoseProgress() {
        return ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(com.example.serverprovision.execution.enums.ProvisioningPhaseStep.INFORMATION_COLLECTING)
                .startedAt(T).lastTransitionAt(T)
                .build();
    }

    @Test
    @DisplayName("실소비 — 복원된 ownedPhases(펌웨어 업데이트 + OS 설치)를 advancer 가 소비 → 커서 FIRMWARE_UPDATING 전진, 종단 아님")
    void advancer_consumesRestoredOwnedPhases() {
        Set<ProvisioningPhase> owned = persistedThenRestored(
                SettingProcessType.BASIC_UPDATE, SettingProcessType.OS_INSTALLATION);
        // OwnedPhasesProvider 는 단일 메서드 SPI — 복원 집합을 그대로 공급하는 람다로 엔진 소비를 재현한다.
        PhaseCursorAdvancer advancer = new PhaseCursorAdvancer(guestId -> owned);
        ProvisioningProgress progress = diagnoseProgress();

        advancer.advanceOrComplete(progress, UUID.randomUUID(), T.plusMinutes(1));

        assertThat(progress.currentPhase()).isEqualTo(ProvisioningPhase.FIRMWARE_UPDATING);   // 진입 step 으로 pre-position(ES-2)
        assertThat(progress.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("실소비 — 빈 정의서면 advancer 가 종단(markCompleted), 커서 불변 (무할당 = 진단 완주 종단)")
    void advancer_emptyOwned_completes() {
        Set<ProvisioningPhase> empty = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(OwnedPhases.empty())).asSet();
        PhaseCursorAdvancer advancer = new PhaseCursorAdvancer(guestId -> empty);
        ProvisioningProgress progress = diagnoseProgress();

        advancer.advanceOrComplete(progress, UUID.randomUUID(), T.plusMinutes(1));

        assertThat(progress.isCompleted()).isTrue();
        assertThat(progress.currentPhase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);
    }
}
