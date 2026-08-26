package com.example.serverprovision.execution.engine.setting.step;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.firmware.BmcIdentity;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.engine.firmware.FlashTimeoutPolicy;
import com.example.serverprovision.execution.engine.phase.PhaseCursorAdvancer;
import com.example.serverprovision.execution.engine.setting.BiosSettingTarget;
import com.example.serverprovision.execution.engine.setting.BmcItemOutcome;
import com.example.serverprovision.execution.engine.setting.BmcSettingItem;
import com.example.serverprovision.execution.engine.setting.BmcSettingTarget;
import com.example.serverprovision.execution.engine.setting.BmcStandardSettings;
import com.example.serverprovision.execution.engine.setting.FanProfileResources;
import com.example.serverprovision.execution.engine.setting.ScriptedAmiWebApi;
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
import com.example.serverprovision.global.bmcweb.AmiWebClient;
import com.example.serverprovision.global.bmcweb.AmiWebError;
import com.example.serverprovision.global.bmcweb.AmiWebRequestException;
import com.example.serverprovision.global.bmcweb.AmiWebSession;
import com.example.serverprovision.global.redfish.BmcCredentialsFallback;
import com.example.serverprovision.global.redfish.BmcCredentialsMemory;
import com.example.serverprovision.global.redfish.BmcCredentialsResolver;
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
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E3-2 진리표 v2 의 7행(BMC 착수)과 2행(Bond 재접속) — 고정하는 것: ① 원장은 로그인보다 먼저 열린다 ② 항목은 선언
 * 순서로 쓰고 Bond 가 마지막이다 ③ 거절 · 불일치는 그 자리에서 닫고 뒤 항목은 미수행 ④ Bond 뒤 단절은 실패가 아니라
 * bondAt 이다 ⑤ 신원이 다르면 세션도 열지 않는다 ⑥ 자격증명 소진은 AUTH_REJECTED.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BmcSettingStepExecutionTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 26, 12, 0);
    private static final String SERIAL = "QG260700082";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock ProvisioningHistoryRecorder recorder;
    @Mock FirmwareUpdateProvider provider;
    @Mock BmcAddressRediscovery rediscovery;
    @Mock AmiWebClient webClient;
    @Mock PhaseCursorAdvancer cursorAdvancer;

    private SettingLedger ledger;
    private FlashTimeoutPolicy timeoutPolicy;
    private BmcIdentityProbe probe;
    private SettingCursor settingCursor;
    private BmcCredentialsFallback fallback;
    private AmiWebSession session;
    private ScriptedAmiWebApi api;
    private final AtomicReference<ProvisioningHistory> opened = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        ledger = new SettingLedger(recorder, JSON);
        timeoutPolicy = new FlashTimeoutPolicy(new MockEnvironment());
        probe = new BmcIdentityProbe(rediscovery);
        settingCursor = new SettingCursor(cursorAdvancer);
        fallback = new BmcCredentialsFallback(new BmcCredentialsResolver("admin", "standard-pw"), new BmcCredentialsMemory());
        session = mock(AmiWebSession.class);
        api = new ScriptedAmiWebApi();
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MATCHED);
        given(webClient.login(any(), any())).willReturn(session);
        given(webClient.bind(session)).willReturn(api);
        given(recorder.openRunning(any(), any(), any(), any())).willAnswer(inv -> {
            ProvisioningHistory row = ProvisioningHistory.openRunning(
                    inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3));
            opened.set(row);
            return row;
        });
    }

    private BeginBmcSettingStep begin() {
        return new BeginBmcSettingStep(ledger, probe, webClient, fallback, timeoutPolicy, settingCursor);
    }

    private ReconnectReadbackStep reconnect() {
        return new ReconnectReadbackStep(ledger, probe, webClient, fallback, timeoutPolicy, settingCursor);
    }

    // ---- 7행 착수 --------------------------------------------------------------

    @Test
    @DisplayName("착수 — 원장을 로그인보다 먼저 열고, 4 항목을 선언 순서로 쓰고 되읽어 APPLIED 로 닫은 뒤 phase 완주로 넘긴다")
    void begin_appliesAllInOrder() {
        BmcSettingTarget target = bmcTarget(profile());
        api.applied(target, "FAN_PROFILE");
        ProvisioningProgress progress = bmcAxis();
        GuestServer server = server();

        begin().execute(context(server, progress, List.of(), target, T));

        InOrder order = inOrder(recorder, webClient);
        order.verify(recorder).openRunning(any(), eq(ProvisioningPhaseStep.BMC_SETTING), eq(T), any());
        order.verify(webClient).login(any(), any());
        order.verify(webClient).logout(session);
        assertThat(api.writes()).containsExactly("PUT /api/settings/date-time", "POST /api/cold_redundant-status",
                "POST /api/settings/fanprofile", "PUT /api/settings/network-bond");
        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(row.getStatusMeta()).contains("\"origin\":\"APPLIED\"").contains("\"axis\":\"BMC\"").contains("\"detail\":\"4개 적용\"");
        assertThat(ledger.itemsOf(row)).containsEntry("DATE_TIME", "APPLIED").containsEntry("NETWORK_BOND", "APPLIED");
        verify(cursorAdvancer).advanceOrComplete(eq(progress), eq(server.getId()), any());
        assertThat(progress.isFailed()).isFalse();
    }

    @Test
    @DisplayName("착수 — 보드 프로파일이 없으면 FAN_PROFILE 만 건너뛰고 나머지 3 을 적용해 SUCCEEDED(detail 에 건너뜀 명시)")
    void begin_skipsFanProfileWithoutResource() {
        BmcSettingTarget target = bmcTarget(null);
        api.applied(target, "default");

        begin().execute(context(server(), bmcAxis(), List.of(), target, T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(ledger.itemsOf(row).get("FAN_PROFILE")).startsWith("SKIPPED:NO_FAN_PROFILE");
        assertThat(row.getStatusMeta()).contains("3개 적용 · FAN_PROFILE 건너뜀(NO_FAN_PROFILE");
        assertThat(api.writes()).doesNotContain("POST /api/settings/fanprofile");
    }

    @Test
    @DisplayName("착수 — 어느 항목이 거절되면 그 자리에서 WRITE_REJECTED 로 닫고 뒤 항목(Bond)은 쓰지 않는다")
    void begin_rejectionStopsSequence() {
        BmcSettingTarget target = bmcTarget(profile());
        api.applied(target, "FAN_PROFILE").fail("POST /api/settings/fanprofile", AmiWebError.DATA_REJECTED, 1);
        ProvisioningProgress progress = bmcAxis();

        begin().execute(context(server(), progress, List.of(), target, T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.WRITE_REJECTED).contains("FAN_PROFILE");
        assertThat(ledger.itemsOf(row)).containsEntry("DATE_TIME", "APPLIED").doesNotContainKey("NETWORK_BOND");
        assertThat(api.writes()).doesNotContain("PUT /api/settings/network-bond");
        assertThat(progress.isFailed()).isTrue();
        verify(webClient).logout(session);
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
    }

    @Test
    @DisplayName("착수 — 되읽은 값이 다르면 READBACK_MISMATCH 에 항목과 필드를 적고 멈춘다")
    void begin_mismatchFails() {
        BmcSettingTarget target = bmcTarget(profile());
        api.applied(target, "FAN_PROFILE").respond("/api/settings/date-time",
                "{\"timezone\":\"Etc/GMT+00\",\"ntp_auto_date\":0,\"primary_ntp\":\"pool.ntp.org\",\"secondary_ntp\":\"time.nist.gov\"}");

        begin().execute(context(server(), bmcAxis(), List.of(), target, T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.READBACK_MISMATCH).contains("DATE_TIME").contains("timezone");
        assertThat(api.writes()).containsExactly("PUT /api/settings/date-time");
    }

    @Test
    @DisplayName("착수 — Bond 를 쓴 뒤 연결이 끊기면 실패가 아니다: bondAt 을 적고 RUNNING 으로 남긴다")
    void begin_bondDropLeavesRowWithBondAt() {
        BmcSettingTarget target = bmcTarget(profile());
        api.applied(target, "FAN_PROFILE").fail("GET /api/settings/network-bond", AmiWebError.CONNECT_FAILED, 1);
        ProvisioningProgress progress = bmcAxis();

        begin().execute(context(server(), progress, List.of(), target, T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(ledger.bondAtOf(row)).isEqualTo(T);
        assertThat(ledger.itemsOf(row).get("NETWORK_BOND")).startsWith("RECONNECT_PENDING");
        assertThat(progress.isFailed()).isFalse();
        verify(cursorAdvancer, never()).advanceOrComplete(any(), any(), any());
        verify(webClient).logout(session);
    }

    @Test
    @DisplayName("착수 — 자격증명 후보가 전부 거부되면 AUTH_REJECTED 로 닫고 항목은 하나도 쓰지 않는다")
    void begin_authExhausted() {
        given(webClient.login(any(), any()))
                .willThrow(new AmiWebRequestException(AmiWebError.AUTH_FAILED, "POST /api/session — cc:7", 7, null));
        ProvisioningProgress progress = bmcAxis();

        begin().execute(context(server(), progress, List.of(), bmcTarget(profile()), T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.AUTH_REJECTED);
        assertThat(api.calls).isEmpty();
        assertThat(progress.isFailed()).isTrue();
        // 표준 · 공장 기본 두 후보를 모두 시도했다(E1.6 사다리 공유).
        verify(webClient, org.mockito.Mockito.times(2)).login(any(), any());
    }

    @Test
    @DisplayName("착수 — 신원이 다르면 세션을 열지 않고 원장도 열지 않는다(단발 실패)")
    void begin_identityMismatchOpensNothing() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MISMATCHED);
        ProvisioningProgress progress = bmcAxis();

        begin().execute(context(server(), progress, List.of(), bmcTarget(profile()), T));

        verify(webClient, never()).login(any(), any());
        verify(recorder, never()).openRunning(any(), any(), any(), any());
        assertThat(progress.isFailed()).isTrue();
        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordInstant(any(), eq(ProvisioningPhaseStep.BMC_SETTING), eq(ProvisioningStatus.FAILED), meta.capture(), any());
        assertThat(meta.getValue()).contains(SettingLedger.IDENTITY_MISMATCH);
    }

    @Test
    @DisplayName("착수 재개 — bondAt 없는 열린 행이 있으면 새로 열지 않고 그 행에서 항목을 다시 쓴다(멱등)")
    void begin_resumesOpenRow() {
        BmcSettingTarget target = bmcTarget(profile());
        api.applied(target, "FAN_PROFILE");
        GuestServer server = server();
        ProvisioningHistory row = openBmcRow(server);

        begin().execute(context(server, bmcAxis(), List.of(row), target, T.plusMinutes(1)));

        verify(recorder, never()).openRunning(any(), any(), any(), any());
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(api.writes()).hasSize(4);
    }

    @Test
    @DisplayName("착수 — 항목 도중 연결이 끊기면 RUNNING 으로 남겨 다음 주기가 재개하고, 시한을 넘기면 BMC_UNREACHABLE")
    void begin_connectMidwayWaitsThenExpires() {
        BmcSettingTarget target = bmcTarget(profile());
        api.applied(target, "FAN_PROFILE").fail("PUT /api/settings/date-time", AmiWebError.CONNECT_FAILED, 2);
        ProvisioningProgress progress = bmcAxis();

        begin().execute(context(server(), progress, List.of(), target, T));
        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(progress.isFailed()).isFalse();

        begin().execute(context(server(), progress, List.of(row), target, T.plusHours(1)));   // 복귀 시한 20분
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.BMC_UNREACHABLE);
        assertThat(progress.isFailed()).isTrue();
    }

    @Test
    @DisplayName("착수 — bond.enable=false 면 NETWORK_BOND 는 SKIPPED(BOND_DISABLED) 이고 step 은 SUCCEEDED")
    void begin_disabledBondSkipped() {
        BmcStandardSettings off = new BmcStandardSettings("Asia/Seoul", false, "pool.ntp.org", "time.nist.gov", false, 0,
                new BmcStandardSettings.Bond(false, "active-backup", "eth1", true));
        BmcSettingTarget target = new BmcSettingTarget(off, "MS03-CE0", profile());
        api.applied(target, "FAN_PROFILE");

        begin().execute(context(server(), bmcAxis(), List.of(), target, T));

        ProvisioningHistory row = opened.get();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(ledger.itemsOf(row).get("NETWORK_BOND")).startsWith("SKIPPED:BOND_DISABLED");
        assertThat(api.writes()).doesNotContain("PUT /api/settings/network-bond");
    }

    // ---- 2행 재접속 readback -----------------------------------------------------

    @Test
    @DisplayName("재접속 — Bond 가 되읽히면 그 행을 APPLIED 로 닫고 phase 완주로 넘긴다")
    void reconnect_appliedCloses() {
        BmcSettingTarget target = bmcTarget(profile());
        api.applied(target, "FAN_PROFILE");
        GuestServer server = server();
        ProvisioningHistory row = bondedRow(server);
        ProvisioningProgress progress = bmcAxis();

        reconnect().execute(context(server, progress, List.of(row), target, T.plusSeconds(30)));

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(ledger.itemsOf(row)).containsEntry("NETWORK_BOND", "APPLIED");
        assertThat(ledger.bondAtOf(row)).isEqualTo(T);
        assertThat(api.writes()).isEmpty();   // 다시 쓰지 않고 읽기만 한다
        verify(cursorAdvancer).advanceOrComplete(eq(progress), eq(server.getId()), any());
        verify(webClient).logout(session);
    }

    @Test
    @DisplayName("재접속 — 아직 닿지 않으면 시한 안에서 기다리고, 넘기면 BMC_UNREACHABLE(bondAt 기준)")
    void reconnect_waitsThenExpires() {
        BmcSettingTarget target = bmcTarget(profile());
        api.fail("GET /api/settings/network-bond", AmiWebError.CONNECT_FAILED, 2);
        GuestServer server = server();
        ProvisioningHistory row = bondedRow(server);
        ProvisioningProgress progress = bmcAxis();

        reconnect().execute(context(server, progress, List.of(row), target, T.plusSeconds(20)));
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(progress.isFailed()).isFalse();

        reconnect().execute(context(server, progress, List.of(row), target, T.plusMinutes(30)));   // 복귀 시한 20분
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.BMC_UNREACHABLE);
    }

    @Test
    @DisplayName("재접속 — 되읽은 Bond 가 다르면 READBACK_MISMATCH, 신원이 다르면 IDENTITY_MISMATCH(세션 0)")
    void reconnect_mismatchAndIdentity() {
        BmcSettingTarget target = bmcTarget(profile());
        api.applied(target, "FAN_PROFILE").respond("/api/settings/network-bond",
                "{\"id\":1,\"bond_enable\":0,\"bond_mode\":\"active-backup\",\"bond_ifc\":\"eth1\",\"auto_configuration_enable\":1}");
        GuestServer server = server();
        ProvisioningHistory row = bondedRow(server);
        reconnect().execute(context(server, bmcAxis(), List.of(row), target, T.plusSeconds(30)));
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.READBACK_MISMATCH).contains("bond_enable");

        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.MISMATCHED);
        ProvisioningHistory other = bondedRow(server);
        reconnect().execute(context(server, bmcAxis(), List.of(other), target, T.plusSeconds(30)));
        assertThat(other.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(other.getStatusMeta()).contains(SettingLedger.IDENTITY_MISMATCH);
        verify(webClient, org.mockito.Mockito.times(1)).login(any(), any());   // 첫 시나리오의 1회뿐
    }

    @Test
    @DisplayName("재접속 — Redfish 까지 끊겨 신원을 확인할 수 없으면 세션을 열지 않고 bondAt 기준 시한 안에서 기다린다")
    void reconnect_identityUnreachableWaits() {
        given(provider.verifyIdentity(any(), any())).willReturn(BmcIdentity.UNREACHABLE);
        GuestServer server = server();
        ProvisioningHistory row = bondedRow(server);
        ProvisioningProgress progress = bmcAxis();

        reconnect().execute(context(server, progress, List.of(row), bmcTarget(profile()), T.plusSeconds(30)));
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        verify(webClient, never()).login(any(), any());

        reconnect().execute(context(server, progress, List.of(row), bmcTarget(profile()), T.plusMinutes(30)));
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(row.getStatusMeta()).contains(SettingLedger.BMC_UNREACHABLE);
    }

    // ---- 픽스처 --------------------------------------------------------------

    private SettingContext context(GuestServer server, ProvisioningProgress progress, List<ProvisioningHistory> history,
                                   BmcSettingTarget bmcTarget, LocalDateTime now) {
        return new SettingContext(server, progress, detail(), history, new BiosSettingTarget(Map.of()), bmcTarget, provider, now);
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

    private static BmcSettingTarget bmcTarget(FanProfileResources.FanProfile profile) {
        return new BmcSettingTarget(standard(), "MS03-CE0", profile);
    }

    private static BmcStandardSettings standard() {
        return new BmcStandardSettings("Asia/Seoul", false, "pool.ntp.org", "time.nist.gov", false, 0,
                new BmcStandardSettings.Bond(true, "active-backup", "eth1", true));
    }

    private static FanProfileResources.FanProfile profile() {
        return new FanProfileResources.FanProfile("MS03-CE0",
                JSON.readTree("{\"strVersion\":\"1.00\",\"arrProfile\":[],\"strMode\":\"FAN_PROFILE\"}"), "FAN_PROFILE");
    }

    /** BIOS 축을 마치고 BMC 축으로 옮겨진 커서. */
    private static ProvisioningProgress bmcAxis() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID()).currentStep(ProvisioningPhaseStep.BIOS_SETTING).lastTransitionAt(T).build();
        p.start(T);
        p.positionAt(ProvisioningPhaseStep.BMC_SETTING, T);
        return p;
    }

    private ProvisioningHistory openBmcRow(GuestServer server) {
        return ProvisioningHistory.openRunning(server, ProvisioningPhaseStep.BMC_SETTING, T,
                "{\"origin\":\"setting\",\"axis\":\"BMC\",\"items\":{\"DATE_TIME\":\"APPLIED\"}}");
    }

    private ProvisioningHistory bondedRow(GuestServer server) {
        ProvisioningHistory row = ProvisioningHistory.openRunning(server, ProvisioningPhaseStep.BMC_SETTING, T,
                "{\"origin\":\"setting\",\"axis\":\"BMC\",\"items\":{\"DATE_TIME\":\"APPLIED\",\"COLD_REDUNDANT\":\"APPLIED\",\"FAN_PROFILE\":\"APPLIED\",\"NETWORK_BOND\":\"RECONNECT_PENDING:Bond 적용 뒤 재접속 대기\"}}");
        ledger.markBondAt(row, T);
        return row;
    }
}
