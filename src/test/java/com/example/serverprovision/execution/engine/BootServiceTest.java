package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.dto.BootIPXEInfoRequest;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.event.GuestServerChangedEvent;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.service.GuestServerRegistrationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * S7 CP4 — {@link BootService} 단위 테스트. /boot 폴링의 등록·접촉 변화가 실시간 스트림 신호
 * ({@link GuestServerChangedEvent})로 발행되는지 검증한다 — 발행 누락은 "그 화면만 안 갱신되는"
 * 회귀(plan §6)라 커밋 경로마다 못 박는다.
 */
@ExtendWith(MockitoExtension.class)
class BootServiceTest {

    @Mock GuestServerRegistrationService registrationService;
    @Mock ProvisioningProgressRepository provisioningProgressRepository;
    @Mock BootScriptDispatcher bootScriptDispatcher;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks BootService service;

    private static final BootIPXEInfoRequest REQUEST = new BootIPXEInfoRequest(
            "52:54:00:aa:bb:cc", "10.0.2.15", UUID.randomUUID().toString(), "QEMU", "Standard PC");

    @Test
    @DisplayName("boot — dispatch 스크립트 반환 + 변화 신호(GuestServerChangedEvent) 발행")
    void boot_dispatches_andPublishesChangedSignal() {
        UUID id = UUID.randomUUID();
        GuestServer server = GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).build();
        ProvisioningProgress progress = ProvisioningProgress.builder()
                .currentPhase(ProvisioningPhase.BOOTSTRAPPING).lastTransitionAt(LocalDateTime.now()).build();
        given(registrationService.initialRegistry(REQUEST)).willReturn(server);
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.of(progress));
        given(bootScriptDispatcher.dispatch(server, progress, "")).willReturn("#!ipxe\nsleep 30");

        String script = service.boot(REQUEST, null);

        assertThat(script).isEqualTo("#!ipxe\nsleep 30");
        assertThat(server.getLastSeenAt()).isNotNull();   // 접촉 관찰 갱신(DEC-32)
        verify(eventPublisher).publishEvent(new GuestServerChangedEvent(id));
    }

    @Test
    @DisplayName("boot — progress seed 누락(1:1 불변 위반) → IllegalStateException, 신호 미발행")
    void boot_missingProgress_failsWithoutSignal() {
        UUID id = UUID.randomUUID();
        GuestServer server = GuestServer.builder().id(id).systemUUID(UUID.randomUUID()).build();
        given(registrationService.initialRegistry(REQUEST)).willReturn(server);
        given(provisioningProgressRepository.findByGuestServer_Id(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.boot(REQUEST, null))
                .isInstanceOf(IllegalStateException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }
}
