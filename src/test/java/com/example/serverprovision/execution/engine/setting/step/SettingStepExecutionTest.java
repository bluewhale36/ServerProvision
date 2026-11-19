package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.engine.setting.BiosSettingTarget;
import com.example.serverprovision.execution.engine.setting.BmcSettingTarget;
import com.example.serverprovision.execution.engine.setting.BmcStandardSettings;
import com.example.serverprovision.execution.engine.setting.SettingCursor;
import com.example.serverprovision.execution.engine.setting.SettingLedger;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.service.BmcAddressRediscovery;
import com.example.serverprovision.execution.service.BmcIdentityProbe;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.execution.vo.MacAddressVO;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishBiosService;
import com.example.serverprovision.global.redfish.RedfishError;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishPowerState;
import com.example.serverprovision.global.redfish.RedfishRequestException;
import com.example.serverprovision.global.redfish.RedfishResetType;
import com.example.serverprovision.global.redfish.RedfishResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E3-1 D-4 · D-5 — 각 행이 실제로 무엇을 하는가. 고정하는 것은 넷이다 — ① 원장은 PATCH <b>보다 먼저</b> 열린다
 * (크래시 시 굽기 유실 방지) ② pending 부재는 관찰 기록이지 실패가 아니다 ③ readback 은 컨텍스트가 아니라
 * <b>원장에 적힌 목표</b>와 대조한다(E2-2 F-1) ④ 신원이 다르면 그 아래 호출을 한 줄도 내지 않는다(D-6).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingStepExecutionTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 25, 12, 0);
    /** 재부팅 시각은 재시작 명령 뒤의 실제 시각이다(HF25) — 기존 시나리오는 그 시각을 T 로 고정해 기대값을 그대로 둔다. */
    private static final java.time.Clock CLOCK_AT_T = java.time.Clock.fixed(
            T.atZone(java.time.ZoneId.systemDefault()).toInstant(), java.time.ZoneId.systemDefault());
    private static final String SERIAL = "QG260700082";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, Object> TARGET = Map.of("BootMode", "UEFI", "NumLock", "On");

    @Mock ProvisioningHistoryRecorder recorder;
    @Mock FirmwareUpdateProvider provider;
    @Mock com.example.serverprovision.execution.engine.setting.BiosRegistryCapturePort registryPort;
    @Mock BmcAddressRediscovery rediscovery;
    @Mock RedfishBiosService biosService;
    @Mock RedfishPowerService powerService;
    @Mock PhaseCursorAdvancer cursorAdvancer;

    private SettingLedger ledger;
    private FlashTimeoutPolicy timeoutPolicy;
    private BmcIdentityProbe probe;
    private SettingCursor settingCursor;
    /** recorder 가 연 행 — 실물 엔티티를 돌려주도록 answer 를 걸고 여기서 붙잡는다. */
    private final AtomicReference<ProvisioningHistory> opened = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        ledger = new SettingLedger(recorder, JSON);
        timeoutPolicy = new FlashTimeoutPolicy(new MockEnvironment());
        probe = new BmcIdentityProbe(rediscovery);
        settingCursor = new SettingCursor(cursorAdvancer);
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MATCHED);
        given(recorder.openRunning(any(), any(), any(), any())).willAnswer(inv -> {
            ProvisioningHistory row = ProvisioningHistory.openRunning(
                    inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3));
            opened.set(row);
            return row;
        });
        given(biosService.pending(any())).willReturn(Optional.of(attributes(TARGET)));
        given(powerService.powerState(any())).willReturn(PowerControlResult.sent(RedfishPowerState.ON, "On"));
        given(powerService.reset(any(), any())).willReturn(PowerControlResult.sent(RedfishPowerState.ON, "sent"));
        // 레지스트리 채집 불가 = 판정 없음 — 종전 시나리오(PATCH → BMC 판정)를 그대로 지난다(E3-3 Q2).
        given(registryPort.captureAndCheck(any(), any(), any()))
                .willReturn(com.example.serverprovision.execution.engine.setting.RegistryCheck.unavailable());
    }

    private BeginSettingStep begin() {
        return new BeginSettingStep(ledger, probe, biosService, powerService, timeoutPolicy, registryPort,
                new SettingFailure(ledger), CLOCK_AT_T);
    }

    private ReturnReadbackStep readback() {
        return new ReturnReadbackStep(ledger, probe, biosService, timeoutPolicy, settingCursor, new SettingFailure(ledger));
    }

    // ---- 5행 착수 ------------------------------------------------------------

    @Test
    @DisplayName("착수 — 원장을 PATCH 보다 먼저 열고, 목표 전체를 쓰고, pending 을 적고, 재부팅 시각을 남긴다")
    void begin_opensLedgerBeforePatchAndRecordsEverything() {
        ProvisioningProgress progress = started();

        begin().execute(context(server(), progress, List.of(), target(), T));

        InOrder order = inOrder(recorder, biosService, powerService);
        order.verify(recorder).openRunning(any(), eq(ProvisioningPhaseStep.BIOS_SETTING), eq(T), any());
        order.verify(biosService).patchPending(any(), eq(TARGET));
        order.verify(powerService).reset(any(), eq(RedfishResetType.FORCE_RESTART));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(ledger.targetOf(row)).containsExactlyInAnyOrderEntriesOf(TARGET);
        assertThat(row.getStatusMeta()).contains("\"pendingSeen\":true");
        assertThat(ledger.rebootAtOf(row)).isEqualTo(T);
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("착수 — 전원이 꺼져 있으면 재시작이 아니라 켜기를 보낸다(꺼진 장비에 ForceRestart 는 성립하지 않는다)")
    void begin_powerOffSendsOn() {
        given(powerService.powerState(any())).willReturn(PowerControlResult.sent(RedfishPowerState.OFF, "Off"));

        begin().execute(context(server(), started(), List.of(), target(), T));

        verify(powerService).reset(any(), eq(RedfishResetType.ON));
    }

    @Test
    @DisplayName("착수 — PATCH 가 거절되면 열린 행을 목표 보존한 채 실패로 닫고 재부팅은 내지 않는다")
    void begin_patchRejectedClosesRowWithoutReboot() {
        willThrow(new RedfishRequestException(RedfishError.PROTOCOL, "400 Bad Request", null))
                .given(biosService).patchPending(any(), any());
        ProvisioningProgress progress = started();

        begin().execute(context(server(), progress, List.of(), target(), T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.PATCH_REJECTED).contains("400 Bad Request");
        assertThat(ledger.targetOf(row)).containsExactlyInAnyOrderEntriesOf(TARGET);
        assertThat(progress.isFailed()).isTrue();
        verify(powerService, never()).reset(any(), any());
        verify(biosService, never()).pending(any());
    }

    @Test
    @DisplayName("착수 — pending 이 비어 있어도 실패가 아니다(관찰만 적고 재부팅한다 — 증거는 readback 하나)")
    void begin_pendingAbsentIsObservationNotFailure() {
        given(biosService.pending(any())).willReturn(Optional.empty());
        ProvisioningProgress progress = started();

        begin().execute(context(server(), progress, List.of(), target(), T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(row.getStatusMeta()).contains("\"pendingSeen\":false");
        assertThat(ledger.rebootAtOf(row)).isEqualTo(T);
        verify(powerService).reset(any(), any());
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("착수 — 재부팅 명령이 실패하면 행을 rebootAt 없이 남겨 다음 주기가 이어받게 한다")
    void begin_resetFailedLeavesRowResumable() {
        given(powerService.reset(any(), any()))
                .willReturn(PowerControlResult.failed(RedfishPowerState.UNKNOWN, "연결 불가"));
        ProvisioningProgress progress = started();

        begin().execute(context(server(), progress, List.of(), target(), T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(ledger.rebootAtOf(row)).isNull();
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("착수 재개 — 재부팅 전에 죽은 열린 행이 있으면 새로 열지 않고 그 행을 이어 다시 쓴다")
    void begin_resumesOpenRowWithoutOpeningAnother() {
        GuestServer server = server();
        ProvisioningHistory row = openRow(server, null);

        begin().execute(context(server, started(), List.of(row), target(), T.plusMinutes(2)));

        verify(recorder, never()).openRunning(any(), any(), any(), any());
        verify(biosService).patchPending(any(), eq(TARGET));
        assertThat(ledger.rebootAtOf(row)).isEqualTo(T);   // HF25 — 주기 시각(T+2)이 아니라 재시작 명령 뒤의 Clock 시각
    }

    @Test
    @DisplayName("착수 — 신원이 다르면 PATCH 를 내지 않고 커서 자리에 즉시 실패를 적는다(남의 장비일 수 있다)")
    void begin_identityMismatchIssuesNothing() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MISMATCHED);
        ProvisioningProgress progress = started();

        begin().execute(context(server(), progress, List.of(), target(), T));

        verify(biosService, never()).patchPending(any(), any());
        verify(recorder, never()).openRunning(any(), any(), any(), any());
        assertThat(progress.isFailed()).isTrue();
        assertThat(metaOf(ProvisioningStatus.FAILED)).contains(SettingLedger.IDENTITY_MISMATCH);
    }

    @Test
    @DisplayName("착수 재개 — 신원이 다르면 이어받으려던 열린 행을 그 사유로 닫는다(행을 고아로 두지 않는다)")
    void begin_identityMismatchOnResumeClosesOpenRow() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MISMATCHED);
        GuestServer server = server();
        ProvisioningHistory row = openRow(server, null);
        ProvisioningProgress progress = started();

        begin().execute(context(server, progress, List.of(row), target(), T));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.IDENTITY_MISMATCH);
        assertThat(progress.isFailed()).isTrue();
        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("착수 — BMC 에 닿지 못하면 주소를 다시 찾아 갱신하고 이번 주기는 넘긴다")
    void begin_unreachableRediscoversAddress() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.UNREACHABLE);
        given(rediscovery.currentAddressOf(any())).willReturn(Optional.of(IpAddressVO.of("10.10.0.77")));
        GuestServerDetail detail = detail();
        ProvisioningProgress progress = started();

        begin().execute(new SettingContext(server(), progress, detail, List.of(), target(), bmcTarget(), provider, T));

        assertThat(detail.getBmcIp()).isEqualTo(IpAddressVO.of("10.10.0.77"));
        verify(biosService, never()).patchPending(any(), any());
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("착수 — 닿지 못한 채 복귀 시한을 넘기면 bmc-unreachable 로 눕는다")
    void begin_unreachableBeyondLimitFails() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.UNREACHABLE);
        given(rediscovery.currentAddressOf(any())).willReturn(Optional.empty());
        ProvisioningProgress progress = started();

        begin().execute(context(server(), progress, List.of(), target(), T.plusHours(1)));   // 복귀 시한 20분

        assertThat(progress.isFailed()).isTrue();
        assertThat(metaOf(ProvisioningStatus.FAILED)).contains(SettingLedger.BMC_UNREACHABLE);
    }

    // ---- 1행 복귀 · readback ---------------------------------------------------

    @Test
    @DisplayName("복귀 — 재부팅 뒤 게스트 접촉이 아직 없으면 BMC 를 읽지 않고 기다린다")
    void readback_notReturnedHolds() {
        GuestServer server = server();
        ProvisioningHistory row = openRow(server, T);

        readback().execute(context(server, started(), List.of(row), target(), T.plusMinutes(3)));

        verify(biosService, never()).bios(any());
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
    }

    @Test
    @DisplayName("복귀 — 재부팅 이전의 접촉은 복귀가 아니다(신호는 rebootAt 이후의 접촉만)")
    void readback_contactBeforeRebootIsNotReturn() {
        GuestServer server = server();
        server.touchBoot(T.minusMinutes(1));
        ProvisioningHistory row = openRow(server, T);

        readback().execute(context(server, started(), List.of(row), target(), T.plusMinutes(3)));

        verify(biosService, never()).bios(any());
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
    }

    @Test
    @DisplayName("HF24 · 미도착 — 옛 재무장 지연(4분)을 넘겨도 전원을 움직이지 않고 열린 행 그대로 기다린다")
    void readback_notReturnedWaitsWithoutPowerCommand() {
        GuestServer server = server();
        ProvisioningHistory row = openRow(server, T);
        ProvisioningProgress progress = started();

        readback().execute(context(server, progress, List.of(row), target(), T.plusMinutes(5)));

        org.mockito.Mockito.verifyNoInteractions(powerService);
        verify(biosService, never()).bios(any());
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(row.getStatusMeta()).doesNotContain("rearm");
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("복귀 — 시한을 넘겨도 돌아오지 않으면 return-timeout 으로 닫는다")
    void readback_returnTimeoutFails() {
        GuestServer server = server();
        ProvisioningHistory row = openRow(server, T);
        ProvisioningProgress progress = started();

        readback().execute(context(server, progress, List.of(row), target(), T.plusMinutes(30)));   // 복귀 시한 20분

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.RETURN_TIMEOUT);
        assertThat(progress.isFailed()).isTrue();
    }

    @Test
    @DisplayName("readback — 원장의 목표가 전부 반영됐으면 applied 로 닫고(목표 보존) 같은 phase 의 BMC 축으로 옮긴다")
    void readback_allMatchAdvances() {
        GuestServer server = returned();
        ProvisioningHistory row = openRow(server, T);
        given(biosService.bios(any())).willReturn(resource(TARGET));
        ProvisioningProgress progress = started();

        readback().execute(context(server, progress, List.of(row), target(), T.plusMinutes(4)));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.APPLIED);
        assertThat(ledger.targetOf(row)).containsExactlyInAnyOrderEntriesOf(TARGET);
        assertThat(ledger.rebootAtOf(row)).isEqualTo(T);
        // E3-2 D-2 — BIOS 축의 끝은 phase 완주가 아니라 BMC 축이다(표준은 항상).
        assertThat(progress.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.BMC_SETTING);
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("readback — 하나라도 어긋나면 그 속성 이름을 적어 행을 닫고 전진하지 않는다(HF25 — 첫 불일치는 자동 재시도 1/3)")
    void readback_mismatchFailsNamingAttribute() {
        GuestServer server = returned();
        ProvisioningHistory row = openRow(server, T);
        given(biosService.bios(any())).willReturn(resource(Map.of("BootMode", "UEFI", "NumLock", "Off")));
        ProvisioningProgress progress = started();

        readback().execute(context(server, progress, List.of(row), target(), T.plusMinutes(4)));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.READBACK_MISMATCH)
                .contains("\"detail\":\"반영되지 않은 속성: NumLock (자동 재시도 1/3)\"");
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("readback — 대조 기준은 컨텍스트의 목표가 아니라 원장에 적힌 목표다(할당이 바뀌어도 판정은 굽은 것 기준)")
    void readback_comparesAgainstLedgerTargetNotContext() {
        GuestServer server = returned();
        ProvisioningHistory row = openRow(server, T);   // 원장 = TARGET(UEFI · On)
        given(biosService.bios(any())).willReturn(resource(TARGET));
        BiosSettingTarget changed = new BiosSettingTarget(Map.of("BootMode", "Legacy"));

        readback().execute(context(server, started(), List.of(row), changed, T.plusMinutes(4)));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("readback — 숫자 · 불리언은 원시값과 JSON 노드의 꼴이 달라도 같은 값으로 본다")
    void readback_normalizesNumberAndBoolean() {
        GuestServer server = returned();
        Map<String, Object> typed = new LinkedHashMap<>();
        typed.put("Timeout", 5L);
        typed.put("Csm", false);
        ProvisioningHistory row = ProvisioningHistory.openRunning(server, ProvisioningPhaseStep.BIOS_SETTING, T,
                "{\"origin\":\"setting\",\"target\":{\"Timeout\":5,\"Csm\":false}}");
        ledger.markRebooted(row, T);
        given(biosService.bios(any())).willReturn(resource(typed));

        readback().execute(context(server, started(), List.of(row), new BiosSettingTarget(typed), T.plusMinutes(4)));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
    }

    @Test
    @DisplayName("readback — 신원이 다르면 BIOS 를 읽지 않는다(잘못된 값으로 성공을 선언하지 않는다)")
    void readback_identityMismatchReadsNothing() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MISMATCHED);
        GuestServer server = returned();
        ProvisioningHistory row = openRow(server, T);

        readback().execute(context(server, started(), List.of(row), target(), T.plusMinutes(4)));

        verify(biosService, never()).bios(any());
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.IDENTITY_MISMATCH);
    }

    @Test
    @DisplayName("readback — BMC 읽기가 실패하면 판정하지 않고 다음 주기에 다시 읽는다")
    void readback_readFailureRetriesNextCycle() {
        GuestServer server = returned();
        ProvisioningHistory row = openRow(server, T);
        given(biosService.bios(any()))
                .willThrow(new RedfishRequestException(RedfishError.CONNECT_FAILED, "연결 불가", null));
        ProvisioningProgress progress = started();

        readback().execute(context(server, progress, List.of(row), target(), T.plusMinutes(4)));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(progress.isFailed()).isFalse();
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
    }

    // ---- 3행 · 4행 ------------------------------------------------------------

    @Test
    @DisplayName("목표 없음 — 건너뜀을 단발로 적고 BMC 축으로 옮긴다(BIOS 목표가 없어도 BMC 표준은 밟는다)")
    void skipNoTarget_recordsInstantAndAdvances() {
        GuestServer server = server();
        ProvisioningProgress progress = started();

        new SkipNoTargetStep(ledger, settingCursor)
                .execute(context(server, progress, List.of(), new BiosSettingTarget(Map.of()), T));

        assertThat(metaOf(ProvisioningStatus.SKIPPED)).contains(SettingLedger.NO_TARGET);
        assertThat(progress.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.BMC_SETTING);
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("목표 없음 — 재개 중 할당이 바뀌어 열린 행이 있으면 그 행을 건너뜀으로 닫는다")
    void skipNoTarget_closesOpenRow() {
        GuestServer server = server();
        ProvisioningHistory row = openRow(server, null);

        new SkipNoTargetStep(ledger, settingCursor)
                .execute(context(server, started(), List.of(row), new BiosSettingTarget(Map.of()), T));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SKIPPED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.NO_TARGET);
        assertThat(ledger.targetOf(row)).containsExactlyInAnyOrderEntriesOf(TARGET);
        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("BMC 없음 — 목표가 있는데 BMC 가 없으면 커서 자리에 bmc-required 로 눕는다(보류가 아니다)")
    void failNoBmc_failsAtCursor() {
        ProvisioningProgress progress = started();

        new FailNoBmcStep(ledger).execute(new SettingContext(server(), progress,
                GuestServerDetail.builder().boardSerial(SERIAL).build(), List.of(), target(), bmcTarget(), provider, T));

        assertThat(progress.isFailed()).isTrue();
        assertThat(metaOf(ProvisioningStatus.FAILED)).contains(SettingLedger.BMC_REQUIRED);
    }

    // ---- HF25 — 가짜 복귀 정정 · 자동 재시도 -------------------------------------

    @Test
    @DisplayName("HF25 · 착수 — rebootAt 은 주기 시각이 아니라 재시작 명령 뒤의 Clock 시각이다(대기 루프 /boot 의 가짜 복귀 차단)")
    void begin_rebootAtComesFromClockAfterReset() {
        begin().execute(context(server(), started(), List.of(), target(), T.plusMinutes(5)));

        assertThat(ledger.rebootAtOf(opened.get())).isEqualTo(T);   // CLOCK_AT_T — 주기 시각 T+5 가 아니다
    }

    @Test
    @DisplayName("HF25 · 자동 재시도 — 이번 시도에서 이미 3 번 다시 했으면 네 번째 불일치는 진행을 실패로 바꾼다")
    void readback_mismatchAfterThreeAutoRetriesFails() {
        GuestServer server = returned();
        List<ProvisioningHistory> history = new java.util.ArrayList<>(autoRetriedRows(server, 3));
        ProvisioningHistory row = openRow(server, T);
        history.add(row);
        given(biosService.bios(any())).willReturn(resource(Map.of("BootMode", "UEFI", "NumLock", "Off")));
        ProvisioningProgress progress = started();

        readback().execute(context(server, progress, history, target(), T.plusMinutes(4)));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.READBACK_MISMATCH).doesNotContain("자동 재시도");
        assertThat(progress.isFailed()).isTrue();
    }

    @Test
    @DisplayName("HF25 · 자동 재시도 — 운영자 재시도(clearFailed) 뒤에는 회차를 0 부터 다시 센다")
    void readback_operatorRetryResetsAutoRetryCount() {
        GuestServer server = server();
        server.touchBoot(T.plusMinutes(8));
        List<ProvisioningHistory> history = new java.util.ArrayList<>(autoRetriedRows(server, 3));
        ProvisioningProgress progress = started();
        progress.markFailed(T.plusMinutes(4));
        progress.clearFailed(T.plusMinutes(5));               // 운영자 재시도 — lastTransitionAt 이 옛 재시도 행들 뒤로
        ProvisioningHistory row = openRow(server, T.plusMinutes(6));
        history.add(row);
        given(biosService.bios(any())).willReturn(resource(Map.of("BootMode", "UEFI", "NumLock", "Off")));

        readback().execute(context(server, progress, history, target(), T.plusMinutes(9)));

        assertThat(row.getStatusMeta()).contains("(자동 재시도 1/3)").contains("\"autoRetry\":1");
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("HF25 · 자동 재시도 대상 아님 — 복귀 시한 만료는 회차가 남아도 첫 번에 진행을 실패로 바꾼다")
    void readback_returnTimeoutIsNotAutoRetried() {
        GuestServer server = server();
        ProvisioningHistory row = openRow(server, T);
        ProvisioningProgress progress = started();

        readback().execute(context(server, progress, List.of(row), target(), T.plusMinutes(30)));

        assertThat(row.getStatusMeta()).contains(SettingLedger.RETURN_TIMEOUT).doesNotContain("autoRetry");
        assertThat(progress.isFailed()).isTrue();
    }

    /** 이번 시도 안에서 자동 재시도로 닫힌 BIOS 행 n 개 — T+1분부터 1분 간격으로 닫혔다(lastTransitionAt=T 뒤). */
    private static List<ProvisioningHistory> autoRetriedRows(GuestServer server, int n) {
        List<ProvisioningHistory> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= n; i++) {
            ProvisioningHistory r = ProvisioningHistory.openRunning(server, ProvisioningPhaseStep.BIOS_SETTING,
                    T.plusSeconds(i * 60L - 30), "{\"origin\":\"setting\"}");
            r.close(ProvisioningStatus.FAILED, "{\"origin\":\"READBACK_MISMATCH\",\"autoRetry\":" + i + "}", T.plusMinutes(i));
            rows.add(r);
        }
        return rows;
    }

    // ---- 픽스처 --------------------------------------------------------------

    private String metaOf(ProvisioningStatus status) {
        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordInstant(any(), eq(ProvisioningPhaseStep.BIOS_SETTING), eq(status), meta.capture(), any());
        return meta.getValue();
    }

    private SettingContext context(GuestServer server, ProvisioningProgress progress,
                                   List<ProvisioningHistory> history, BiosSettingTarget target, LocalDateTime now) {
        return new SettingContext(server, progress, detail(), history, target, bmcTarget(), provider, now);
    }

    private static GuestServer server() {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    }

    /** 재부팅(T) 뒤에 돌아온 게스트. */
    private static GuestServer returned() {
        GuestServer server = server();
        server.touchBoot(T.plusMinutes(3));
        return server;
    }

    private static GuestServerDetail detail() {
        return GuestServerDetail.builder()
                .bmcIp(IpAddressVO.of("10.10.0.51"))
                .bmcMac(MacAddressVO.of("00:1f:c6:e2:1b:01"))
                .boardSerial(SERIAL)
                .build();
    }

    private static BiosSettingTarget target() {
        return new BiosSettingTarget(TARGET);
    }

    private static BmcSettingTarget bmcTarget() {
        return new BmcSettingTarget(new BmcStandardSettings("Asia/Seoul", false, "pool.ntp.org", "time.nist.gov", false, 0,
                new BmcStandardSettings.Bond(true, "active-backup", "eth1", true)), "MS03-CE0", null);
    }

    private static ProvisioningProgress started() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.BIOS_SETTING)
                .lastTransitionAt(T)
                .build();
        p.start(T);
        return p;
    }

    /** 착수가 열어 둔 행 — 목표는 TARGET, rebootAt 은 있거나(재부팅 뒤) 없다(재부팅 전에 죽음). */
    private ProvisioningHistory openRow(GuestServer server, LocalDateTime rebootAt) {
        ProvisioningHistory row = ProvisioningHistory.openRunning(server, ProvisioningPhaseStep.BIOS_SETTING, T,
                "{\"origin\":\"setting\",\"target\":" + JSON.writeValueAsString(TARGET) + "}");
        if (rebootAt != null) {
            ledger.markRebooted(row, rebootAt);
        }
        return row;
    }

    private static JsonNode attributes(Map<String, Object> values) {
        return JSON.valueToTree(Map.of("Attributes", values));
    }

    private static RedfishResource resource(Map<String, Object> values) {
        return new RedfishResource(attributes(values), "W/\"1\"");
    }

    // ---- E3-3 — 집행 전 레지스트리 대조 ------------------------------------------

    @Test
    @DisplayName("착수 — 채집한 레지스트리 허용값 밖이면 PATCH 없이 VALUE_NOT_IN_REGISTRY 로 닫는다(2026-08-27 실기: BMC 400 을 기다리지 않는다)")
    void begin_registryViolationClosesWithoutPatch() {
        given(registryPort.captureAndCheck(any(), any(), any())).willReturn(
                new com.example.serverprovision.execution.engine.setting.RegistryCheck(true, "F44", true,
                        List.of("Whitley0000 = Disabled(허용 값: Disable, Enable)")));
        ProvisioningProgress progress = started();

        begin().execute(context(server(), progress, List.of(), target(), T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.VALUE_NOT_IN_REGISTRY).contains("F44").contains("Whitley0000");
        assertThat(ledger.targetOf(row)).containsExactlyInAnyOrderEntriesOf(TARGET);
        assertThat(progress.isFailed()).isTrue();
        verify(biosService, never()).patchPending(any(), any());
        verify(powerService, never()).reset(any(), any());
    }

    @Test
    @DisplayName("착수 — 대조가 정합이면 종전대로 PATCH · pending · 재부팅으로 간다")
    void begin_registryOkProceedsToPatch() {
        given(registryPort.captureAndCheck(any(), any(), any())).willReturn(
                new com.example.serverprovision.execution.engine.setting.RegistryCheck(true, "F44", false, List.of()));
        ProvisioningProgress progress = started();

        begin().execute(context(server(), progress, List.of(), target(), T));

        verify(biosService).patchPending(any(), any());
        verify(powerService).reset(any(), any());
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("HF24 · 착수 — 재부팅은 무장 없는 reset 하나이고 행 meta 에 무장 요약(arm)이 남지 않는다")
    void begin_resetsWithoutArming() {
        begin().execute(context(server(), started(), List.of(), target(), T));

        verify(powerService).reset(any(), eq(RedfishResetType.FORCE_RESTART));
        verify(powerService, never()).powerOnOrRestart(any());
        ProvisioningHistory row = opened.get();
        assertThat(row.getStatusMeta()).doesNotContain("\"arm\"");
        assertThat(ledger.rebootAtOf(row)).isEqualTo(T);
    }
}
