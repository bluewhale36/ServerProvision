package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.boot.IpxeScripts;
import com.example.serverprovision.execution.engine.osinstall.OsFamilyInstaller;
import com.example.serverprovision.execution.engine.osinstall.OsInstallCycle;
import com.example.serverprovision.execution.engine.osinstall.OsInstallLedger;
import com.example.serverprovision.execution.engine.osinstall.OsInstallTimeoutPolicy;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.rhel.kickstart.InstallDisk;
import com.example.serverprovision.execution.engine.rhel.kickstart.KickstartDocument;
import com.example.serverprovision.execution.engine.rhel.kickstart.KickstartRenderer;
import com.example.serverprovision.execution.engine.rhel.kickstart.KickstartRuntime;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.installtree.service.InstallTreeManager;
import com.example.serverprovision.execution.installtree.vo.InstallTree;
import com.example.serverprovision.execution.installtree.vo.TreeAcquisition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * RHEL 계열(Rocky 8 · 9 · 10) kickstart 설치를 맡는 OS 설치 계열 설치기다(E4-1-b-4-2). 처음 부팅하면 설치 트리를 확보하고, 트리가
 * 정의서와 같은 배포판 · 주 버전이면 kickstart 를 렌더해 토큰과 함께 두고 설치 체인을 낸다. 트리를 푸는 중이면 기다리게 하고,
 * 확보에 실패하거나 버전이 다르면 설치 단계를 실패로 닫는다. 착수 뒤 판정은 {@link OsInstallCycle} 가 하고, 행을 닫을 때마다
 * 토큰을 회수하고 트리 보유를 반납한다. 트리 확보는 {@link #bootScript} 에서만 한다 — 조회마다 추출이 시작되면 안 된다(D-5).
 * b-5 전에는 완료 보고가 없어 성공한 설치도 스윕이 시한 실패로 닫는다.
 */
@Slf4j
@Component
public class RhelInstaller implements OsFamilyInstaller {

    static final String TARGET_MISSING_WIRE = "rhel install target missing (no active assignment)";
    static final String DISK_NOT_CONFIDENT_WIRE = "disk selection not confident";

    private final RhelInstallReadinessResolver resolver;
    private final RhelInstallationResolutionProvider provider;
    private final InstallTreeManager treeManager;
    private final RhelInstallLedger ledger;
    private final RhelInstallTokenRegistry tokenRegistry;
    private final RhelInstallTimeoutPolicy timeoutPolicy;
    private final OsInstallCycle cycle;

    public RhelInstaller(RhelInstallReadinessResolver resolver, RhelInstallationResolutionProvider provider,
                         InstallTreeManager treeManager, RhelInstallLedger ledger,
                         RhelInstallTimeoutPolicy timeoutPolicy, RhelInstallTokenRegistry tokenRegistry) {
        this.resolver = resolver;
        this.provider = provider;
        this.treeManager = treeManager;
        this.ledger = ledger;
        this.tokenRegistry = tokenRegistry;
        this.timeoutPolicy = timeoutPolicy;
        this.cycle = new OsInstallCycle(ledger, timeoutPolicy, this::releaseServing);
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
        Optional<RhelInstallReadinessResolver.Resolved> resolved = resolver.resolve(id);
        if (resolved.isEmpty()) {
            return IpxeScripts.shortageHold(TARGET_MISSING_WIRE, reentryUrl);   // 게이트를 지난 뒤 할당이 바뀐 경우(드묾)
        }
        RhelInstallReadinessResolver.Resolved r = resolved.get();
        if (r.readiness().isBlocked()) {
            return IpxeScripts.shortageHold(r.readiness().wire(), reentryUrl);
        }
        if (!(r.disk() instanceof RhelDiskSelection.Selection.Confident confident)) {
            // RAID 구성 뒤에 정해지는 디스크는 준비도를 막지 않는다 — 렌더에는 확정 디스크가 필요하므로 여기서 세운다.
            return IpxeScripts.shortageHold(DISK_NOT_CONFIDENT_WIRE, reentryUrl);
        }
        RhelResolution.Ready ready = r.resolution().ready().orElseThrow();   // 준비도가 막히지 않았으면 해석이 성립했다
        return switch (treeManager.acquire(ready.media().isoId(), id)) {
            case TreeAcquisition.Preparing preparing -> IpxeScripts.installTreePreparing(reentryUrl);
            case TreeAcquisition.Failed failed ->
                    failBeforeServing(server, progress, new RhelInstallFailure.TreeUnavailable(failed.reason()), reentryUrl, now);
            case TreeAcquisition.Ready tree -> serve(server, progress, ready, tree.tree(), confident.disk(), reentryUrl, now);
        };
    }

    /** 트리가 정의서와 같은 배포판 · 주 버전이면 kickstart 를 렌더하고 토큰을 발급한다. 다르면 실패로 닫는다(b-2 개정 5 · Q4). */
    private String serve(GuestServer server, ProvisioningProgress progress, RhelResolution.Ready ready, InstallTree tree,
                         InstallDisk disk, String reentryUrl, LocalDateTime now) {
        RhelInstallMedia media = ready.media();
        if (!media.matches(tree.release())) {
            treeManager.release(media.isoId(), server.getId());   // Ready 로 보유자가 됐으므로 반납한다
            return failBeforeServing(server, progress,
                    new RhelInstallFailure.ReleaseMismatch(tree.release().label(), media.osLabel()), reentryUrl, now);
        }
        UUID token = UUID.randomUUID();   // kickstart 의 url 이 자기 토큰 경로를 품어야 해 먼저 뽑는다
        String treeUrl = tokenRegistry.treeUrlOf(token);
        RhelServing serving = new RhelServing(
                KickstartRenderer.render(ready.target(), new KickstartRuntime(treeUrl, disk)), tree, media, disk);
        tokenRegistry.issue(server.getId(), token, serving);
        return RhelInstallChainload.script(treeUrl, tokenRegistry.kickstartUrlOf(token), media.osLabel(), media.isoId(),
                reentryUrl);
    }

    /** 서빙 전에 정해진 실패 — 단발 실패 행을 적고 실패 안내 스크립트를 낸다. */
    private String failBeforeServing(GuestServer server, ProvisioningProgress progress, RhelInstallFailure reason,
                                     String reentryUrl, LocalDateTime now) {
        ledger.failBeforeServing(server, progress, reason, reason.screenMessage().orElse(""), now);
        log.warn("[rhelinstall] {} — 서빙 전 실패 전환 : reason={}", server.getId(), reason.code());
        return cycle.failedScript(reason, reentryUrl);
    }

    /**
     * dispatcher 가 이 설치기의 스크립트를 내주기로 한 직후(같은 트랜잭션). 열린 행이 있으면 재진입을 적는다. 열린 행이 없는데
     * 이 게스트에게 살아 있는 토큰이 있으면 bootScript 가 방금 설치 체인을 낸 것이므로 착수를 적는다 — 토큰은 행을 닫을 때마다
     * 회수되므로, 열린 행 없이 남은 토큰은 방금 발급한 것뿐이다.
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
        Optional<RhelServing> serving = tokenRegistry.currentOf(id);
        if (serving.isEmpty()) {
            return;                                    // 대기 스크립트를 냈다 — 착수 아님
        }
        long isoId = serving.get().media().isoId();
        cycle.supersedeStale(server, now).flatMap(ledger::isoOf)
                .filter(staleIso -> staleIso != isoId)
                .ifPresent(staleIso -> treeManager.release(staleIso, id));   // 옛 서빙만 잡고 있던 다른 ISO 트리
        progress.positionAt(ProvisioningPhaseStep.OS_INSTALLING, now);
        ledger.openServed(server, serving.get(), now);
        log.info("[rhelinstall] {} — 설치 체인 서빙 = 착수 : iso={}, release={}, tree={}, disk={}", id, isoId,
                serving.get().media().osLabel(), serving.get().tree().release().label(), serving.get().disk().byIdName());
    }

    /**
     * 설치 프로그램이 받을 kickstart — 그 토큰의 게스트가 설치 중(열린 행)일 때만 낸다. 실패로 닫힌 서버의 비밀번호 해시가
     * 토큰 회수 전 틈에도 열려 있지 않게 원장을 함께 본다.
     */
    public Optional<KickstartDocument> kickstartWhileInstalling(UUID token) {
        boolean installing = tokenRegistry.guestOf(token).flatMap(ledger::latestRunning).isPresent();
        return installing ? tokenRegistry.resolve(token).map(RhelServing::kickstart) : Optional.empty();
    }

    @Override
    public void onOperatorFailed(GuestServer server, ProvisioningProgress progress, LocalDateTime now) {
        cycle.operatorFailed(server, now);
    }

    @Override
    public boolean sweep(GuestServer server, ProvisioningProgress progress, ProvisioningHistory openRow, LocalDateTime now) {
        return cycle.sweep(server, progress, openRow, now);
    }

    /**
     * 행을 닫은 뒤의 뒷정리 — 토큰을 회수하고 트리 보유를 반납한다. 반납할 ISO 는 닫은 행의 meta, 살아 있던 토큰, 정의서 순으로
     * 찾는다: 트리를 푸는 중에 운영자가 실패로 바꾸면 행도 토큰도 없이 보유만 남기 때문이다.
     */
    private void releaseServing(GuestServer server, Optional<ProvisioningHistory> closedRow) {
        UUID id = server.getId();
        Optional<Long> isoId = closedRow.flatMap(ledger::isoOf)
                .or(() -> tokenRegistry.currentOf(id).map(serving -> serving.media().isoId()))
                .or(() -> provider.resolveFor(id).flatMap(RhelResolution::ready).map(ready -> ready.media().isoId()));
        tokenRegistry.revoke(id);
        isoId.ifPresent(iso -> treeManager.release(iso, id));
    }
}
