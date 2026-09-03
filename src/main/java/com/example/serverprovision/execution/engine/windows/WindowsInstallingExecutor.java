package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.boot.IpxeScripts;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ProvisioningPhaseExecutor;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.wininstall.WindowsInstallSource;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * OS 설치 phase 실행기(E4-1-a-3) — Windows Server 무인 설치의 세 갈래: ① 첫 진입 = 토큰 번들 발급 · 렌더 · wimboot
 * 체인 ② 설치 중 재진입 = {@code exit}(로컬 부팅 폴스루) + 재진입 +1 ③ 시한 · 상한 초과 = 실패 전환.
 *
 * <p>WinPE 는 보고 수단이 없으므로 <b>스크립트를 내준 사실이 착수</b>다 — 상태 기록은 {@link #onBootScriptServed} 훅이
 * 같은 트랜잭션에서 한다(D-1). {@link #bootScript} 는 DB 상태를 바꾸지 않되(토큰 발급은 메모리), 시한 · 상한 초과의
 * 실패 전환만은 판정과 기록이 한 자리여야 하므로 여기서 한다(RAID 실행기의 directiveFor 와 같은 결).</p>
 *
 * <p>{@code @ConditionalOnProperty} 를 걸지 않는다 — 설정이 비어 있는 상태는 빈의 부재가 아니라 준비도의 BLOCKED 로
 * 드러나야 운영자가 "왜 아무 일도 안 일어나는가" 대신 "무엇이 없는가" 를 읽는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WindowsInstallingExecutor implements ProvisioningPhaseExecutor {

    private final WindowsInstallReadinessResolver resolver;
    private final WindowsInstallProperties properties;
    private final WindowsInstallSource source;
    private final WindowsInstallLedger ledger;
    private final WindowsInstallTimeoutPolicy timeoutPolicy;
    private final WindowsInstallTokenRegistry tokenRegistry;

    @Override
    public ProvisioningPhase phase() {
        return ProvisioningPhase.OS_INSTALLING;
    }

    @Override
    public PhaseReadiness readiness(GuestServer server, ProvisioningProgress progress) {
        return resolver.readiness(server.getId());
    }

    @Override
    public String bootScript(GuestServer server, ProvisioningProgress progress, String rebootQuery) {
        UUID id = server.getId();
        LocalDateTime now = LocalDateTime.now();
        Optional<ProvisioningHistory> running = runningRow(id, progress);
        if (running.isPresent()) {
            return reentry(server, progress, running.get(), rebootQuery, now);
        }
        Optional<WindowsInstallReadinessResolver.Resolved> resolved = resolver.resolve(id);
        if (resolved.isEmpty()) {
            // 커서는 이 phase 인데 활성 할당에 OS 설치 단계가 없다(할당 교체 등) — 게이트는 창 밖으로 봐 READY 를 돌려주므로 여기서 세운다.
            return IpxeScripts.shortageHold("windows install target missing (no active assignment)", rebootQuery);
        }
        WindowsInstallReadinessResolver.Resolved r = resolved.get();
        if (r.readiness().isBlocked()) {
            return IpxeScripts.shortageHold(r.readiness().wire(), rebootQuery);   // 게이트와 서빙 사이의 결손(드묾)
        }
        WindowsImage image = r.image().orElseThrow();   // READY 는 이미지 실재를 보장한다(진리표 7번)
        UUID token = tokenRegistry.issue(id, bundleFor(server, r.target(), image));
        return WindowsInstallChainload.script(tokenRegistry.bundleUrl(token), image.name().value(), rebootQuery);
    }

    /** 설치 중 재진입 — 두 눈금을 넘으면 실패, 아니면 로컬 부팅으로 돌려보낸다(D-2). */
    private String reentry(GuestServer server, ProvisioningProgress progress, ProvisioningHistory row,
                           String rebootQuery, LocalDateTime now) {
        LocalDateTime served = ledger.servedAtOf(row);
        int reentries = ledger.reentriesOf(row);
        int max = timeoutPolicy.maxReentries();
        if (timeoutPolicy.isExpired(served, now)) {
            long elapsed = Duration.between(served, now).toMinutes();
            ledger.failRunning(server, progress, row, WindowsInstallLedger.INSTALL_TIMEOUT,
                    "서빙 후 " + elapsed + "분 — 설치 시한 " + timeoutPolicy.installTimeout().toMinutes() + "분 초과", now);
            tokenRegistry.revoke(server.getId());   // 실패로 세운 서버의 응답 파일이 계속 열려 있지 않게(CP5 F-2)
            log.warn("[wininstall] {} — 설치 시한 초과, 실패 전환 : served={}, reentries={}", server.getId(), served, reentries);
            return IpxeScripts.windowsInstallFailed(WindowsInstallLedger.INSTALL_TIMEOUT, rebootQuery);
        }
        if (reentries + 1 > max) {
            ledger.failRunning(server, progress, row, WindowsInstallLedger.REPXE_LOOP,
                    "재진입 " + (reentries + 1) + "회 — 상한 " + max + "회 초과", now);
            tokenRegistry.revoke(server.getId());
            log.warn("[wininstall] {} — 재진입 상한 초과, 실패 전환 : reentries={}", server.getId(), reentries + 1);
            return IpxeScripts.windowsInstallFailed(WindowsInstallLedger.REPXE_LOOP, rebootQuery);
        }
        return IpxeScripts.localBootFallthrough(reentries + 1, max);
    }

    /**
     * dispatcher 가 이 실행기의 스크립트를 내주기로 한 직후(같은 트랜잭션) — bootScript 가 고른 갈래를 상태에서 다시
     * 읽어 기록한다: 열린 행이 있으면 재진입(+1), 없고 준비됐으면 착수(커서 STEP_RUNNING + RUNNING 행). 실패 전환은
     * bootScript 가 이미 적었으므로 no-op.
     */
    @Override
    public void onBootScriptServed(GuestServer server, ProvisioningProgress progress, LocalDateTime now) {
        if (progress.isFailed() || progress.isCompleted()) {
            return;
        }
        UUID id = server.getId();
        Optional<ProvisioningHistory> running = runningRow(id, progress);
        if (running.isPresent()) {
            int n = ledger.bumpReentry(running.get(), now);
            log.info("[wininstall] {} — 설치 중 재진입 {}회 (exit 로 로컬 부팅)", id, n);
            return;
        }
        Optional<WindowsInstallReadinessResolver.Resolved> resolved = resolver.resolve(id);
        if (resolved.isEmpty() || resolved.get().readiness().isBlocked()) {
            return;                                    // bootScript 가 대기 스크립트를 냈다 — 착수 아님
        }
        // 새 사이클이 시작되는데 옛 서빙 행이 아직 열려 있으면(정정 전 데이터) 닫고 간다 — 열린 행은 언제나 하나다.
        ledger.latestRunning(id).ifPresent(stale ->
                ledger.abortRunning(stale, WindowsInstallLedger.SUPERSEDED, "새 서빙으로 대체 — 열린 채 남아 있던 행", now));
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, now);
        ledger.openServed(server, resolved.get().target().imageName(), now);
        log.info("[wininstall] {} — wimboot 체인 서빙 = 착수 : image={}", id, resolved.get().target().imageName());
    }

    /**
     * 운영자 수동 실패 전환의 뒷정리(CP5 F-1 · F-2) — 열린 서빙 행을 OPERATOR 사유로 닫고 토큰을 회수한다.
     * 진행 신호는 호출자가 이미 실패로 바꿨다. 게스트가 보고하지 않는 phase 라 서버가 닫지 않으면 행이 영원히 열려 있다.
     */
    @Override
    public void onOperatorFailed(GuestServer server, ProvisioningProgress progress, LocalDateTime now) {
        ledger.latestRunning(server.getId()).ifPresent(row ->
                ledger.abortRunning(row, WindowsInstallLedger.OPERATOR, "운영자 수동 실패 전환", now));
        tokenRegistry.revoke(server.getId());
    }

    private Optional<ProvisioningHistory> runningRow(UUID guestServerId, ProvisioningProgress progress) {
        return progress.getMotion() == ProvisioningMotion.STEP_RUNNING
                ? ledger.latestRunning(guestServerId) : Optional.empty();
    }

    private WindowsInstallBundle bundleFor(GuestServer server, WindowsInstallTarget target, WindowsImage image) {
        WindowsInstallAssets assets = source.assets();
        String productKey = properties.productKeysOrEmpty().forEdition(image.editionId()).orElseThrow();
        String autounattend = AutounattendRenderer.render(new AutounattendRenderer.AutounattendValues(
                image.language(), productKey, image.name().value(),
                AutounattendRenderer.computerNameFor(server.getSystemUUID()),
                properties.effectiveTimeZone(), target.administratorPassword(),
                tokenRegistry.baseUrl(), server.issueTokenIfAbsent().value()));   // E4-1-a-4 — 첫 로그온 완료 보고 인자
        String installBat = InstallBatRenderer.render(properties.shareUnc(), properties.shareUser(), properties.sharePassword());
        return new WindowsInstallBundle(assets.wimboot(), assets.bootWim(),
                WindowsInstallTemplates.WINPESHL_INI, installBat, autounattend);
    }
}
