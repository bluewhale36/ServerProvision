package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.provisioning.assignment.dto.response.PlannedPhaseRailResponse.RunState;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * {@link PlannedPhaseRailResponse#of} 순수 겹침 규칙 — 계획(assigned)에 진행 커서와 원장 행을 겹쳐 단계 패널 목록을
 * 만든다(DB 불요). S21-1 부터 rail 은 암시 단계 2(부트스트래핑 · 진단 리눅스) + 계획 phase + 원장 phase 의 합집합이다.
 */
class PlannedPhaseRailResponseTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 9, 13, 9, 21, 0);

    private static AssignmentPlanResponse plan() {
        return new AssignmentPlanResponse(true, "web-standard", AssignmentState.ACTIVE_CONSUMED,
                "이미 개시되어 재할당할 수 없습니다(회수 후 재등록 필요)",
                null,   // U3-5-a — 하드웨어 대조 통과(경고 없음)
                List.of(ProvisioningPhase.DIAGNOSE_LINUX,
                        ProvisioningPhase.FIRMWARE_UPDATING,
                        ProvisioningPhase.OS_INSTALLING));
    }

    private static GuestServerDetailResponse.Progress progress(ProvisioningPhase cursor, LocalDateTime failedAt,
                                                               LocalDateTime completedAt) {
        return new GuestServerDetailResponse.Progress(cursor, T0, T0, failedAt, null, completedAt,
                false, false, false, false, false);
    }

    private static GuestServerDetailResponse.Step step(ProvisioningPhase phase, ProvisioningPhaseStep step,
                                                       ProvisioningStatus status, LocalDateTime start, LocalDateTime end) {
        return new GuestServerDetailResponse.Step(phase, step, status, start, end, null);
    }

    @Test
    @DisplayName("미할당 → 암시 단계 둘(부트스트래핑 · 진단 리눅스)만 선다 — 할당 없이도 밟는 단계")
    void unassigned_implicitPhasesOnly() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                AssignmentPlanResponse.unassigned(), progress(ProvisioningPhase.DIAGNOSE_LINUX, null, null), List.of());

        assertThat(rail.assigned()).isFalse();
        assertThat(rail.entries()).extracting(PlannedPhaseRailResponse.Entry::phase, PlannedPhaseRailResponse.Entry::runState)
                .containsExactly(
                        tuple(ProvisioningPhase.BOOTSTRAPPING, RunState.DONE),
                        tuple(ProvisioningPhase.DIAGNOSE_LINUX, RunState.CURRENT));
    }

    @Test
    @DisplayName("진행 없음(등록 직후) → 전부 PENDING")
    void noProgress_allPending() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(plan(), null, List.of());

        assertThat(rail.entries()).extracting(PlannedPhaseRailResponse.Entry::runState)
                .containsOnly(RunState.PENDING);
        assertThat(rail.entries()).hasSize(4);   // 암시 2 ∪ 계획 3 (진단 리눅스 겹침)
    }

    @Test
    @DisplayName("of — reassignBlockReason · state 를 계획에서 그대로 전달(뷰 disabled 판정 SSOT)")
    void of_propagatesReassignBlockReason() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                plan(), progress(ProvisioningPhase.FIRMWARE_UPDATING, null, null), List.of());

        assertThat(rail.state()).isEqualTo(AssignmentState.ACTIVE_CONSUMED);
        assertThat(rail.reassignBlockReason()).isEqualTo("이미 개시되어 재할당할 수 없습니다(회수 후 재등록 필요)");
        assertThat(rail.badgeClass()).isEqualTo("n-badge-green");
    }

    @Test
    @DisplayName("커서=FIRMWARE_UPDATING → 앞 DONE · 커서 CURRENT · 뒤 PENDING (선언 순)")
    void cursor_overlaysDoneCurrentPending() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                plan(), progress(ProvisioningPhase.FIRMWARE_UPDATING, null, null), List.of());

        assertThat(rail.entries()).extracting(PlannedPhaseRailResponse.Entry::phase, PlannedPhaseRailResponse.Entry::runState)
                .containsExactly(
                        tuple(ProvisioningPhase.BOOTSTRAPPING, RunState.DONE),
                        tuple(ProvisioningPhase.DIAGNOSE_LINUX, RunState.DONE),
                        tuple(ProvisioningPhase.FIRMWARE_UPDATING, RunState.CURRENT),
                        tuple(ProvisioningPhase.OS_INSTALLING, RunState.PENDING));
    }

    @Test
    @DisplayName("실패 신호가 켜진 커서 단계는 FAILED — 앞은 DONE 그대로")
    void failed_marksCursorFailed() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                plan(), progress(ProvisioningPhase.FIRMWARE_UPDATING, T0.plusHours(1), null), List.of());

        assertThat(rail.entries()).extracting(PlannedPhaseRailResponse.Entry::runState)
                .containsExactly(RunState.DONE, RunState.DONE, RunState.FAILED, RunState.PENDING);
    }

    @Test
    @DisplayName("종단(completedAt) → 커서와 무관하게 전부 DONE")
    void completed_allDone() {
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                plan(), progress(ProvisioningPhase.OS_INSTALLING, null, T0.plusHours(3)), List.of());

        assertThat(rail.entries()).extracting(PlannedPhaseRailResponse.Entry::runState).containsOnly(RunState.DONE);
    }

    @Test
    @DisplayName("원장 행은 자기 단계에 실리고 시작 · 종료 시각을 만든다 — 열린 행은 openStep, 완료 단계만 접힘")
    void steps_groupedIntoEntries() {
        List<GuestServerDetailResponse.Step> steps = List.of(
                step(ProvisioningPhase.BOOTSTRAPPING, ProvisioningPhaseStep.NETWORK_ALLOCATING, ProvisioningStatus.SUCCEEDED, T0, T0.plusMinutes(1)),
                step(ProvisioningPhase.BOOTSTRAPPING, ProvisioningPhaseStep.INIT_PERSISTING, ProvisioningStatus.SUCCEEDED, T0.plusMinutes(1), T0.plusMinutes(2)),
                step(ProvisioningPhase.FIRMWARE_UPDATING, ProvisioningPhaseStep.BIOS_UPDATING, ProvisioningStatus.SUCCEEDED, T0.plusMinutes(10), T0.plusMinutes(17)),
                step(ProvisioningPhase.FIRMWARE_UPDATING, ProvisioningPhaseStep.BMC_UPDATING, ProvisioningStatus.RUNNING, T0.plusMinutes(17), null));
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                plan(), progress(ProvisioningPhase.FIRMWARE_UPDATING, null, null), steps);

        PlannedPhaseRailResponse.Entry boot = rail.entries().get(0);
        assertThat(boot.steps()).hasSize(2);
        assertThat(boot.startedAt()).isEqualTo(T0);
        assertThat(boot.finishedAt()).isEqualTo(T0.plusMinutes(2));
        assertThat(boot.openStep()).isNull();
        assertThat(boot.expanded()).isFalse();   // 완료 = 접힘

        PlannedPhaseRailResponse.Entry firmware = rail.entries().get(2);
        assertThat(firmware.steps()).hasSize(2);
        assertThat(firmware.finishedAt()).isNull();   // 마지막 행이 열려 있다
        assertThat(firmware.openStep().step()).isEqualTo(ProvisioningPhaseStep.BMC_UPDATING);
        assertThat(firmware.expanded()).isTrue();

        assertThat(rail.entries().get(3).expanded()).isTrue();   // 예정도 펼침
    }

    @Test
    @DisplayName("회수된 서버(CP5 F-1) — 커서와 그 뒤는 STOPPED, 앞은 DONE · 밟은 기록이 있는 STOPPED 만 펼침")
    void decommissioned_stopsAtCursor() {
        List<GuestServerDetailResponse.Step> steps = List.of(
                step(ProvisioningPhase.BOOTSTRAPPING, ProvisioningPhaseStep.NETWORK_ALLOCATING, ProvisioningStatus.SUCCEEDED, T0, T0.plusMinutes(1)),
                step(ProvisioningPhase.DIAGNOSE_LINUX, ProvisioningPhaseStep.DIAGNOSTIC_BOOTING, ProvisioningStatus.SUCCEEDED, T0.plusMinutes(1), T0.plusMinutes(3)));
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.ofDecommissioned(
                plan(), progress(ProvisioningPhase.DIAGNOSE_LINUX, null, null), steps);

        assertThat(rail.entries()).extracting(PlannedPhaseRailResponse.Entry::runState)
                .containsExactly(RunState.DONE, RunState.STOPPED, RunState.STOPPED, RunState.STOPPED);
        assertThat(rail.entries().get(1).expanded()).isTrue();    // 진단 리눅스 — 밟은 기록이 있다
        assertThat(rail.entries().get(2).expanded()).isFalse();   // 펌웨어 업데이트 — 밟지 않은 단계는 접힘
    }

    @Test
    @DisplayName("of(plan, server) — 단계 데이터(펌웨어 · RAID · Windows)가 실려 온 phase 는 계획에 없어도 선다(보일 자리가 그 패널뿐)")
    void ofServer_addsPhasesThatCarryData() {
        var windows = new GuestServerDetailResponse.WindowsInstall("WS2025", null,
                com.example.serverprovision.execution.engine.phase.ReadinessGrade.READY, List.of(),
                null, 0, 5, null, null, false, 0, null, null, null, 0, 0, List.of(), false, null, null, null, null,
                null, List.of(), List.of(), List.of());   // R15-2 — 드라이버 4 필드
        var raidPlan = new GuestServerDetailResponse.RaidPlanPreview(false, List.of(), null);
        var server = new GuestServerDetailResponse(
                java.util.UUID.randomUUID(), "web-01", null, null, java.util.UUID.randomUUID(), "aabbcc", null, null,
                com.example.serverprovision.execution.enums.GuestServerStatus.REGISTERED, null, T0, T0,
                null, null, List.of(), progress(ProvisioningPhase.DIAGNOSE_LINUX, null, null),
                null, null, null, windows, raidPlan, List.of(), List.of());

        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(AssignmentPlanResponse.unassigned(), server);

        assertThat(rail.entries()).extracting(PlannedPhaseRailResponse.Entry::phase)
                .containsExactly(ProvisioningPhase.BOOTSTRAPPING, ProvisioningPhase.DIAGNOSE_LINUX,
                        ProvisioningPhase.RAID_CONFIGURATION, ProvisioningPhase.OS_INSTALLING);
    }

    @Test
    @DisplayName("계획에 없는 단계라도 원장 행이 있으면 rail 에 선다 — 할당이 끝나도 한 일이 사라지지 않는다")
    void ledgerPhase_joinsRail() {
        List<GuestServerDetailResponse.Step> steps = List.of(
                step(ProvisioningPhase.RAID_CONFIGURATION, ProvisioningPhaseStep.RAID_APPLYING, ProvisioningStatus.SUCCEEDED, T0, T0.plusMinutes(2)));
        PlannedPhaseRailResponse rail = PlannedPhaseRailResponse.of(
                AssignmentPlanResponse.unassigned(), progress(ProvisioningPhase.OS_INSTALLING, null, T0.plusHours(1)), steps);

        assertThat(rail.entries()).extracting(PlannedPhaseRailResponse.Entry::phase)
                .containsExactly(ProvisioningPhase.BOOTSTRAPPING, ProvisioningPhase.DIAGNOSE_LINUX, ProvisioningPhase.RAID_CONFIGURATION);
    }
}
