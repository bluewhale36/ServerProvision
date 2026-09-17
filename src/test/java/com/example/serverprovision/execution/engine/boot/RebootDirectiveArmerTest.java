package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.redfish.NextBoot;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishPowerState;
import com.example.serverprovision.global.redfish.RedfishTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * HF17 — REBOOT 지시 직전 Once · Pxe 무장의 적격 판정과 커밋 뒤 실행. 진행 중 + BMC 있음 + REBOOT 일 때만 세우고,
 * 종단 · 실패 · 회수 · 미개시 · BMC 미검출 · 다른 지시는 BMC 를 두드리지 않는다. 무장 실패는 예외가 아니다(best effort).
 */
@ExtendWith(MockitoExtension.class)
class RebootDirectiveArmerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 16, 12, 0);

    @Mock GuestServerDetailRepository detailRepository;
    @Mock RedfishPowerService powerService;
    @InjectMocks RebootDirectiveArmer armer;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("REBOOT · 진행 중 · BMC 검출 → Once · Pxe 를 세운다(트랜잭션이 없으면 즉시)")
    void armsOnRebootWhileProvisioning() {
        GuestServer server = server();
        given(detailRepository.findByGuestServer_Id(server.getId())).willReturn(Optional.of(detail()));
        given(powerService.armBootOverride(any(), any()))
                .willReturn(PowerControlResult.sent(RedfishPowerState.UNKNOWN, "다음 부팅 PXE 강제 : 반영 확인 · "));

        armer.armIfReboot(AgentDirective.REBOOT, server, provisioning());

        verify(powerService).armBootOverride(eq(new RedfishTarget("10.10.0.51", "QG260700082")), eq(NextBoot.PXE_ONCE));
    }

    @Test
    @DisplayName("트랜잭션 안에서는 커밋 뒤에 세운다 — 등록 시점에는 BMC 를 부르지 않고 afterCommit 에서 부른다")
    void armsAfterCommitWhenTransactionActive() {
        GuestServer server = server();
        given(detailRepository.findByGuestServer_Id(server.getId())).willReturn(Optional.of(detail()));
        given(powerService.armBootOverride(any(), any()))
                .willReturn(PowerControlResult.sent(RedfishPowerState.UNKNOWN, "반영"));
        TransactionSynchronizationManager.initSynchronization();

        armer.armIfReboot(AgentDirective.REBOOT, server, provisioning());
        verify(powerService, never()).armBootOverride(any(), any());

        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(powerService).armBootOverride(any(), eq(NextBoot.PXE_ONCE));
    }

    @Test
    @DisplayName("REBOOT 가 아닌 지시(COLLECT · WAIT · RAID_APPLY)는 무장하지 않는다 — 저장소도 읽지 않는다")
    void ignoresNonRebootDirectives() {
        GuestServer server = server();

        armer.armIfReboot(AgentDirective.COLLECT, server, provisioning());
        armer.armIfReboot(AgentDirective.WAIT, server, provisioning());
        armer.armIfReboot(AgentDirective.RAID_APPLY, server, provisioning());

        verifyNoInteractions(detailRepository, powerService);
    }

    @Test
    @DisplayName("종단 · 실패 · 미개시 · 회수의 REBOOT 은 로컬 부팅이 맞다 — 무장 없음")
    void ignoresRebootOutsideProvisioning() {
        GuestServer server = server();
        ProvisioningProgress completed = provisioning();
        completed.markCompleted(T.plusMinutes(1));
        ProvisioningProgress failed = provisioning();
        failed.markFailed(T.plusMinutes(1));
        ProvisioningProgress unstarted = ProvisioningProgress.builder()
                .id(UUID.randomUUID()).guestServer(server)
                .currentStep(ProvisioningPhaseStep.DIAGNOSTIC_BOOTING).lastTransitionAt(T).build();

        armer.armIfReboot(AgentDirective.REBOOT, server, completed);
        armer.armIfReboot(AgentDirective.REBOOT, server, failed);
        armer.armIfReboot(AgentDirective.REBOOT, server, unstarted);
        GuestServer decommissioned = server();
        decommissioned.decommission(T.plusMinutes(2));
        armer.armIfReboot(AgentDirective.REBOOT, decommissioned, provisioning());

        verifyNoInteractions(detailRepository, powerService);
    }

    @Test
    @DisplayName("BMC 미검출(QEMU · 진단 전)은 세울 수단이 없다 — 무장 없음")
    void ignoresWhenBmcUnknown() {
        GuestServer server = server();
        given(detailRepository.findByGuestServer_Id(server.getId()))
                .willReturn(Optional.of(GuestServerDetail.builder().boardSerial("QG260700082").build()));

        armer.armIfReboot(AgentDirective.REBOOT, server, provisioning());

        verifyNoInteractions(powerService);
    }

    @Test
    @DisplayName("무장 실패(BMC 거절 · 미응답)는 예외가 아니다 — 응답은 REBOOT 그대로(best effort)")
    void failureIsSwallowed() {
        GuestServer server = server();
        given(detailRepository.findByGuestServer_Id(server.getId())).willReturn(Optional.of(detail()));
        given(powerService.armBootOverride(any(), any()))
                .willReturn(PowerControlResult.failed(null, "BootSourceOverride 조정을 BMC 가 거절했습니다."));

        armer.armIfReboot(AgentDirective.REBOOT, server, provisioning());   // 예외 없음

        verify(powerService).armBootOverride(any(), eq(NextBoot.PXE_ONCE));
    }

    private static GuestServer server() {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    }

    private static GuestServerDetail detail() {
        return GuestServerDetail.builder()
                .bmcIp(IpAddressVO.of("10.10.0.51"))
                .boardSerial("QG260700082")
                .build();
    }

    /** 개시된 RAID 검증 커서 — 실기 3호 F-2b 의 자리(전이 뒤 에이전트가 REBOOT 를 받는다). */
    private static ProvisioningProgress provisioning() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.RAID_VERIFYING)
                .lastTransitionAt(T)
                .build();
        p.start(T);
        return p;
    }
}
