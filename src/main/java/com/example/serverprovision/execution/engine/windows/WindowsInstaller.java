package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.engine.boot.IpxeScripts;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.osinstall.OsFamilyInstaller;
import com.example.serverprovision.execution.engine.osinstall.OsInstallCycle;
import com.example.serverprovision.execution.engine.osinstall.OsInstallLedger;
import com.example.serverprovision.execution.engine.osinstall.OsInstallTimeoutPolicy;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.wininstall.WindowsInstallSource;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImage;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Windows Server 무인 설치를 맡는 OS 설치 계열 설치기다(E4-1-a-3, E4-1-b-4-1 에서 실행기 본문을 옮김). 처음 부팅하면 토큰을
 * 발급하고 응답 파일을 렌더해 wimboot 체인을 낸다. 설치가 시작된 뒤의 재진입 · 스윕 · 운영자 실패는 {@link OsInstallCycle} 가 판정한다.
 *
 * <p>WinPE 는 보고 수단이 없어 스크립트를 내준 사실이 착수다. 착수 기록은 같은 트랜잭션의 {@link #onBootScriptServed} 가 한다(D-1).
 * 설정이 비어 있으면 빈을 끄지 않고 준비도 BLOCKED 로 드러낸다 — 운영자가 무엇이 없는지 읽게 하기 위해서다.</p>
 */
@Slf4j
@Component
public class WindowsInstaller implements OsFamilyInstaller {

    private final WindowsInstallReadinessResolver resolver;
    private final WindowsInstallProperties properties;
    private final WindowsInstallSource source;
    private final WindowsInstallLedger ledger;
    private final WindowsInstallTokenRegistry tokenRegistry;
    private final WindowsInstallTimeoutPolicy timeoutPolicy;
    private final OsInstallCycle cycle;

    public WindowsInstaller(WindowsInstallReadinessResolver resolver, WindowsInstallProperties properties,
                            WindowsInstallSource source, WindowsInstallLedger ledger,
                            WindowsInstallTimeoutPolicy timeoutPolicy, WindowsInstallTokenRegistry tokenRegistry) {
        this.resolver = resolver;
        this.properties = properties;
        this.source = source;
        this.ledger = ledger;
        this.tokenRegistry = tokenRegistry;
        this.timeoutPolicy = timeoutPolicy;
        // 실패로 세운 서버의 응답 파일이 계속 열려 있지 않게 토큰을 회수한다(E4-1-a-3 CP5 F-2).
        this.cycle = new OsInstallCycle(ledger, timeoutPolicy, (server, row) -> tokenRegistry.revoke(server.getId()));
    }

    @Override
    public OsInstallLedger ledger() {
        return ledger;
    }

    @Override
    public OsInstallTimeoutPolicy timeoutPolicy() {
        return timeoutPolicy;
    }

    @Override
    public boolean owns(ProvisioningHistory row) {
        return ledger.owns(row);
    }

    @Override
    public boolean claims(UUID guestServerId) {
        return resolver.claims(guestServerId);
    }

    @Override
    public PhaseReadiness readiness(GuestServer server, ProvisioningProgress progress) {
        return resolver.readiness(server.getId());
    }

    @Override
    public String bootScript(GuestServer server, ProvisioningProgress progress, String reentryUrl) {
        UUID id = server.getId();
        LocalDateTime now = LocalDateTime.now();
        Optional<ProvisioningHistory> running = cycle.runningRow(id, progress);
        if (running.isPresent()) {
            return cycle.reentry(server, progress, running.get(), reentryUrl, now);
        }
        Optional<WindowsInstallReadinessResolver.Resolved> resolved = resolver.resolve(id);
        if (resolved.isEmpty()) {
            // 커서는 이 phase 인데 활성 할당에 OS 설치 단계가 없다(할당 교체 등) — 게이트는 창 밖으로 봐 READY 를 돌려주므로 여기서 세운다.
            return IpxeScripts.shortageHold("windows install target missing (no active assignment)", reentryUrl);
        }
        WindowsInstallReadinessResolver.Resolved r = resolved.get();
        if (r.readiness().isBlocked()) {
            return IpxeScripts.shortageHold(r.readiness().wire(), reentryUrl);   // 게이트와 서빙 사이의 결손(드묾)
        }
        if (r.diskSelection() != null && !r.diskSelection().confident()) {
            // DEFERRED(RAID 구성 뒤 확정)는 카드 안내용이라 준비도를 막지 않는다 — 서빙에는 확정 번호가 필요하므로 여기서 세운다(HF15-5 안전망).
            return IpxeScripts.shortageHold("disk selection not confident", reentryUrl);
        }
        WindowsImage image = r.image().orElseThrow();   // READY 는 이미지 실재를 보장한다(진리표 7번)
        UUID token = UUID.randomUUID();   // R15-2 — autounattend 가 자기 토큰 URL(드라이버 목록)을 품어야 해 먼저 뽑는다
        tokenRegistry.issue(id, token, bundleFor(server, r.target(), image, r.diskSelection(), r.driverSelection(), token));
        return WindowsInstallChainload.script(tokenRegistry.issuedUrlOf(token), image.name().value(), reentryUrl);
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
        Optional<ProvisioningHistory> running = cycle.runningRow(id, progress);
        if (running.isPresent()) {
            cycle.recordReentry(server, running.get(), now);
            return;
        }
        Optional<WindowsInstallReadinessResolver.Resolved> resolved = resolver.resolve(id);
        if (resolved.isEmpty() || resolved.get().readiness().isBlocked()
                || (resolved.get().diskSelection() != null && !resolved.get().diskSelection().confident())) {
            return;                                    // bootScript 가 대기 스크립트를 냈다 — 착수 아님
        }
        cycle.supersedeStale(server, now);   // 정정 전 데이터처럼 옛 서빙 행이 열려 있으면 닫고 간다
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, now);
        WindowsDiskSelection.DiskSelection selection = resolved.get().diskSelection();   // READY 를 지난 서빙이라 CONFIDENT 가 보장된다
        ProvisioningHistory servedRow = ledger.openServed(server, resolved.get().target().imageName(), selection, now);
        ledger.markDrivers(servedRow, resolved.get().driverSelection());   // R15-2 — 이 게스트가 받은 드라이버 목록 · 제외 사유
        log.info("[wininstall] {} — wimboot 체인 서빙 = 착수 : image={}, diskId={}, basis={}, expectedUniqueId={}",
                id, resolved.get().target().imageName(), selection.diskId(),
                selection.basis() == null ? null : selection.basis().wire(), selection.expectedUniqueId());
    }

    /** 운영자 수동 실패 전환의 뒷정리(CP5 F-1 · F-2) — 열린 서빙 행을 OPERATOR 사유로 닫고 토큰을 회수한다. */
    @Override
    public void onOperatorFailed(GuestServer server, ProvisioningProgress progress, LocalDateTime now) {
        cycle.operatorFailed(server, now);
    }

    @Override
    public boolean sweep(GuestServer server, ProvisioningProgress progress, ProvisioningHistory openRow, LocalDateTime now) {
        return cycle.sweep(server, progress, openRow, now);
    }

    private WindowsInstallBundle bundleFor(GuestServer server, WindowsInstallTarget target, WindowsImage image,
                                           WindowsDiskSelection.DiskSelection diskSelection,
                                           WindowsDriverSelection.Selection drivers, UUID token) {
        WindowsInstallAssets assets = source.assets();
        String productKey = properties.productKeysOrEmpty().forEdition(image.editionId()).orElseThrow();
        String autounattend = AutounattendRenderer.render(new AutounattendRenderer.AutounattendValues(
                image.language(), productKey, image.name().value(),
                AutounattendRenderer.computerNameFor(server.getSystemUUID()),
                properties.effectiveTimeZone(), target.administratorPassword(),
                tokenRegistry.baseUrl(), server.issueTokenIfAbsent().value(),   // E4-1-a-4 — 첫 로그온 완료 보고 인자
                diskSelection.diskId(),                                         // E4-1-a-6 — 설치 대상 디스크 번호
                tokenRegistry.urlOf(token) + "/" + WindowsInstallFile.DRIVERS.fileName()));   // R15-2 — 드라이버 목록 URL
        String installBat = InstallBatRenderer.render(properties.shareUnc(), properties.shareUser(), properties.sharePassword());
        return new WindowsInstallBundle(assets.wimboot(), assets.bootWim(),
                WindowsInstallTemplates.WINPESHL_INI, installBat, autounattend,
                (drivers == null ? WindowsDriverSelection.Selection.EMPTY : drivers).toListText());
    }
}
