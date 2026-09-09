package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.repository.GuestServerRepository;
import com.example.serverprovision.execution.repository.ProvisioningProgressRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.redfish.NextBoot;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishPowerState;
import com.example.serverprovision.global.redfish.RedfishTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * HF15-1 — PXE 보장 조정자: 원하는 상태(창 안 = Continuous · 밖 = 해제)를 진행 상태에서 도출하고, 뒤집힐 때만 BMC 를 부르며,
 * 세우기 실패는 재시도 · 미상 상태의 해제 실패는 한 번으로 접는다.
 */
@ExtendWith(MockitoExtension.class)
class BootOverrideReconcilerTest {

    private static final UUID ID = UUID.randomUUID();
    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 8, 18, 0);

    @Mock GuestServerRepository guestServerRepository;
    @Mock GuestServerDetailRepository detailRepository;
    @Mock ProvisioningProgressRepository progressRepository;
    @Mock RedfishPowerService powerService;
    @Mock PlatformTransactionManager txManager;

    private BootOverrideReconciler reconciler;
    private GuestServer server;

    @BeforeEach
    void setUp() {
        lenient().when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        reconciler = new BootOverrideReconciler(guestServerRepository, detailRepository, progressRepository, powerService, txManager);
        server = GuestServer.builder().id(ID).systemUUID(UUID.randomUUID()).build();
        lenient().when(guestServerRepository.findById(ID)).thenReturn(Optional.of(server));
        GuestServerDetail detail = mock(GuestServerDetail.class);
        lenient().when(detail.getBmcIp()).thenReturn(IpAddressVO.of("192.168.1.111"));
        lenient().when(detail.getBoardSerial()).thenReturn("QG26");
        lenient().when(detailRepository.findByGuestServer_Id(ID)).thenReturn(Optional.of(detail));
    }

    private ProvisioningProgress progressAt(ProvisioningPhaseStep step, boolean started) {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID()).guestServer(server)
                .currentStep(step).lastTransitionAt(T).build();
        if (started) {
            p.start(T);
        }
        given(progressRepository.findByGuestServer_Id(ID)).willReturn(Optional.of(p));
        return p;
    }

    private void bmcAccepts() {
        given(powerService.armBootOverride(any(), any()))
                .willReturn(PowerControlResult.sent(RedfishPowerState.UNKNOWN, "ok"));
    }

    @Test
    @DisplayName("개시된 게스트(창 안) → Continuous 를 한 번 세우고, 같은 상태의 재조정은 BMC 를 부르지 않는다")
    void inWindow_armsContinuousOnce() {
        progressAt(ProvisioningPhaseStep.BIOS_UPDATING, true);
        bmcAccepts();

        reconciler.reconcile(ID);
        reconciler.reconcile(ID);

        verify(powerService, times(1)).armBootOverride(eq(new RedfishTarget("192.168.1.111", "QG26")), eq(NextBoot.PXE_CONTINUOUS));
        assertThat(reconciler.armedOf(ID)).isEqualTo(NextBoot.PXE_CONTINUOUS);
    }

    @Test
    @DisplayName("창이 닫히면(종단 · 실패 · 설치 착수) 해제로 뒤집는다 — 실기 3호 F-2b 의 RAID → OS 전이는 아직 창 안")
    void windowClose_releases() {
        ProvisioningProgress p = progressAt(ProvisioningPhaseStep.RAID_VERIFYING, true);
        bmcAccepts();
        reconciler.reconcile(ID);
        assertThat(reconciler.armedOf(ID)).isEqualTo(NextBoot.PXE_CONTINUOUS);

        p.advanceToEntry(ProvisioningPhaseStep.OS_INSTALLING, T.plusMinutes(1));   // RAID → OS 진입 대기 = 아직 PXE 필요
        reconciler.reconcile(ID);
        assertThat(reconciler.armedOf(ID)).isEqualTo(NextBoot.PXE_CONTINUOUS);

        p.positionAt(ProvisioningPhaseStep.OS_INSTALLING, T.plusMinutes(2));          // wimboot 서빙 = 착수 → 창 닫힘
        reconciler.reconcile(ID);
        verify(powerService).armBootOverride(any(), eq(NextBoot.RELEASE));
        assertThat(reconciler.armedOf(ID)).isEqualTo(NextBoot.RELEASE);
    }

    @Test
    @DisplayName("Continuous 세우기 실패는 기억하지 않아 다음 변화에서 다시 시도한다")
    void armFailure_retriesNextTime() {
        progressAt(ProvisioningPhaseStep.BIOS_UPDATING, true);
        given(powerService.armBootOverride(any(), any()))
                .willReturn(PowerControlResult.failed(null, "BMC 재기동 중"))
                .willReturn(PowerControlResult.sent(RedfishPowerState.UNKNOWN, "ok"));

        reconciler.reconcile(ID);
        assertThat(reconciler.armedOf(ID)).isNull();
        reconciler.reconcile(ID);
        assertThat(reconciler.armedOf(ID)).isEqualTo(NextBoot.PXE_CONTINUOUS);
        verify(powerService, times(2)).armBootOverride(any(), eq(NextBoot.PXE_CONTINUOUS));
    }

    @Test
    @DisplayName("애초 상태를 모르는 게스트(미개시)의 해제 실패는 한 번으로 접는다 — 꺼진 장비의 BMC 를 변화마다 두드리지 않는다")
    void unknownRelease_failsOnce() {
        progressAt(ProvisioningPhaseStep.INFORMATION_COLLECTING, false);
        given(powerService.armBootOverride(any(), any())).willReturn(PowerControlResult.failed(null, "연결 불가"));

        reconciler.reconcile(ID);
        reconciler.reconcile(ID);

        verify(powerService, times(1)).armBootOverride(any(), eq(NextBoot.RELEASE));
        assertThat(reconciler.armedOf(ID)).isEqualTo(NextBoot.RELEASE);
    }

    @Test
    @DisplayName("BMC 미검출(bmcIp null) · 게스트 없음은 조정 대상이 아니다 — BMC 호출 0")
    void noBmc_orNoGuest_skips() {
        GuestServerDetail bare = mock(GuestServerDetail.class);
        given(bare.getBmcIp()).willReturn(null);
        given(detailRepository.findByGuestServer_Id(ID)).willReturn(Optional.of(bare));
        reconciler.reconcile(ID);

        UUID gone = UUID.randomUUID();
        given(guestServerRepository.findById(gone)).willReturn(Optional.empty());
        reconciler.reconcile(gone);

        verify(powerService, never()).armBootOverride(any(), any());
    }

    @Test
    @DisplayName("회수된 게스트는 창 밖 — 개시 상태와 무관하게 해제")
    void decommissioned_releases() {
        progressAt(ProvisioningPhaseStep.BIOS_UPDATING, true);
        server.decommission(T);
        bmcAccepts();

        reconciler.reconcile(ID);

        verify(powerService).armBootOverride(any(), eq(NextBoot.RELEASE));
    }
}
