package com.example.serverprovision.execution.engine.firmware.step;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.firmware.AxisResolution;
import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.BmcIdentityGuard;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxisReason;
import com.example.serverprovision.execution.engine.firmware.FirmwareResolution;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.execution.engine.firmware.FlashTaskState;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.service.BmcAddressRediscovery;
import com.example.serverprovision.execution.service.BmcIdentityProbe;
import com.example.serverprovision.execution.service.FirmwareImageTokenRegistry;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishPowerState;
import com.example.serverprovision.global.redfish.RedfishResetType;
import com.example.serverprovision.global.redfish.RedfishTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E2-2 — 각 행이 실제로 무엇을 하는가. 특히 <b>신원 확인이 통과하지 못하면 그 아래 호출을 한 줄도
 * 내지 않는다</b>는 것(D-11)과, 축이 <b>따로 성패한다</b>는 것(D-2)을 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FlashStepExecutionTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 23, 12, 0);
    private static final String SERIAL = "QG260700082";

    @Mock ProvisioningHistoryRecorder recorder;
    @Mock FirmwareUpdateProvider provider;
    @Mock RedfishPowerService powerService;
    @Mock BmcAddressRediscovery rediscovery;
    @Mock FirmwareImageTokenRegistry tokenRegistry;
    @Mock PhaseCursorAdvancer cursorAdvancer;

    private FlashLedger ledger;
    private FlashTimeoutPolicy timeoutPolicy;
    private BmcIdentityGuard guard;

    @BeforeEach
    void setUp() {
        ledger = new FlashLedger(recorder);
        timeoutPolicy = new FlashTimeoutPolicy(new MockEnvironment());
        // E3-1 — 판정 · 주소 재발견은 Probe 로 옮겨졌다. mock 재발견을 실물 Probe 로 감싸면 행동은 종전과 같다.
        guard = new BmcIdentityGuard(new BmcIdentityProbe(rediscovery), timeoutPolicy, ledger);
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MATCHED);
        given(tokenRegistry.issue(any(), any(), any())).willReturn(UUID.randomUUID());
        given(tokenRegistry.urlFor(any())).willReturn("http://server/api/pxe/v1/firmware/tok");
    }

    // ---- 4행 착수 ------------------------------------------------------------

    @Test
    @DisplayName("착수 — 커서를 첫 축에 놓고 전원을 끈다(왕복은 phase 수준에서 한 번)")
    void begin_positionsCursorAndPowersOff() {
        ProvisioningProgress progress = started();
        FlashContext ctx = context(progress, List.of(), ready());

        new BeginFlashStep(guard, powerService).execute(ctx);

        assertThat(progress.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.BIOS_UPDATING);
        verify(powerService).reset(any(), eq(RedfishResetType.FORCE_OFF));
    }

    @Test
    @DisplayName("착수 — 신원이 다르면 전원 명령을 내지 않고 즉시 실패한다(남의 장비일 수 있다)")
    void begin_identityMismatchIssuesNothing() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MISMATCHED);
        ProvisioningProgress progress = started();

        new BeginFlashStep(guard, powerService).execute(context(progress, List.of(), ready()));

        verify(powerService, never()).reset(any(), any());
        assertThat(progress.isFailed()).isTrue();
        assertThat(metaOf(ProvisioningStatus.FAILED)).contains(FlashLedger.IDENTITY_MISMATCH);
    }

    @Test
    @DisplayName("신원 도달 불가 — lease 에서 새 주소를 찾으면 갱신하고 이번 주기는 넘긴다")
    void identityUnreachable_rediscoversAddress() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.UNREACHABLE);
        given(rediscovery.currentAddressOf(any())).willReturn(Optional.of(IpAddressVO.of("10.10.0.77")));
        GuestServerDetail detail = detail();

        FlashContext ctx = new FlashContext(server(), started(), detail, List.of(), ready(), provider, T);
        new BeginFlashStep(guard, powerService).execute(ctx);

        assertThat(detail.getBmcIp()).isEqualTo(IpAddressVO.of("10.10.0.77"));
        verify(powerService, never()).reset(any(), any());
    }

    @Test
    @DisplayName("신원 도달 불가 — 새 주소도 없고 시한이 지나면 실패로 눕는다")
    void identityUnreachable_expiresIntoFailure() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.UNREACHABLE);
        given(rediscovery.currentAddressOf(any())).willReturn(Optional.empty());
        ProvisioningProgress progress = started();

        FlashContext ctx = context(progress, List.of(), ready());
        new BeginFlashStep(guard, powerService).execute(
                new FlashContext(ctx.server(), progress, ctx.detail(), List.of(), ready(), provider, T.plusHours(1)));

        assertThat(progress.isFailed()).isTrue();
        assertThat(metaOf(ProvisioningStatus.FAILED)).contains(FlashLedger.BMC_UNREACHABLE);
    }

    // ---- 5행 굽기 ------------------------------------------------------------

    @Test
    @DisplayName("굽기 — 판정이 SKIPPED 인 축은 굽지 않고 그 사실만 남긴다(나머지는 진행한다)")
    void flash_skippedAxisRecordsOnly() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BIOS);
        FirmwareResolution resolution = new FirmwareResolution(
                AxisResolution.of(FirmwareAxisReason.NO_CANDIDATE),
                AxisResolution.selected(2L, "13.06.27", "/opt/fw/bmc.ima_enc"));

        new FlashAxisStep(guard, tokenRegistry, ledger).execute(context(progress, List.of(), resolution));

        verify(provider, never()).startFlash(any(), any(), any());
        verify(recorder).recordInstant(any(), eq(ProvisioningPhaseStep.BIOS_UPDATING),
                eq(ProvisioningStatus.SKIPPED), any(), any());
    }

    @Test
    @DisplayName("굽기 — 버전이 같아도 굽는다(2026-08-25 결정: 커스텀 이미지는 버전이 같아도 내용이 다르다)")
    void flash_burnsEvenWhenVersionMatches() {
        given(provider.readVersion(any(), eq(FirmwareAxis.BIOS))).willReturn(Optional.of("F29"));
        given(provider.startFlash(any(), eq(FirmwareAxis.BIOS), any()))
                .willReturn(Optional.of("/redfish/v1/TaskService/Tasks/2"));

        new FlashAxisStep(guard, tokenRegistry, ledger)
                .execute(context(flashing(FirmwareAxis.BIOS), List.of(), ready()));

        verify(provider).startFlash(any(), eq(FirmwareAxis.BIOS), any());
    }

    @Test
    @DisplayName("굽기 — 목표와 다르면 굽고, 무엇을 어느 Task 로 굽는지 여는 시점에 적는다")
    void flash_startsAndRecordsTarget() {
        given(provider.readVersion(any(), eq(FirmwareAxis.BIOS))).willReturn(Optional.of("F27"));
        given(provider.startFlash(any(), eq(FirmwareAxis.BIOS), any()))
                .willReturn(Optional.of("/redfish/v1/TaskService/Tasks/2"));

        new FlashAxisStep(guard, tokenRegistry, ledger)
                .execute(context(flashing(FirmwareAxis.BIOS), List.of(), ready()));

        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).openRunning(any(), eq(ProvisioningPhaseStep.BIOS_UPDATING), any(), meta.capture());
        assertThat(meta.getValue()).contains("F29").contains("/redfish/v1/TaskService/Tasks/2");
    }

    @Test
    @DisplayName("굽기 — 요청이 받아들여지지 않으면 토큰을 회수하고 그 축을 실패로 닫는다")
    void flash_requestRejectedRevokesToken() {
        given(provider.readVersion(any(), any())).willReturn(Optional.of("F27"));
        given(provider.startFlash(any(), any(), any())).willReturn(Optional.empty());
        ProvisioningProgress progress = flashing(FirmwareAxis.BIOS);

        new FlashAxisStep(guard, tokenRegistry, ledger).execute(context(progress, List.of(), ready()));

        verify(tokenRegistry).revoke(any(), eq(FirmwareAxis.BIOS));
        assertThat(progress.isFailed()).isTrue();
        assertThat(metaOf(ProvisioningStatus.FAILED)).contains(FlashLedger.FLASH_EXCEPTION);
    }

    // ---- 2행 폴링 ------------------------------------------------------------

    @Test
    @DisplayName("폴링 — Completed 면 그 축만 닫고 phase 는 계속된다(축은 따로 성패한다)")
    void poll_completedClosesAxisOnly() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BIOS);
        ProvisioningHistory row = openFlashRow(FirmwareAxis.BIOS);
        given(provider.pollTask(any(), any())).willReturn(FlashTaskState.COMPLETED);

        new PollFlashTaskStep(timeoutPolicy, ledger, tokenRegistry).execute(context(progress, List.of(row), ready()));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(progress.isFailed()).isFalse();
        // 굽기가 끝났으니 파일을 더 열어 둘 이유가 없다(CP5 F-3).
        verify(tokenRegistry).revoke(any(), eq(FirmwareAxis.BIOS));
        // 무엇을 구웠는지는 지워지지 않는다 — 반영 확인이 대조할 기준이다(CP5 F-1).
        assertThat(row.flashTargetVersion()).isEqualTo("F29");
    }

    @Test
    @DisplayName("폴링 — Exception 이면 그 축을 실패로 닫고 phase 도 실패한다")
    void poll_exceptionFailsPhase() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BIOS);
        ProvisioningHistory row = openFlashRow(FirmwareAxis.BIOS);
        given(provider.pollTask(any(), any())).willReturn(FlashTaskState.FAILED);

        new PollFlashTaskStep(timeoutPolicy, ledger, tokenRegistry).execute(context(progress, List.of(row), ready()));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(FlashLedger.FLASH_EXCEPTION);
        assertThat(progress.isFailed()).isTrue();
    }

    @Test
    @DisplayName("폴링 — 응답 없음은 즉시 실패가 아니다(BMC 재기동 구간을 정상 완료로 되돌리지 않는다)")
    void poll_unreachableWithinLimitHolds() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BIOS);
        ProvisioningHistory row = openFlashRow(FirmwareAxis.BIOS);
        given(provider.pollTask(any(), any())).willReturn(FlashTaskState.UNREACHABLE);

        new PollFlashTaskStep(timeoutPolicy, ledger, tokenRegistry).execute(context(progress, List.of(row), ready()));

        assertThat(row.getFinishedAt()).isNull();
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("폴링 — 시한을 넘긴 응답 없음은 bmc-unreachable 로 눕는다(굽다 난 시한 초과와 어휘가 다르다)")
    void poll_unreachableBeyondLimitFails() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BIOS);
        ProvisioningHistory row = openFlashRow(FirmwareAxis.BIOS);
        given(provider.pollTask(any(), any())).willReturn(FlashTaskState.UNREACHABLE);

        FlashContext late = new FlashContext(server(), progress, detail(), List.of(row), ready(), provider,
                T.plusMinutes(16));   // BIOS 기본 시한 15분
        new PollFlashTaskStep(timeoutPolicy, ledger, tokenRegistry).execute(late);

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(FlashLedger.BMC_UNREACHABLE);
    }

    // ---- 6행 전원 · 8행 확인 --------------------------------------------------

    @Test
    @DisplayName("전원 — 이미 켜져 있으면 다시 넣지 않는다(멱등이라 별도 표시가 필요 없다)")
    void powerOn_idempotent() {
        given(powerService.powerState(any())).willReturn(PowerControlResult.sent(RedfishPowerState.ON, "ON"));

        new PowerOnStep(guard, powerService, timeoutPolicy, ledger)
                .execute(context(flashing(FirmwareAxis.BMC), closedBoth(), ready()));

        verify(powerService, never()).powerOnAndVerify(any());
    }

    @Test
    @DisplayName("전원 — 복귀 시한을 넘기면 커서 자리에 return-timeout 으로 눕는다")
    void powerOn_returnTimeout() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BMC);
        FlashContext late = new FlashContext(server(), progress, detail(), closedBoth(), ready(), provider,
                T.plusMinutes(30));   // 복귀 시한 20분

        new PowerOnStep(guard, powerService, timeoutPolicy, ledger).execute(late);

        assertThat(progress.isFailed()).isTrue();
        assertThat(metaOf(ProvisioningStatus.FAILED)).contains(FlashLedger.RETURN_TIMEOUT);
    }

    @Test
    @DisplayName("확인 — 축마다 원장에 적힌 목표와 대조하고, 전부 맞으면 전진한다")
    void verify_allMatchAdvances() {
        given(provider.readVersion(any(), eq(FirmwareAxis.BIOS))).willReturn(Optional.of("F29"));
        given(provider.readVersion(any(), eq(FirmwareAxis.BMC))).willReturn(Optional.of("13.06.27"));
        ProvisioningProgress progress = flashing(FirmwareAxis.BMC);

        new VerifyFlashStep(guard, cursorAdvancer, ledger)
                .execute(context(progress, closedWithTargets(), ready()));

        verify(cursorAdvancer).advanceOrComplete(eq(progress), any(), any());
    }

    @Test
    @DisplayName("확인 — 반영이 어긋나면 그 축에 두 번째 행을 남긴다(전송 완료 사실을 지우지 않는다)")
    void verify_mismatchAppendsSecondRow() {
        given(provider.readVersion(any(), eq(FirmwareAxis.BIOS))).willReturn(Optional.of("F27"));
        ProvisioningProgress progress = flashing(FirmwareAxis.BMC);

        new VerifyFlashStep(guard, cursorAdvancer, ledger)
                .execute(context(progress, closedWithTargets(), ready()));

        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
        assertThat(progress.isFailed()).isTrue();
        assertThat(metaOf(ProvisioningStatus.FAILED)).contains(FlashLedger.VERIFY_MISMATCH);
    }

    @Test
    @DisplayName("확인 — 신원이 다르면 인벤토리를 읽지 않는다(잘못된 값으로 성공을 선언하지 않는다)")
    void verify_identityMismatchReadsNothing() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MISMATCHED);

        new VerifyFlashStep(guard, cursorAdvancer, ledger)
                .execute(context(flashing(FirmwareAxis.BMC), closedWithTargets(), ready()));

        verify(provider, never()).readVersion(any(), any());
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
    }

    // ---- 픽스처 --------------------------------------------------------------

    private String metaOf(ProvisioningStatus status) {
        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordInstant(any(), any(), eq(status), meta.capture(), any());
        return meta.getValue();
    }

    private FlashContext context(ProvisioningProgress progress, List<ProvisioningHistory> history,
                                 FirmwareResolution resolution) {
        return new FlashContext(server(), progress, detail(), history, resolution, provider, T);
    }

    private static GuestServer server() {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    }

    private static GuestServerDetail detail() {
        return GuestServerDetail.builder()
                .bmcIp(IpAddressVO.of("10.10.0.51"))
                .bmcMac(MacAddressVO.of("00:1f:c6:e2:1b:01"))
                .boardSerial(SERIAL)
                .build();
    }

    private static ProvisioningProgress started() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.BIOS_UPDATING)
                .lastTransitionAt(T)
                .build();
        p.start(T);
        return p;
    }

    private static ProvisioningProgress flashing(FirmwareAxis axis) {
        ProvisioningProgress p = started();
        p.positionAt(axis.getStep(), T);
        return p;
    }

    private static FirmwareResolution ready() {
        return new FirmwareResolution(
                AxisResolution.selected(1L, "F29", "/opt/fw/bios/image.RBU"),
                AxisResolution.selected(2L, "13.06.27", "/opt/fw/bmc/image.ima_enc"));
    }

    private static ProvisioningHistory openFlashRow(FirmwareAxis axis) {
        return ProvisioningHistory.openRunning(server(), axis.getStep(), T,
                ProvisioningHistory.flashTargetMeta("F29", 1L, "/redfish/v1/TaskService/Tasks/2"));
    }

    private static List<ProvisioningHistory> closedBoth() {
        return List.of(closed(FirmwareAxis.BIOS, "F29"), closed(FirmwareAxis.BMC, "13.06.27"));
    }

    private static List<ProvisioningHistory> closedWithTargets() {
        return closedBoth();
    }

    private static ProvisioningHistory closed(FirmwareAxis axis, String target) {
        return ProvisioningHistory.instant(server(), axis.getStep(), ProvisioningStatus.SUCCEEDED,
                ProvisioningHistory.flashTargetMeta(target, 1L, "/redfish/v1/TaskService/Tasks/2"),
                T.plusMinutes(1));
    }
}
