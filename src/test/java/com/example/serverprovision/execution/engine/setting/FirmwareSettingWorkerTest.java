package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/** E3-2 D-2 — 워커는 두 축의 step 을 함께 훑고, 한 대의 실패가 다음 대를 막지 않는다. */
@ExtendWith(MockitoExtension.class)
class FirmwareSettingWorkerTest {

    @Mock ProvisioningProgressRepository repository;
    @Mock FirmwareSettingCycle cycle;

    @Test
    @DisplayName("sweep — BIOS_SETTING · BMC_SETTING 커서를 한 번에 집고 게스트마다 advance 를 부른다")
    void sweepsBothAxes() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        given(repository.findAllByCurrentStepInAndFailedAtIsNullAndCompletedAtIsNull(
                eq(List.of(ProvisioningPhaseStep.BIOS_SETTING, ProvisioningPhaseStep.BMC_SETTING))))
                .willReturn(List.of(progressOf(a), progressOf(b)));
        willThrow(new IllegalStateException("boom")).given(cycle).advance(eq(a), any());

        new FirmwareSettingWorker(repository, cycle).sweep();

        verify(cycle).advance(eq(a), any());
        verify(cycle).advance(eq(b), any());   // a 의 예외가 b 를 막지 않는다
        assertThat(true).isTrue();
    }

    private static ProvisioningProgress progressOf(UUID guestId) {
        GuestServer server = GuestServer.builder().id(guestId).systemUUID(UUID.randomUUID()).build();
        return ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(server)
                .currentStep(ProvisioningPhaseStep.BIOS_SETTING).build();
    }
}
