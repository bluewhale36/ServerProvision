package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.AgentDirective;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.wininstall.WindowsInstallSource;
import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E4-1-a-3 CP4 — 실행기의 세 갈래(첫 서빙 · 재진입 exit · 시한/상한 실패)와 착수 훅의 전이. bootScript 가 고른 갈래와
 * onBootScriptServed 가 기록하는 갈래가 짝이 맞는 것이 이 파일의 요점이다(D-1 · D-2).
 */
@ExtendWith(MockitoExtension.class)
class WindowsInstallingExecutorTest {

    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final UUID SYSTEM_UUID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 12, 0);
    private static final WindowsImageName STANDARD = new WindowsImageName("Windows Server 2025 SERVERSTANDARD");
    private static final Pattern TOKEN = Pattern.compile("windows/([0-9a-f-]{36})/wimboot");

    @Mock WindowsInstallReadinessResolver resolver;
    @Mock WindowsInstallSource source;
    @Mock WindowsInstallLedger ledger;

    private final WindowsInstallProperties properties = new WindowsInstallProperties("/srv/pxe/win2025",
            "\\\\10.0.0.7\\win2025", "deploy", "s3cret-9x", null,
            new WindowsInstallProperties.ProductKeys("KEY11-STAND-ARD00-XXXXX-YYYYY", null));
    private final WindowsInstallTimeoutPolicy timeoutPolicy = new WindowsInstallTimeoutPolicy(Duration.ofMinutes(60), 5);
    private final WindowsInstallTokenRegistry tokenRegistry = new WindowsInstallTokenRegistry("http://10.0.0.7:8080");
    private WindowsInstallingExecutor executor;

    private final GuestServer guest = GuestServer.builder().id(GUEST_ID).systemUUID(SYSTEM_UUID).build();

    @BeforeEach
    void setUp() {
        executor = new WindowsInstallingExecutor(resolver, properties, source, ledger, timeoutPolicy, tokenRegistry);
        lenient().when(source.assets()).thenReturn(new WindowsInstallAssets(
                Path.of("/srv/pxe/win2025/wimboot"), true, Path.of("/srv/pxe/win2025/sources/boot.wim"), true,
                Path.of("/srv/pxe/win2025/sources/setup.exe"), true));
    }

    private static WindowsImage image() {
        return new WindowsImage(2, STANDARD, "Windows Server 2025 Standard (데스크톱 환경)", "ServerStandard", "Server", "ko-KR", "10.0.26100.1742");
    }

    private static WindowsInstallReadinessResolver.Resolved ready() {
        return new WindowsInstallReadinessResolver.Resolved(WindowsInstallTarget.windows(STANDARD, "P@ssw0rd!"),
                InstallSourceSnapshot.present(List.of(image()), 1L, Instant.now()), Optional.of(image()), PhaseReadiness.ready());
    }

    private static WindowsInstallReadinessResolver.Resolved blocked(String wire) {
        return new WindowsInstallReadinessResolver.Resolved(WindowsInstallTarget.windows(STANDARD, "P@ssw0rd!"),
                InstallSourceSnapshot.missing(), Optional.empty(),
                PhaseReadiness.of(ReadinessGrade.BLOCKED, List.of("install.wim 없음"), wire));
    }

    private static ProvisioningProgress awaitingBoot() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(NOW).build();
        p.start(NOW);
        return p;
    }

    private static ProvisioningProgress running() {
        ProvisioningProgress p = awaitingBoot();
        p.positionAt(ProvisioningPhaseStep.OS_INSTALLING, NOW);
        return p;
    }

    private ProvisioningHistory runningRow(LocalDateTime served, int reentries) {
        ProvisioningHistory row = ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, served,
                "{\"origin\":\"windows-install\",\"image\":\"" + STANDARD.value() + "\",\"served\":\"" + served + "\",\"reentries\":" + reentries + "}");
        given(ledger.latestRunning(GUEST_ID)).willReturn(Optional.of(row));
        given(ledger.servedAtOf(row)).willReturn(served);
        given(ledger.reentriesOf(row)).willReturn(reentries);
        return row;
    }

    private static UUID tokenOf(String script) {
        Matcher m = TOKEN.matcher(script);
        assertThat(m.find()).as("wimboot 체인의 토큰").isTrue();
        return UUID.fromString(m.group(1));
    }

    @Test
    @DisplayName("① 첫 진입 — wimboot 체인(토큰 URL 5 · 이미지 echo · 실패 폴백) + 번들 렌더(ComputerName · 이미지 · 키 · 평문 없음)")
    void firstEntry_servesChainAndBundle() {
        given(resolver.resolve(GUEST_ID)).willReturn(Optional.of(ready()));
        ProvisioningProgress progress = awaitingBoot();

        String script = executor.bootScript(guest, progress, "systemUUID=x");

        assertThat(script).startsWith("#!ipxe")
                .contains("echo [provision] windows install: Windows Server 2025 SERVERSTANDARD")
                .contains("kernel http://10.0.0.7:8080/api/pxe/v1/windows/")
                .contains("/winpeshl.ini winpeshl.ini || goto failed")
                .contains("/install.bat install.bat || goto failed")
                .contains("/autounattend.xml autounattend.xml || goto failed")
                .contains("/boot.wim boot.wim || goto failed")
                .contains("chain /api/pxe/v1/boot?systemUUID=x");
        WindowsInstallBundle bundle = tokenRegistry.resolve(tokenOf(script)).orElseThrow();
        assertThat(bundle.wimboot()).isEqualTo(Path.of("/srv/pxe/win2025/wimboot"));
        assertThat(bundle.autounattendXml()).contains("<ComputerName>SPV-14174000</ComputerName>")
                .contains("<Value>Windows Server 2025 SERVERSTANDARD</Value>")
                .contains("<Key>KEY11-STAND-ARD00-XXXXX-YYYYY</Key>")
                .contains("<UILanguage>ko-KR</UILanguage>")
                .doesNotContain("P@ssw0rd!");
        assertThat(bundle.installBat()).contains("net use N: \\\\10.0.0.7\\win2025 /user:deploy \"s3cret-9x\"");
        assertThat(bundle.winpeshlIni()).contains("install.bat");
        assertThat(progress.getMotion()).isEqualTo(ProvisioningMotion.AWAITING_BOOT);   // bootScript 는 상태를 바꾸지 않는다
    }

    @Test
    @DisplayName("① 착수 훅 — 첫 서빙 뒤 커서 STEP_RUNNING + 원장 RUNNING 행(이미지) — 게스트 보고 없이 서버가 연다")
    void firstEntry_hookOpensStep() {
        given(resolver.resolve(GUEST_ID)).willReturn(Optional.of(ready()));
        ProvisioningProgress progress = awaitingBoot();
        executor.bootScript(guest, progress, "q");

        executor.onBootScriptServed(guest, progress, NOW);

        assertThat(progress.getMotion()).isEqualTo(ProvisioningMotion.STEP_RUNNING);
        assertThat(progress.getCurrentStep()).isEqualTo(ProvisioningPhaseStep.OS_INSTALLING);
        verify(ledger).openServed(guest, STANDARD, NOW);
    }

    @Test
    @DisplayName("창 밖 — 활성 할당에 OS 설치 단계가 없으면 대기 스크립트, 훅은 아무것도 열지 않는다")
    void noTarget_holdsWithoutOpening() {
        given(resolver.resolve(GUEST_ID)).willReturn(Optional.empty());
        ProvisioningProgress progress = awaitingBoot();

        String script = executor.bootScript(guest, progress, "q");
        executor.onBootScriptServed(guest, progress, NOW);

        assertThat(script).contains("waiting for resources: windows install target missing");
        assertThat(progress.getMotion()).isEqualTo(ProvisioningMotion.AWAITING_BOOT);
        verify(ledger, never()).openServed(any(), any(), any());
    }

    @Test
    @DisplayName("결손 — 게이트와 서빙 사이에 재료가 무너지면 사유(wire)를 실은 대기 스크립트 · 착수 아님")
    void blocked_holdsWithWire() {
        given(resolver.resolve(GUEST_ID)).willReturn(Optional.of(blocked("install.wim missing")));
        ProvisioningProgress progress = awaitingBoot();

        String script = executor.bootScript(guest, progress, "q");
        executor.onBootScriptServed(guest, progress, NOW);

        assertThat(script).contains("waiting for resources: install.wim missing");
        assertThat(progress.getMotion()).isEqualTo(ProvisioningMotion.AWAITING_BOOT);
        verify(ledger, never()).openServed(any(), any(), any());
    }

    @Test
    @DisplayName("② 설치 중 재진입 — exit(로컬 부팅 폴스루) + 재진입 n/max · 훅은 bumpReentry (정상 재부팅은 막지 않는다)")
    void reentry_exitsAndBumps() {
        ProvisioningProgress progress = running();
        ProvisioningHistory row = runningRow(LocalDateTime.now().minusMinutes(5), 1);

        String script = executor.bootScript(guest, progress, "q");
        executor.onBootScriptServed(guest, progress, NOW);

        assertThat(script).contains("windows setup in progress (reentry 2/5). booting local disk...")
                .contains("this server: ip=${ip} mac=${mac} uuid=${uuid}")
                .contains("exit").doesNotContain("chain");
        verify(ledger).bumpReentry(row, NOW);
        verify(ledger, never()).openServed(any(), any(), any());
        verify(ledger, never()).failRunning(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("③ 재진입 상한 — 6회째 재진입은 REPXE_LOOP 로 실패 전환 + 실패 안내 스크립트")
    void reentry_overLimit_failsLoop() {
        ProvisioningProgress progress = running();
        ProvisioningHistory row = runningRow(LocalDateTime.now().minusMinutes(20), 5);

        String script = executor.bootScript(guest, progress, "q");

        assertThat(script).contains("windows install FAILED (REPXE_LOOP). waiting for operator...");
        verify(ledger).failRunning(eq(guest), eq(progress), eq(row), eq(WindowsInstallLedger.REPXE_LOOP), contains("6회"), any());
    }

    @Test
    @DisplayName("③ 설치 시한 — 서빙 후 60분을 넘긴 재진입은 INSTALL_TIMEOUT (상한 검사보다 먼저)")
    void reentry_afterTimeout_failsTimeout() {
        ProvisioningProgress progress = running();
        ProvisioningHistory row = runningRow(LocalDateTime.now().minusMinutes(61), 0);

        String script = executor.bootScript(guest, progress, "q");

        assertThat(script).contains("windows install FAILED (INSTALL_TIMEOUT)");
        verify(ledger).failRunning(eq(guest), eq(progress), eq(row), eq(WindowsInstallLedger.INSTALL_TIMEOUT), contains("설치 시한 60분 초과"), any());
    }

    @Test
    @DisplayName("실패 뒤의 훅 — bootScript 가 이미 원장에 적었으므로 no-op")
    void hook_afterFailure_isNoop() {
        ProvisioningProgress progress = running();
        progress.markFailed(NOW);

        executor.onBootScriptServed(guest, progress, NOW);

        verify(ledger, never()).bumpReentry(any(), any());
        verify(ledger, never()).openServed(any(), any(), any());
    }

    @Test
    @DisplayName("운영자 재시도 뒤 — 새 토큰으로 다시 서빙되고 옛 토큰은 죽는다(재시도 = 새 사이클)")
    void retry_reservesWithNewToken() {
        given(resolver.resolve(GUEST_ID)).willReturn(Optional.of(ready()));
        ProvisioningProgress progress = awaitingBoot();
        UUID first = tokenOf(executor.bootScript(guest, progress, "q"));
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, NOW);
        progress.markFailed(NOW.plusMinutes(70));
        progress.clearFailed(NOW.plusMinutes(80));   // 운영자 재시도 → AWAITING_BOOT

        UUID second = tokenOf(executor.bootScript(guest, progress, "q"));

        assertThat(second).isNotEqualTo(first);
        assertThat(tokenRegistry.resolve(first)).isEmpty();
        assertThat(tokenRegistry.resolve(second)).isPresent();
        verify(ledger, never()).latestRunning(any());   // AWAITING_BOOT 에서는 열린 행을 묻지 않는다
    }

    @Test
    @DisplayName("SPI 형태 — phase OS_INSTALLING · readiness 는 조립기에 위임 · directive 기본 REBOOT(게스트가 떠나는 phase)")
    void spiShape() {
        given(resolver.readiness(GUEST_ID)).willReturn(PhaseReadiness.ready());

        assertThat(executor.phase()).isEqualTo(ProvisioningPhase.OS_INSTALLING);
        assertThat(executor.readiness(guest, awaitingBoot()).grade()).isEqualTo(ReadinessGrade.READY);
        assertThat(executor.directiveFor(guest, awaitingBoot())).isEqualTo(AgentDirective.REBOOT);
    }

    @Test
    @DisplayName("CP5 F-2 — 시한 · 상한 실패 전환 때 그 게스트의 토큰을 즉시 회수한다(응답 파일이 계속 열려 있지 않게)")
    void engineFailure_revokesToken() {
        given(resolver.resolve(GUEST_ID)).willReturn(Optional.of(ready()));
        UUID token = tokenOf(executor.bootScript(guest, awaitingBoot(), "q"));
        ProvisioningProgress progress = running();
        runningRow(LocalDateTime.now().minusMinutes(20), 5);

        executor.bootScript(guest, progress, "q");   // 6회째 → REPXE_LOOP

        assertThat(tokenRegistry.resolve(token)).isEmpty();
    }

    @Test
    @DisplayName("CP5 F-1 — 운영자 수동 실패 훅: 열린 서빙 행을 OPERATOR 로 닫고 토큰 회수 · 열린 행이 없으면 회수만")
    void onOperatorFailed_closesRowAndRevokes() {
        given(resolver.resolve(GUEST_ID)).willReturn(Optional.of(ready()));
        UUID token = tokenOf(executor.bootScript(guest, awaitingBoot(), "q"));
        ProvisioningProgress progress = running();
        ProvisioningHistory row = ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, NOW, "{\"origin\":\"windows-install\"}");
        given(ledger.latestRunning(GUEST_ID)).willReturn(Optional.of(row));
        progress.markFailed(NOW.plusMinutes(1));

        executor.onOperatorFailed(guest, progress, NOW.plusMinutes(1));

        verify(ledger).abortRunning(eq(row), eq(WindowsInstallLedger.OPERATOR), contains("운영자"), eq(NOW.plusMinutes(1)));
        assertThat(tokenRegistry.resolve(token)).isEmpty();

        given(ledger.latestRunning(GUEST_ID)).willReturn(Optional.empty());
        executor.onOperatorFailed(guest, progress, NOW.plusMinutes(2));   // 열린 행 없음 — 예외 없이 회수만
        verify(ledger, org.mockito.Mockito.times(1)).abortRunning(any(), any(), any(), any());
    }

    @Test
    @DisplayName("자가 치유 — 새 서빙(재시도 뒤)에 옛 열린 행이 남아 있으면 SUPERSEDED 로 닫고 새 행을 연다(열린 행은 하나)")
    void firstEntry_closesStaleOpenRow() {
        given(resolver.resolve(GUEST_ID)).willReturn(Optional.of(ready()));
        ProvisioningProgress progress = awaitingBoot();
        ProvisioningHistory stale = ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, NOW.minusHours(1), "{\"origin\":\"windows-install\"}");
        given(ledger.latestRunning(GUEST_ID)).willReturn(Optional.of(stale));

        executor.bootScript(guest, progress, "q");
        executor.onBootScriptServed(guest, progress, NOW);

        verify(ledger).abortRunning(eq(stale), eq(WindowsInstallLedger.SUPERSEDED), any(), eq(NOW));
        verify(ledger).openServed(guest, STANDARD, NOW);
    }
}
