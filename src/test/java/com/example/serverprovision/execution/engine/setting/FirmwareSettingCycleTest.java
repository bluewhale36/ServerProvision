package com.example.serverprovision.execution.engine.setting;

import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.setting.step.SettingContext;
import com.example.serverprovision.execution.engine.setting.step.SettingStep;
import com.example.serverprovision.execution.engine.setting.step.SettingStepRegistry;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * E3-1 D-2 · E3-2 D-2 — 한 주기의 재료 조립(두 축의 목표). 판정 로직은 행이 갖고, 여기는 저장소에서 읽은 것을 컨텍스트에 담아
 * registry 의 첫 행에 넘기는 일만 한다. provider 는 게스트를 지원하는 첫 흐름이다.
 */
@ExtendWith(MockitoExtension.class)
class FirmwareSettingCycleTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 25, 12, 0);

    @Mock ProvisioningProgressRepository progressRepository;
    @Mock ProvisioningHistoryRepository historyRepository;
    @Mock GuestServerDetailRepository detailRepository;
    @Mock BiosSettingResolutionProvider resolutionProvider;
    @Mock BmcSettingTargetResolver bmcTargetResolver;
    @Mock FirmwareUpdateProvider unsupported;
    @Mock FirmwareUpdateProvider supported;
    @Mock SettingStepRegistry stepRegistry;
    @Mock SettingStep step;

    @Test
    @DisplayName("진행 행이 없으면 아무 행도 묻지 않는다")
    void noProgress_asksNothing() {
        given(progressRepository.findByGuestServer_Id(any())).willReturn(Optional.empty());

        cycle().advance(UUID.randomUUID(), T);

        then(stepRegistry).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("재료를 모아 처음 맞는 행을 실행한다 — provider 는 지원하는 첫 흐름")
    void assemblesContextAndRunsFirstMatching() {
        GuestServer server = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
        ProvisioningProgress progress = progress(server);
        GuestServerDetail detail = GuestServerDetail.builder().boardSerial("QG260700082").build();
        ProvisioningHistory row = ProvisioningHistory.openRunning(server, ProvisioningPhaseStep.BIOS_SETTING, T);
        BiosSettingTarget target = new BiosSettingTarget(Map.of("BootMode", "UEFI"));
        given(progressRepository.findByGuestServer_Id(server.getId())).willReturn(Optional.of(progress));
        given(detailRepository.findByServerIdWithBoardModel(server.getId())).willReturn(Optional.of(detail));
        given(historyRepository.findAllByServerIdOrderByStartedAt(server.getId())).willReturn(List.of(row));
        given(resolutionProvider.resolveFor(server.getId())).willReturn(Optional.of(target));
        BmcSettingTarget bmcTarget = new BmcSettingTarget(null, "MS03-CE0", null);
        given(bmcTargetResolver.resolve(detail)).willReturn(bmcTarget);
        given(unsupported.supports(server, detail)).willReturn(false);
        given(supported.supports(server, detail)).willReturn(true);
        given(stepRegistry.firstMatching(any())).willReturn(Optional.of(step));

        cycle().advance(server.getId(), T);

        ArgumentCaptor<SettingContext> captor = ArgumentCaptor.forClass(SettingContext.class);
        verify(step).execute(captor.capture());
        SettingContext context = captor.getValue();
        assertThat(context.server()).isSameAs(server);
        assertThat(context.progress()).isSameAs(progress);
        assertThat(context.detail()).isSameAs(detail);
        assertThat(context.history()).containsExactly(row);
        assertThat(context.target()).isEqualTo(target);
        assertThat(context.bmcTarget()).isSameAs(bmcTarget);
        assertThat(context.provider()).isSameAs(supported);
        assertThat(context.now()).isEqualTo(T);
    }

    @Test
    @DisplayName("창 밖(목표 empty) · 지원 흐름 없음은 null 로 실린다 — 판정은 행이 한다")
    void outOfWindowAndNoProviderAreCarriedAsNull() {
        GuestServer server = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
        given(progressRepository.findByGuestServer_Id(server.getId())).willReturn(Optional.of(progress(server)));
        given(detailRepository.findByServerIdWithBoardModel(any())).willReturn(Optional.empty());
        given(historyRepository.findAllByServerIdOrderByStartedAt(any())).willReturn(List.of());
        given(resolutionProvider.resolveFor(any())).willReturn(Optional.empty());
        given(unsupported.supports(any(), any())).willReturn(false);
        given(supported.supports(any(), any())).willReturn(false);
        SettingStep noop = mock(SettingStep.class);
        given(stepRegistry.firstMatching(any())).willReturn(Optional.of(noop));

        cycle().advance(server.getId(), T);

        ArgumentCaptor<SettingContext> captor = ArgumentCaptor.forClass(SettingContext.class);
        verify(noop).execute(captor.capture());
        assertThat(captor.getValue().target()).isNull();
        assertThat(captor.getValue().provider()).isNull();
        assertThat(captor.getValue().bmcDetected()).isFalse();
    }

    private FirmwareSettingCycle cycle() {
        return new FirmwareSettingCycle(progressRepository, historyRepository, detailRepository, resolutionProvider,
                bmcTargetResolver, List.of(unsupported, supported), stepRegistry,
                mock(org.springframework.context.ApplicationEventPublisher.class),
                new com.example.serverprovision.execution.engine.WorkerObservations());
    }

    private static ProvisioningProgress progress(GuestServer server) {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .guestServer(server)
                .currentStep(ProvisioningPhaseStep.BIOS_SETTING)
                .lastTransitionAt(T)
                .build();
        p.start(T);
        return p;
    }
}
