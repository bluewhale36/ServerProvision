package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * ES-1 CP4 — 커서 전진 · 종단 규칙 SSOT(DES-1). {@link OwnedPhasesProvider} 만 mock 하고
 * {@link PhaseSequence}(순수 함수) · {@link ProvisioningProgress}(실 도메인) 는 실물로 두어,
 * "소유 phase 있으면 전진 · 없으면 종단 · 여러 개면 소유 첫 phase" 판정을 격리 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class PhaseCursorAdvancerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 2, 21, 0);

    @Mock OwnedPhasesProvider ownedPhasesProvider;

    private PhaseCursorAdvancer advancer() {
        return new PhaseCursorAdvancer(ownedPhasesProvider);
    }

    /** 진단 리눅스 커서(수집 step)에서 시작한, 개시된 진행 상태(전진 가드 통과 전제). */
    private ProvisioningProgress diagnoseProgress() {
        return ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.INFORMATION_COLLECTING)
                .startedAt(T).lastTransitionAt(T)
                .build();
    }

    @Test
    @DisplayName("소유 phase 있음 → 소유 첫 phase 로 전진(advanceTo), 종단 아님")
    void present_advancesToFirstOwned() {
        UUID guestId = UUID.randomUUID();
        given(ownedPhasesProvider.ownedPhasesOf(guestId)).willReturn(Set.of(ProvisioningPhase.FIRMWARE_UPDATING));
        ProvisioningProgress progress = diagnoseProgress();

        advancer().advanceOrComplete(progress, guestId, T.plusMinutes(1));

        assertThat(progress.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.BIOS_UPDATING);   // 진입 step pre-position(ES-2)
        assertThat(progress.currentPhase()).isEqualTo(ProvisioningPhase.FIRMWARE_UPDATING);
        assertThat(progress.isCompleted()).isFalse();
        assertThat(progress.getLastTransitionAt()).isEqualTo(T.plusMinutes(1));
    }

    @Test
    @DisplayName("무할당(빈 집합) → 종단(markCompleted), 커서 불변 (현 동작 보존)")
    void empty_completes() {
        UUID guestId = UUID.randomUUID();
        given(ownedPhasesProvider.ownedPhasesOf(guestId)).willReturn(Set.of());
        ProvisioningProgress progress = diagnoseProgress();

        advancer().advanceOrComplete(progress, guestId, T.plusMinutes(1));

        assertThat(progress.isCompleted()).isTrue();
        assertThat(progress.currentPhase()).isEqualTo(ProvisioningPhase.DIAGNOSE_LINUX);   // markCompleted 는 커서 불변
    }

    @Test
    @DisplayName("소유가 진단 바로 다음이 아님(OS 설치만) → 미소유(펌웨어)는 건너뛰고 소유 첫 phase(OS_INSTALLING)로")
    void owned_notAdjacent_advancesToFirstOwned() {
        UUID guestId = UUID.randomUUID();
        given(ownedPhasesProvider.ownedPhasesOf(guestId)).willReturn(Set.of(ProvisioningPhase.OS_INSTALLING));
        ProvisioningProgress progress = diagnoseProgress();

        advancer().advanceOrComplete(progress, guestId, T);

        assertThat(progress.currentPhase()).isEqualTo(ProvisioningPhase.OS_INSTALLING);
        assertThat(progress.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("소유 여럿 → 소유 첫 phase 에서 멈춘다(DES-4 — 뒤 소유 phase 로 건너뛰지 않음)")
    void multiOwned_stopsAtFirst() {
        UUID guestId = UUID.randomUUID();
        given(ownedPhasesProvider.ownedPhasesOf(guestId)).willReturn(
                Set.of(ProvisioningPhase.FIRMWARE_UPDATING, ProvisioningPhase.OS_INSTALLING,
                        ProvisioningPhase.OS_SETTING));
        ProvisioningProgress progress = diagnoseProgress();

        advancer().advanceOrComplete(progress, guestId, T);

        assertThat(progress.currentPhase()).isEqualTo(ProvisioningPhase.FIRMWARE_UPDATING);
    }

    @Test
    @DisplayName("안전망 — 이미 종단된 진행에 소유 phase 전진 시도(stale · 동시성)는 advanceToEntry 가드로 IllegalState(500)")
    void advanceOnCompleted_throwsGuard() {
        UUID guestId = UUID.randomUUID();
        given(ownedPhasesProvider.ownedPhasesOf(guestId)).willReturn(Set.of(ProvisioningPhase.FIRMWARE_UPDATING));
        ProvisioningProgress completed = diagnoseProgress();
        completed.markCompleted(T);   // 이미 종단

        assertThatThrownBy(() -> advancer().advanceOrComplete(completed, guestId, T.plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("전이할 수 없습니다");
    }
}
