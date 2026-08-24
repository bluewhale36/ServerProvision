package com.example.serverprovision.execution.engine.firmware.step;

import com.example.serverprovision.execution.engine.firmware.AxisResolution;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxis;
import com.example.serverprovision.execution.engine.firmware.FirmwareAxisReason;
import com.example.serverprovision.execution.engine.firmware.FirmwareResolution;
import com.example.serverprovision.execution.engine.firmware.FirmwareUpdateProvider;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2-2 §5 — 집행 상태 기계의 <b>판정</b>만 시험한다. 각 행의 {@code matches} 는 외부 호출 없이
 * {@link FlashContext} 만 보고 답해야 하므로, 여기서는 Redfish 도 저장소도 등장하지 않는다.
 *
 * <p>행 순서가 곧 설계다 — 특히 Task 폴링이 준비도 판정보다 위라는 것과, 준비도 판정이 착수 전
 * 조건과 묶여 있다는 것이 이 슬라이스의 핵심 결정이라 그 우선순위를 직접 고정한다.</p>
 */
class FlashStepMatchingTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 23, 12, 0);

    private final FlashStepRegistry registry = new FlashStepRegistry(List.of(
            new SkipOutOfWindowStep(),
            new PollFlashTaskStep(null, null, null),
            new SkipUnreadyStep(),
            new BeginFlashStep(null, null),
            new FlashAxisStep(null, null, null),
            new PowerOnStep(null, null, null, null),
            new VerifyFlashStep(null, null, null)));

    @Test
    @DisplayName("1행 — 실패한 게스트는 집지 않는다(펌웨어 실패에 자동 재시도는 없다)")
    void failedGuestIsOutOfWindow() {
        ProvisioningProgress progress = started();
        progress.markFailed(T);

        assertThat(matched(context(progress, List.of(), ready()))).isInstanceOf(SkipOutOfWindowStep.class);
    }

    @Test
    @DisplayName("2행 — 굽는 중이면 준비도보다 먼저 Task 를 본다(도중에 자원이 무너져도 놓치지 않는다)")
    void pollingWinsOverReadiness() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BIOS);
        List<ProvisioningHistory> history = List.of(openFlashRow(FirmwareAxis.BIOS, "/redfish/v1/TaskService/Tasks/2"));

        // 판정이 BLOCKED 여도 — 착수한 뒤이므로 3행은 아예 후보가 아니다.
        assertThat(matched(context(progress, history, blocked()))).isInstanceOf(PollFlashTaskStep.class);
    }

    @Test
    @DisplayName("2행 — Task 경로가 없는 열린 행은 폴링 대상이 아니다(추적할 수단이 없다)")
    void openRowWithoutTaskIsNotPolled() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BIOS);
        List<ProvisioningHistory> history = List.of(openFlashRow(FirmwareAxis.BIOS, null));

        assertThat(matched(context(progress, history, ready()))).isNotInstanceOf(PollFlashTaskStep.class);
    }

    @Test
    @DisplayName("3행 — 착수 전 판정이 BLOCKED 면 진입하지 않는다(결손 사다리가 받는다)")
    void blockedBeforeStartSkips() {
        assertThat(matched(context(awaitingBoot(), List.of(), blocked()))).isInstanceOf(SkipUnreadyStep.class);
    }

    @Test
    @DisplayName("3행 — 지원하는 provider 가 없으면 굽기 전에 막는다(D-6)")
    void unsupportedGuestSkips() {
        FlashContext ctx = new FlashContext(server(), awaitingBoot(), detail(), List.of(),
                ready(), null, T);   // provider 없음

        assertThat(matched(ctx)).isInstanceOf(SkipUnreadyStep.class);
    }

    @Test
    @DisplayName("4행 — 착수 전이고 재료가 갖춰졌으면 집행을 시작한다")
    void readyBeforeStartBegins() {
        assertThat(matched(context(awaitingBoot(), List.of(), ready()))).isInstanceOf(BeginFlashStep.class);
    }

    @Test
    @DisplayName("5행 — 손대지 않은 축이 남으면 그 축을 굽는다")
    void untouchedAxisIsFlashed() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BIOS);
        List<ProvisioningHistory> history = List.of(closedRow(FirmwareAxis.BIOS, ProvisioningStatus.SUCCEEDED));

        assertThat(matched(context(progress, history, ready()))).isInstanceOf(FlashAxisStep.class);
    }

    @Test
    @DisplayName("5행 — 건너뛴 축도 '처리됨' 이다(다시 집지 않는다)")
    void skippedAxisCountsAsTouched() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BMC);
        List<ProvisioningHistory> history = List.of(
                closedRow(FirmwareAxis.BIOS, ProvisioningStatus.SUCCEEDED),
                closedRow(FirmwareAxis.BMC, ProvisioningStatus.SKIPPED));

        assertThat(matched(context(progress, history, ready()))).isNotInstanceOf(FlashAxisStep.class);
    }

    @Test
    @DisplayName("6행 — 축이 전부 끝났고 게스트가 아직이면 전원을 넣고 기다린다")
    void allAxesDoneWaitsForReturn() {
        ProvisioningProgress progress = flashing(FirmwareAxis.BMC);
        List<ProvisioningHistory> history = closedBoth();

        assertThat(matched(context(progress, history, ready()))).isInstanceOf(PowerOnStep.class);
    }

    @Test
    @DisplayName("8행 — 게스트가 돌아왔으면 반영을 확인한다(재진입이 곧 POST 를 지났다는 신호)")
    void returnedGuestIsVerified() {
        GuestServer server = server();
        server.touchSeen(T.plusMinutes(3));   // 마지막 축 종결(T+1) 이후의 접촉
        FlashContext ctx = new FlashContext(server, flashing(FirmwareAxis.BMC), detail(),
                closedBoth(), ready(), provider(), T.plusMinutes(4));

        assertThat(matched(ctx)).isInstanceOf(VerifyFlashStep.class);
    }

    @Test
    @DisplayName("registry — 순서가 겹치면 기동에서 막는다(어느 행이 이길지 모르는 상태를 두지 않는다)")
    void duplicateOrderFailsFast() {
        FlashStep first = stubStep(9);
        FlashStep second = stubStep(9);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new FlashStepRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("겹칩니다");
    }

    // ---- 픽스처 --------------------------------------------------------------

    private FlashStep matched(FlashContext ctx) {
        return registry.firstMatching(ctx).orElse(null);
    }

    private FlashContext context(ProvisioningProgress progress, List<ProvisioningHistory> history,
                                 FirmwareResolution resolution) {
        return new FlashContext(server(), progress, detail(), history, resolution, provider(), T);
    }

    private static GuestServer server() {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    }

    private static GuestServerDetail detail() {
        return GuestServerDetail.builder().build();
    }

    /** 운동 양태는 전이 메서드로만 세운다 — 빌더로 무효 상태를 만들지 않는다. */
    private static ProvisioningProgress started() {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID())
                .currentStep(ProvisioningPhaseStep.BIOS_UPDATING)
                .lastTransitionAt(T)
                .build();
        p.start(T);
        return p;
    }

    private static ProvisioningProgress awaitingBoot() {
        return started();
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

    private static FirmwareResolution blocked() {
        return new FirmwareResolution(
                AxisResolution.of(FirmwareAxisReason.SIGNATURE_INVALID),
                AxisResolution.of(FirmwareAxisReason.NO_CANDIDATE));
    }

    private static FirmwareUpdateProvider provider() {
        return new FirmwareUpdateProvider() {
            @Override public boolean supports(GuestServer s, GuestServerDetail d) { return true; }
            @Override public com.example.serverprovision.execution.engine.firmware.BmcIdentity
                    verifyIdentity(com.example.serverprovision.global.redfish.RedfishTarget t, String serial) {
                return com.example.serverprovision.execution.engine.firmware.BmcIdentity.MATCHED;
            }
            @Override public java.util.Optional<String> startFlash(
                    com.example.serverprovision.global.redfish.RedfishTarget t, FirmwareAxis a, String uri) {
                return java.util.Optional.empty();
            }
            @Override public com.example.serverprovision.execution.engine.firmware.FlashTaskState pollTask(
                    com.example.serverprovision.global.redfish.RedfishTarget t, String path) {
                return com.example.serverprovision.execution.engine.firmware.FlashTaskState.RUNNING;
            }
            @Override public java.util.Optional<String> readVersion(
                    com.example.serverprovision.global.redfish.RedfishTarget t, FirmwareAxis a) {
                return java.util.Optional.empty();
            }
        };
    }

    private static ProvisioningHistory openFlashRow(FirmwareAxis axis, String taskPath) {
        return ProvisioningHistory.openRunning(server(), axis.getStep(), T,
                taskPath == null ? null : ProvisioningHistory.flashTargetMeta("F29", 1L, taskPath));
    }

    private static ProvisioningHistory closedRow(FirmwareAxis axis, ProvisioningStatus status) {
        ProvisioningHistory row = ProvisioningHistory.instant(server(), axis.getStep(), status,
                ProvisioningHistory.flashOutcomeMeta("flash-completed", "전송 완료"), T.plusMinutes(1));
        return row;
    }

    private static List<ProvisioningHistory> closedBoth() {
        List<ProvisioningHistory> rows = new ArrayList<>();
        rows.add(closedRow(FirmwareAxis.BIOS, ProvisioningStatus.SUCCEEDED));
        rows.add(closedRow(FirmwareAxis.BMC, ProvisioningStatus.SUCCEEDED));
        return rows;
    }

    private static FlashStep stubStep(int order) {
        return new FlashStep() {
            @Override public int order() { return order; }
            @Override public boolean matches(FlashContext context) { return false; }
            @Override public void execute(FlashContext context) { }
        };
    }
}
