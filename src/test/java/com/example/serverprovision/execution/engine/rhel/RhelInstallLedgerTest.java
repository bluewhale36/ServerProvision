package com.example.serverprovision.execution.engine.rhel;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.engine.osinstall.InstallConfirmation;
import com.example.serverprovision.execution.engine.osinstall.OsInstallFailure;
import com.example.serverprovision.execution.engine.rhel.kickstart.InstallDisk;
import com.example.serverprovision.execution.engine.rhel.kickstart.KickstartDocument;
import com.example.serverprovision.execution.engine.windows.WindowsInstallLedger;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import com.example.serverprovision.execution.installtree.vo.InstallTree;
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
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/** E4-1-b-4-2 — RHEL 원장: 서빙 meta(비밀값 없음) · origin 거름 · 사유 타입이 meta 에 남기는 코드와 화면 문구. */
@ExtendWith(MockitoExtension.class)
class RhelInstallLedgerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 19, 10, 0);

    @Mock ProvisioningHistoryRecorder recorder;
    @Mock ProvisioningHistoryRepository historyRepository;
    private RhelInstallLedger ledger;
    private final GuestServer guest = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();

    @BeforeEach
    void setUp() {
        ledger = new RhelInstallLedger(recorder, historyRepository, new ObjectMapper());
        lenient().when(recorder.openRunning(any(), any(), any(), any())).thenAnswer(inv -> ProvisioningHistory.openRunning(
                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
    }

    static RhelServing serving() {
        return new RhelServing(new KickstartDocument("rootpw --iscrypted $6$secret-hash"),
                new InstallTree(Path.of("/srv/trees/iso-12"), new TreeRelease("Rocky Linux", "9.5"), Path.of("/srv/iso/rocky-9.5.iso")),
                new RhelInstallMedia(12L, OSName.ROCKY_LINUX_9, "9.5", "Rocky Linux 9.5"), new InstallDisk("wwn-0x600508e000000000"));
    }

    private ProvisioningProgress progress() {
        ProvisioningProgress p = ProvisioningProgress.builder().id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.OS_INSTALLING).lastTransitionAt(NOW).build();
        p.start(NOW);
        return p;
    }

    @Test
    @DisplayName("openServed — origin rhel-install · ISO · 등록 OS · 트리 · 서빙 시각 · 디스크를 적고, kickstart 해시 · 토큰은 싣지 않는다")
    void openServed() {
        ProvisioningHistory row = ledger.openServed(guest, serving(), NOW);

        assertThat(row.getStatusMeta()).contains("\"origin\":\"rhel-install\"").doesNotContain("secret-hash").doesNotContain("token");
        assertThat(ledger.owns(row)).isTrue();
        assertThat(ledger.isoOf(row)).contains(12L);
        assertThat(ledger.releaseOf(row)).isEqualTo("Rocky Linux 9.5");
        assertThat(ledger.targetDiskOf(row)).isEqualTo("wwn-0x600508e000000000");
        assertThat(ledger.servedAtOf(row)).isEqualTo(NOW);
    }

    @Test
    @DisplayName("closeSucceeded — 서빙 meta 를 둔 채 완료 meta · 사유 COMPLETED 를 더하고 SUCCEEDED 로 닫는다. 확증은 Boolean 으로 적는다")
    void closeSucceeded_keepsServingMeta() {
        ProvisioningHistory row = ledger.openServed(guest, serving(), NOW);

        boolean closed = ledger.closeSucceeded(row, new RhelInstallLedger.RhelCompletion("rocky", "9.5",
                "Rocky Linux 9.5 (Blue Onyx)", "5.14.0", "localhost", "wwn-0x600508e000000000",
                InstallConfirmation.CONFIRMED, "4c4c4544-0042-3510-8051-b4c04f4e3332", InstallConfirmation.MISMATCH),
                NOW.plusMinutes(30));

        assertThat(closed).isTrue();
        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.SUCCEEDED);
        assertThat(ledger.isCompletedRow(row)).isTrue();
        assertThat(ledger.completedAtOf(row)).isEqualTo(NOW.plusMinutes(30));
        assertThat(ledger.isoOf(row)).contains(12L);
        assertThat(ledger.targetDiskOf(row)).isEqualTo("wwn-0x600508e000000000");
        assertThat(row.getStatusMeta()).contains("\"diskConfirmed\":true", "\"identityConfirmed\":false",
                "\"reportedSystemUuid\":\"4c4c4544-0042-3510-8051-b4c04f4e3332\"");
        assertThat(ledger.completionOf(row).orElseThrow().identityConfirmation()).isEqualTo(InstallConfirmation.MISMATCH);
        assertThat(ledger.closeSucceeded(row, ledger.completionOf(row).orElseThrow(), NOW.plusMinutes(31))).isFalse();
    }

    @Test
    @DisplayName("closeSucceeded — 스크립트가 빈 문자열로 보낸 값은 null 로 적고 읽는다(빈 값 · 미보고 구분 없음)")
    void closeSucceeded_blankReportedValuesBecomeNull() {
        ProvisioningHistory row = ledger.openServed(guest, serving(), NOW);

        ledger.closeSucceeded(row, new RhelInstallLedger.RhelCompletion("", " ", "", "5.14.0", "", "",
                InstallConfirmation.UNREPORTED, "", InstallConfirmation.UNREPORTED), NOW.plusMinutes(30));

        assertThat(row.getStatusMeta()).doesNotContain("\"\"").contains("\"installedRootDisk\":null", "\"reportedSystemUuid\":null");
        RhelInstallLedger.RhelCompletion read = ledger.completionOf(row).orElseThrow();
        assertThat(read.installedRootDisk()).isNull();
        assertThat(read.reportedSystemUuid()).isNull();
        assertThat(read.hostname()).isNull();
        assertThat(read.osVersion()).isNull();
        assertThat(read.kernel()).isEqualTo("5.14.0");
    }

    @Test
    @DisplayName("completionOf — 완료 행이 아니면(열린 행 · 실패 행) empty")
    void completionOf_onlyCompletedRows() {
        ProvisioningHistory row = ledger.openServed(guest, serving(), NOW);
        assertThat(ledger.completionOf(row)).isEmpty();
        ledger.abortRunning(row, OsInstallFailure.OPERATOR, "운영자", NOW.plusMinutes(1));
        assertThat(ledger.completionOf(row)).isEmpty();
        assertThat(ledger.diskConfirmationOf(row)).isNull();
    }

    @Test
    @DisplayName("latestRunning 은 자기 origin 행만 — Windows 원장이 연 행은 집지 않는다")
    void latestRunning_filtersOrigin() {
        WindowsInstallLedger windows = new WindowsInstallLedger(recorder, historyRepository, new ObjectMapper());
        ProvisioningHistory windowsRow = ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, NOW,
                "{\"origin\":\"windows-install\"}");
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeAndStatusOrderByCreatedAtDesc(
                guest.getId(), ProvisioningPhaseStep.OS_INSTALLING, ProvisioningStatus.RUNNING)).willReturn(Optional.of(windowsRow));

        assertThat(ledger.latestRunning(guest.getId())).isEmpty();
        assertThat(windows.latestRunning(guest.getId())).contains(windowsRow);
    }

    @Test
    @DisplayName("failBeforeServing — 단발 실패 행에 사유 코드와 화면 문구(message)를 적고 진행을 실패로 바꾼다")
    void failBeforeServing_writesMessage() {
        ProvisioningProgress progress = progress();
        var reason = new RhelInstallFailure.TreeUnavailable(TreeFailureReason.TOOL_MISSING);

        ledger.failBeforeServing(guest, progress, reason, reason.screenMessage().orElseThrow(), NOW);

        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordInstant(eq(guest), eq(ProvisioningPhaseStep.OS_INSTALLING), eq(ProvisioningStatus.FAILED), meta.capture(), eq(NOW));
        assertThat(meta.getValue()).contains("\"origin\":\"rhel-install\"").contains("\"reason\":\"TREE_TOOL_MISSING\"")
                .contains("\"message\":\"" + TreeFailureReason.TOOL_MISSING.message() + "\"");
        assertThat(progress.isFailed()).isTrue();
    }

    @Test
    @DisplayName("화면 문구가 없는 사유로 닫으면 message 를 적지 않고, 행에 남아 있던 옛 message 는 지운다")
    void abort_withoutMessage_removesOldMessage() {
        ProvisioningHistory row = ProvisioningHistory.openRunning(guest, ProvisioningPhaseStep.OS_INSTALLING, NOW,
                "{\"origin\":\"rhel-install\",\"served\":\"" + NOW + "\",\"message\":\"옛 문구\"}");

        assertThat(ledger.abortRunning(row, OsInstallFailure.OPERATOR, "운영자 수동 실패 전환", NOW.plusMinutes(5))).isTrue();

        assertThat(row.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(ledger.reasonOf(row)).isEqualTo("OPERATOR");
        assertThat(ledger.failureMessageOf(row)).isEmpty();
        assertThat(ledger.servedAtOf(row)).isEqualTo(NOW);   // 서빙 meta 는 보존
    }

    @Test
    @DisplayName("버전 불일치로 닫힌 행은 화면 문구를 돌려준다")
    void failureMessageOf_mismatch() {
        ProvisioningHistory row = ledger.openServed(guest, serving(), NOW);
        var mismatch = new RhelInstallFailure.ReleaseMismatch("Rocky Linux 10.0", "Rocky Linux 9.5");

        ledger.failRunning(guest, progress(), row, mismatch, "d", NOW);

        assertThat(ledger.reasonOf(row)).isEqualTo("TREE_RELEASE_MISMATCH");
        assertThat(ledger.failureMessageOf(row)).isEqualTo(mismatch.screenMessage());
    }
}
