package com.example.serverprovision.execution.pxeinfra.apply;

import com.example.serverprovision.execution.pxeinfra.command.AllowedCommand;
import com.example.serverprovision.execution.pxeinfra.command.CommandResult;
import com.example.serverprovision.execution.pxeinfra.command.StubSystemCommandRunner;
import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.entity.PxeNetworkConfig;
import com.example.serverprovision.execution.pxeinfra.inspect.SystemServiceInspector;
import com.example.serverprovision.execution.pxeinfra.render.DhcpdConfigRenderer;
import com.example.serverprovision.execution.pxeinfra.spi.ServiceState;
import com.example.serverprovision.global.asset.AtomicAssetSwap;
import com.example.serverprovision.global.asset.FakeAssetHistoryService;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.example.serverprovision.execution.pxeinfra.PxeNetworkConfigFixtures.full;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * E1-I-3-c — dhcpd 조각 적용 상태기계(§7)의 전 경로 검증. 실제 프로세스를 spawn 하지 않는 스텁 러너와, 실 바이트를
 * 보관하는 이력 페이크(복원 정합 확인용) 위에서 APPLIED·REJECTED·게이트 실행불능·ROLLED_BACK·RESTORE_FAILED·
 * 최초적용 실패(빈 조각)를 각각 유발한다. 파이프라인은 명령 실패를 예외가 아닌 {@link ApplyOutcome} 으로만
 * 귀결해야 하며(archive IO·미구성만 예외), 실패 경로에서는 재기동을 함부로 부르지 않고 이전 조각을 되살린다.
 */
class DhcpdConfigApplyServiceTest {

    @TempDir
    Path work;    // dhcpd 조각과 temp 가 함께 사는 디렉토리
    @TempDir
    Path store;   // 이력 store

    private Path fragmentPath;
    private StubSystemCommandRunner runner;
    private SystemServiceInspector inspector;
    private DhcpdConfigRenderer renderer;
    private FakeAssetHistoryService history;
    private DhcpdConfigApplyService service;

    private final PxeNetworkConfig desired = full();

    @BeforeEach
    void setUp() {
        fragmentPath = work.resolve("pxe-fragment.conf");
        PxeInfraProperties props = new PxeInfraProperties(
                fragmentPath.toString(),
                work.resolve("dhcpd.conf").toString(),
                work.resolve("dhcpd.leases").toString());

        @SuppressWarnings("unchecked")
        ObjectProvider<PxeInfraProperties> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(props);

        runner = new StubSystemCommandRunner();
        inspector = mock(SystemServiceInspector.class);
        renderer = new DhcpdConfigRenderer();
        FileSystemHardener hardener = new FileSystemHardener(mock(FileSystemSecurityProperties.class));
        // 실패 복원 시 방금 만든 archive 제거(중복 축적 방지) 검증을 위해 스왑 부품과 apply 서비스가 같은 이력 페이크를 공유한다.
        history = new FakeAssetHistoryService(store);
        AtomicAssetSwap swap = new AtomicAssetSwap(history, hardener);

        service = new DhcpdConfigApplyService(runner, inspector, swap, renderer, provider, history, hardener);
    }

    // ── APPLIED ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("APPLIED — 게이트 통과·재기동 0·active → 새 조각 서빙 + 이전본 archive")
    void applied_existingFragment() throws IOException {
        seedFragment("OLD-FRAGMENT");
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.completed(0, "", ""))
                .stub(AllowedCommand.DHCPD_SERVICE_RESTART, CommandResult.completed(0, "", ""));
        given(inspector.status()).willReturn(ServiceState.ACTIVE);

        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.APPLIED);
        assertThat(outcome.appliedVersionId()).isNotNull();                 // 이전본이 archive 됨
        assertThat(Files.readString(fragmentPath)).isEqualTo(renderer.render(desired));
        assertThat(noTempLeftover()).isTrue();
    }

    @Test
    @DisplayName("APPLIED — 최초 적용(이전본 없음)이면 archivedVersionId 는 null")
    void applied_firstApply_noPreviousVersion() {
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.completed(0, "", ""))
                .stub(AllowedCommand.DHCPD_SERVICE_RESTART, CommandResult.completed(0, "", ""));
        given(inspector.status()).willReturn(ServiceState.ACTIVE);

        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.APPLIED);
        assertThat(outcome.appliedVersionId()).isNull();
    }

    // ── REJECTED (실패경로 ①) ─────────────────────────────────────────────────

    @Test
    @DisplayName("REJECTED — dhcpd -t 거절 → 이전 조각 복원, 재기동 미실행, 게이트 원문 보존")
    void rejected_gateSyntaxError() throws IOException {
        seedFragment("OLD-FRAGMENT");
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK,
                CommandResult.completed(1, "dhcpd.conf line 3: syntax error", " near ';'"));

        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.REJECTED);
        assertThat(outcome.gateOutput()).isEqualTo("dhcpd.conf line 3: syntax error\nnear ';'");  // stdout·stderr 개행 결합 원문
        assertThat(Files.readString(fragmentPath)).isEqualTo("OLD-FRAGMENT");                     // 복원
        assertThat(restartInvoked()).isFalse();                                                    // 재기동 안 함
    }

    @Test
    @DisplayName("REJECTED — 실패한 스왑이 만든 archive 를 제거(연속 실패가 이력을 부풀리지 않음)")
    void rejected_discardsRedundantArchive() throws IOException {
        seedFragment("PREVIOUS-FRAGMENT");
        // 1회 정상 적용 → 이전본(PREVIOUS-FRAGMENT) 1건 archive.
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.completed(0, "", ""))
                .stub(AllowedCommand.DHCPD_SERVICE_RESTART, CommandResult.completed(0, "", ""));
        given(inspector.status()).willReturn(ServiceState.ACTIVE);
        service.apply(desired);
        assertThat(history.archiveCount()).isEqualTo(1);

        // 이후 게이트 거절 적용 → 스왑이 현재본을 archive 했다가 복원하며 그 archive 를 제거 → 이력 그대로 1(2 로 안 늘어남).
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.completed(1, "syntax error", ""));
        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.REJECTED);
        assertThat(history.archiveCount()).isEqualTo(1);
    }

    // ── 게이트 실행불능 (실패경로 ②) ──────────────────────────────────────────

    @Test
    @DisplayName("게이트 실행불능(NOT_FOUND) → ROLLED_BACK(500), 이전 조각 복원, 재기동 미실행")
    void gateUnexecutable_notFound_rollsBack() throws IOException {
        seedFragment("OLD-FRAGMENT");
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.notFound());

        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.ROLLED_BACK);
        assertThat(outcome.detail()).contains("NOT_FOUND");
        assertThat(Files.readString(fragmentPath)).isEqualTo("OLD-FRAGMENT");
        assertThat(restartInvoked()).isFalse();
    }

    @Test
    @DisplayName("게이트 실행불능(TIMED_OUT) → ROLLED_BACK(500)")
    void gateUnexecutable_timedOut_rollsBack() throws IOException {
        seedFragment("OLD-FRAGMENT");
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.timedOut());

        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.ROLLED_BACK);
        assertThat(outcome.detail()).contains("TIMED_OUT");
        assertThat(Files.readString(fragmentPath)).isEqualTo("OLD-FRAGMENT");
    }

    // ── ROLLED_BACK (실패경로 ③) ──────────────────────────────────────────────

    @Test
    @DisplayName("ROLLED_BACK — 재기동 실패 → 복원 후 재기동·재검증 active")
    void rolledBack_restartFails_restoreActive() throws IOException {
        seedFragment("OLD-FRAGMENT");
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.completed(0, "", ""))
                .stub(AllowedCommand.DHCPD_SERVICE_RESTART, CommandResult.completed(1, "", "restart failed"));
        given(inspector.status()).willReturn(ServiceState.ACTIVE);   // 복원 후 이전 구성으로 살아남

        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.ROLLED_BACK);
        assertThat(Files.readString(fragmentPath)).isEqualTo("OLD-FRAGMENT");   // 이전 조각 복원
    }

    @Test
    @DisplayName("ROLLED_BACK — 재기동은 0 이나 검증 inactive → 복원 후 재검증 active")
    void rolledBack_verifyInactiveThenRestoreActive() throws IOException {
        seedFragment("OLD-FRAGMENT");
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.completed(0, "", ""))
                .stub(AllowedCommand.DHCPD_SERVICE_RESTART, CommandResult.completed(0, "", ""));
        given(inspector.status()).willReturn(ServiceState.INACTIVE, ServiceState.ACTIVE);

        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.ROLLED_BACK);
        assertThat(Files.readString(fragmentPath)).isEqualTo("OLD-FRAGMENT");
    }

    // ── RESTORE_FAILED ────────────────────────────────────────────────────────

    @Test
    @DisplayName("RESTORE_FAILED — 재기동 실패 + 복원 후 재검증도 inactive → 최악(500, 수동복구)")
    void restoreFailed_restartAndRestoreBothDead() throws IOException {
        seedFragment("OLD-FRAGMENT");
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.completed(0, "", ""))
                .stub(AllowedCommand.DHCPD_SERVICE_RESTART, CommandResult.completed(1, "", "dead"));
        given(inspector.status()).willReturn(ServiceState.INACTIVE);

        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.RESTORE_FAILED);
        assertThat(outcome.detail()).contains("systemctl restart dhcpd");   // 수동 복구 안내
    }

    // ── 최초 적용 게이트 실패 → 빈 조각(삭제 아님) ─────────────────────────────

    @Test
    @DisplayName("최초 적용 게이트 실패 — 이전본이 없으니 삭제가 아닌 유효한 빈 조각으로 복원(D7)")
    void firstApplyGateFailure_writesEmptyFragment() throws IOException {
        // 이전 조각을 seed 하지 않음 → 복원 소스 없음.
        runner.stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.completed(1, "invalid", ""));

        ApplyOutcome outcome = service.apply(desired);

        assertThat(outcome.result()).isEqualTo(ApplyResult.REJECTED);
        assertThat(outcome.gateOutput()).isEqualTo("invalid");
        assertThat(Files.exists(fragmentPath)).isTrue();                         // 삭제 아님
        assertThat(Files.readString(fragmentPath)).isEqualTo(renderer.renderEmpty());   // 유효한 빈 조각
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private void seedFragment(String content) throws IOException {
        Files.writeString(fragmentPath, content);
    }

    private boolean restartInvoked() {
        return runner.invocations().stream()
                .anyMatch(i -> i.command() == AllowedCommand.DHCPD_SERVICE_RESTART);
    }

    private boolean noTempLeftover() throws IOException {
        try (var entries = Files.list(work)) {
            return entries.noneMatch(p -> p.getFileName().toString().endsWith(".tmp"));
        }
    }
}
