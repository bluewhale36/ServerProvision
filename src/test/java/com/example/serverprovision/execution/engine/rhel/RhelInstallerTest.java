package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.phase.PhaseReadiness;
import com.example.serverprovision.execution.engine.phase.ReadinessGrade;
import com.example.serverprovision.execution.engine.rhel.kickstart.InstallDisk;
import com.example.serverprovision.execution.engine.rhel.kickstart.KickstartFixtures;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningMotion;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import com.example.serverprovision.execution.installtree.service.InstallTreeManager;
import com.example.serverprovision.execution.installtree.vo.InstallTree;
import com.example.serverprovision.execution.installtree.vo.TreeAcquisition;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E4-1-b-4-2 — RHEL 설치기: 순서표 5 ~ 8 행(준비 중 대기 · 확보 실패 · 버전 불일치 · 서빙), 착수 판정(막 발급한 토큰),
 * 재진입 · 스윕 · 운영자 실패 때 토큰 회수와 트리 반납, 설치 중일 때만 kickstart.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RhelInstallerTest {

    private static final String REENTRY = "http://10.0.0.7:8080/api/pxe/v1/boot?mac=aa";
    private static final long ISO = 12L;
    private static final RhelInstallMedia MEDIA = new RhelInstallMedia(ISO, OSName.ROCKY_LINUX_9, "9.5", "Rocky Linux 9.5");
    private static final InstallDisk DISK = new InstallDisk("wwn-0x600508e000000000");

    @Mock RhelInstallReadinessResolver resolver;
    @Mock RhelInstallationResolutionProvider provider;
    @Mock InstallTreeManager treeManager;
    @Mock ProvisioningHistoryRecorder recorder;
    @Mock ProvisioningHistoryRepository historyRepository;

    private final RhelInstallTokenRegistry tokenRegistry = new RhelInstallTokenRegistry("http://10.0.0.7:8080");
    private final GuestServer guest = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    private RhelInstallLedger ledger;
    private RhelInstaller installer;
    private ProvisioningHistory openRow;

    @BeforeEach
    void setUp() {
        ledger = new RhelInstallLedger(recorder, historyRepository, new ObjectMapper());
        installer = new RhelInstaller(resolver, provider, treeManager, ledger,
                new RhelInstallTimeoutPolicy(Duration.ofMinutes(60), 5, Duration.ofMinutes(30)), tokenRegistry);
        given(recorder.openRunning(any(), any(), any(), any())).willAnswer(inv -> {
            openRow = ProvisioningHistory.openRunning(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3));
            return openRow;
        });
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                eq(guest.getId()), eq(ProvisioningPhaseStep.OS_INSTALLING), eq(ProvisioningStatus.RUNNING)))
                .willAnswer(inv -> Optional.ofNullable(openRow).filter(r -> r.getStatus() == ProvisioningStatus.RUNNING));
        given(provider.resolveFor(guest.getId())).willReturn(Optional.of(new RhelResolution.Ready(KickstartFixtures.rocky9(), MEDIA)));
    }

    private static ProvisioningProgress awaitingBoot() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(LocalDateTime.now()).build();
        p.start(LocalDateTime.now());
        return p;
    }

    private void resolves(PhaseReadiness readiness, RhelDiskSelection.Selection disk) {
        given(resolver.resolve(guest.getId())).willReturn(Optional.of(new RhelInstallReadinessResolver.Resolved(
                new RhelResolution.Ready(KickstartFixtures.rocky9(), MEDIA), readiness, disk)));
    }

    private void readyToServe(String treeVersion) {
        resolves(PhaseReadiness.ready(), new RhelDiskSelection.Selection.Confident(DISK));
        given(treeManager.acquire(ISO, guest.getId())).willReturn(TreeAcquisition.ready(
                new InstallTree(Path.of("/srv/trees/iso-12"), new TreeRelease("Rocky Linux", treeVersion))));
    }

    /** 서빙 → 착수 기록까지 마친 게스트(열린 행 + STEP_RUNNING). */
    private ProvisioningProgress served(LocalDateTime servedAt) {
        readyToServe("9.5");
        ProvisioningProgress progress = awaitingBoot();
        installer.bootScript(guest, progress, REENTRY);
        installer.onBootScriptServed(guest, progress, servedAt);
        return progress;
    }

    @Test
    @DisplayName("준비도가 막히면 그 wire 로 대기 · 확보는 하지 않는다")
    void blocked_holds() {
        resolves(PhaseReadiness.of(ReadinessGrade.BLOCKED, List.of("n"), "pxe base url missing"), new RhelDiskSelection.Selection.Confident(DISK));
        assertThat(installer.bootScript(guest, awaitingBoot(), REENTRY)).contains("waiting for resources: pxe base url missing");
        verify(treeManager, never()).acquire(anyLong(), any());
    }

    @Test
    @DisplayName("디스크가 RAID 구성 뒤면(미룸) 서빙하지 않고 기다린다 · 할당이 사라졌으면 target missing")
    void deferredDiskOrMissingTarget_holds() {
        resolves(PhaseReadiness.ready(), new RhelDiskSelection.Selection.Deferred("RAID 구성 뒤 정해집니다"));
        assertThat(installer.bootScript(guest, awaitingBoot(), REENTRY)).contains(RhelInstaller.DISK_NOT_CONFIDENT_WIRE);

        given(resolver.resolve(guest.getId())).willReturn(Optional.empty());
        assertThat(installer.bootScript(guest, awaitingBoot(), REENTRY)).contains(RhelInstaller.TARGET_MISSING_WIRE);
    }

    @Test
    @DisplayName("트리를 푸는 중이면 준비 중 대기 · 토큰은 없고 착수도 아니다")
    void preparing_waits() {
        resolves(PhaseReadiness.ready(), new RhelDiskSelection.Selection.Confident(DISK));
        given(treeManager.acquire(ISO, guest.getId())).willReturn(TreeAcquisition.preparing());
        ProvisioningProgress progress = awaitingBoot();

        assertThat(installer.bootScript(guest, progress, REENTRY)).contains("rhel install tree preparing");
        installer.onBootScriptServed(guest, progress, LocalDateTime.now());

        assertThat(tokenRegistry.currentOf(guest.getId())).isEmpty();
        verify(recorder, never()).openRunning(any(), any(), any(), any());
    }

    @Test
    @DisplayName("확보 실패 — TREE_<사유> 로 단발 실패 행 · 진행 실패 · 실패 안내 스크립트")
    void acquireFailed_failsStep() {
        resolves(PhaseReadiness.ready(), new RhelDiskSelection.Selection.Confident(DISK));
        given(treeManager.acquire(ISO, guest.getId())).willReturn(TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED));
        ProvisioningProgress progress = awaitingBoot();

        String script = installer.bootScript(guest, progress, REENTRY);

        assertThat(script).contains("rhel install FAILED (TREE_EXTRACTION_FAILED)");
        assertThat(progress.isFailed()).isTrue();
        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordInstant(eq(guest), eq(ProvisioningPhaseStep.OS_INSTALLING), eq(ProvisioningStatus.FAILED), meta.capture(), any());
        assertThat(meta.getValue()).contains("TREE_EXTRACTION_FAILED").contains(TreeFailureReason.EXTRACTION_FAILED.message());
    }

    @Test
    @DisplayName("트리 주 버전이 정의서와 다르면 트리를 반납하고 TREE_RELEASE_MISMATCH 로 실패한다")
    void releaseMismatch_failsAndReleases() {
        readyToServe("10.0");
        ProvisioningProgress progress = awaitingBoot();

        assertThat(installer.bootScript(guest, progress, REENTRY)).contains("rhel install FAILED (TREE_RELEASE_MISMATCH)");
        verify(treeManager).release(ISO, guest.getId());
        assertThat(progress.isFailed()).isTrue();
        assertThat(tokenRegistry.currentOf(guest.getId())).isEmpty();
    }

    @Test
    @DisplayName("서빙 — 토큰을 발급하고 kickstart 를 한 번 렌더해 둔다(설치 원본 = 토큰 트리 URL · 대상 디스크) · 체인은 그 토큰 경로")
    void serve_issuesTokenAndChain() {
        readyToServe("9.5");

        String script = installer.bootScript(guest, awaitingBoot(), REENTRY);

        RhelServing serving = tokenRegistry.currentOf(guest.getId()).orElseThrow();
        String treeUrl = serving.kickstart().text().lines().filter(l -> l.startsWith("url --url=")).findFirst().orElseThrow()
                .substring("url --url=".length());
        assertThat(treeUrl).startsWith("http://10.0.0.7:8080/api/pxe/v1/rhel/").endsWith("/tree");
        assertThat(serving.kickstart().text()).contains("ignoredisk --only-use=disk/by-id/wwn-0x600508e000000000");
        assertThat(script).contains("inst.repo=" + treeUrl).contains("inst.ks=" + treeUrl.replace("/tree", "/ks.cfg"));
    }

    @Test
    @DisplayName("착수 — 막 발급한 토큰이 있으면 커서 STEP_RUNNING + 원장 RUNNING 행 · 토큰이 없으면 기록하지 않는다")
    void onServed_startsOnlyWithFreshToken() {
        ProvisioningProgress progress = served(LocalDateTime.now());

        assertThat(progress.getMotion()).isEqualTo(ProvisioningMotion.STEP_RUNNING);
        assertThat(openRow).isNotNull();
        assertThat(ledger.isoOf(openRow)).contains(ISO);
        assertThat(ledger.targetDiskOf(openRow)).isEqualTo(DISK.byIdName());
    }

    @Test
    @DisplayName("설치 중 재진입 — 로컬 부팅 폴스루 · 재진입 기록 / 시한이 지나면 실패 · 토큰 회수 · 트리 반납")
    void reentry_fallthroughThenTimeout() {
        ProvisioningProgress progress = served(LocalDateTime.now());
        assertThat(installer.bootScript(guest, progress, REENTRY)).contains("os setup in progress (reentry 1/5)");
        installer.onBootScriptServed(guest, progress, LocalDateTime.now());
        assertThat(ledger.reentriesOf(openRow)).isEqualTo(1);

        ProvisioningProgress late = served(LocalDateTime.now().minusMinutes(61));
        assertThat(installer.bootScript(guest, late, REENTRY)).contains("rhel install FAILED (INSTALL_TIMEOUT)");
        assertThat(late.isFailed()).isTrue();
        assertThat(tokenRegistry.currentOf(guest.getId())).isEmpty();
        verify(treeManager).release(ISO, guest.getId());
    }

    @Test
    @DisplayName("스윕 — 서빙 + 시한 + 유예가 지나면 INSTALL_TIMEOUT 으로 닫고 토큰 회수 · 트리 반납")
    void sweep_closesAndReleases() {
        ProvisioningProgress progress = served(LocalDateTime.now().minusMinutes(100));

        assertThat(installer.sweep(guest, progress, openRow, LocalDateTime.now())).isTrue();
        assertThat(ledger.reasonOf(openRow)).isEqualTo("INSTALL_TIMEOUT");
        assertThat(tokenRegistry.currentOf(guest.getId())).isEmpty();
        verify(treeManager).release(ISO, guest.getId());
    }

    @Test
    @DisplayName("운영자 실패 — 열린 행을 OPERATOR 로 닫고 반납 · 행도 토큰도 없으면(트리 준비 중) 정의서의 ISO 로 반납한다")
    void operatorFailed_releases() {
        ProvisioningProgress progress = served(LocalDateTime.now());
        installer.onOperatorFailed(guest, progress, LocalDateTime.now());
        assertThat(ledger.reasonOf(openRow)).isEqualTo("OPERATOR");

        openRow = null;
        installer.onOperatorFailed(guest, awaitingBoot(), LocalDateTime.now());
        verify(treeManager, org.mockito.Mockito.times(2)).release(ISO, guest.getId());
    }

    @Test
    @DisplayName("kickstart 는 그 토큰의 게스트가 설치 중일 때만 — 실패로 닫힌 뒤에는 내지 않는다")
    void kickstartOnlyWhileInstalling() {
        ProvisioningProgress progress = served(LocalDateTime.now());
        UUID token = tokenOf();

        assertThat(installer.kickstartWhileInstalling(token)).isPresent();
        assertThat(installer.kickstartWhileInstalling(UUID.randomUUID())).isEmpty();   // 발급하지 않은 토큰
        ledger.abortRunning(openRow, com.example.serverprovision.execution.engine.osinstall.OsInstallFailure.OPERATOR, "x", LocalDateTime.now());
        assertThat(installer.kickstartWhileInstalling(token)).isEmpty();
    }

    @Test
    @DisplayName("새 서빙이 다른 ISO 의 옛 열린 행을 대체하면 옛 ISO 트리만 반납한다")
    void supersede_releasesStaleIsoOnly() {
        openRow = ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, LocalDateTime.now(),
                "{\"origin\":\"rhel-install\",\"iso\":7,\"served\":\"" + LocalDateTime.now() + "\"}");
        readyToServe("9.5");
        ProvisioningProgress progress = awaitingBoot();
        installer.bootScript(guest, progress, REENTRY);
        ProvisioningHistory stale = openRow;

        installer.onBootScriptServed(guest, progress, LocalDateTime.now());

        assertThat(ledger.reasonOf(stale)).isEqualTo("SUPERSEDED");
        verify(treeManager).release(7L, guest.getId());
        verify(treeManager, never()).release(ISO, guest.getId());
    }

    private UUID tokenOf() {
        String url = tokenRegistry.currentOf(guest.getId()).orElseThrow().kickstart().text().lines()
                .filter(l -> l.startsWith("url --url=")).findFirst().orElseThrow();
        String[] parts = url.split("/");
        return UUID.fromString(parts[parts.length - 2]);
    }
}
