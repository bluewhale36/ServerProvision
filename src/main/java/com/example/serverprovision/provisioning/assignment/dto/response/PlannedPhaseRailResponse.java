package com.example.serverprovision.provisioning.assignment.dto.response;

import com.example.serverprovision.execution.dto.response.GuestServerDetailResponse;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.provisioning.assignment.enums.AssignmentState;
import com.example.serverprovision.provisioning.assignment.view.AssignmentStateView;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * 상세 화면의 단계 rail 뷰 모델 — 계획(할당 스냅샷의 phase)에 실제 진행 커서와 원장 행을 겹친 결과.
 *
 * <p>rail 의 단계는 세 출처의 합집합이다(S21-1). ① 할당 없이도 항상 밟는 {@code BOOTSTRAPPING} · {@code DIAGNOSE_LINUX}
 * ② 활성 할당의 계획 phase ③ 원장에 행이 남은 phase — 할당이 종료된 뒤에도 그 단계에서 무엇을 했는지가 화면에서
 * 사라지지 않게 한다. 각 단계는 실행 상태(done/current/failed/pending)와 자기 원장 행, 시작 · 종료 시각을 함께 든다.
 * 순수 함수 {@link #of}(DB 불요)라 단위 테스트로 겹침 규칙을 검증한다.</p>
 */
public record PlannedPhaseRailResponse(
        boolean assigned,
        String definitionName,
        AssignmentState state,
        String badgeClass,
        String reassignBlockReason,
        /** 지금 든 스냅샷이 이 서버의 하드웨어와 맞지 않으면 그 사유(U3-5-a) — 경고 배너의 입력. */
        String hardwareMismatchReason,
        List<Entry> entries
) {

    /**
     * 단계 패널 한 칸 — phase · 표시명 · 실행 상태 · 그 단계의 원장 행 · 시작(첫 행) · 종료(마지막 행, 열려 있으면 null).
     * 패널은 완료 단계만 기본 접힘이다(사용자 결정 2026-09-13) — {@link #expanded()} 가 그 판정이다.
     */
    public record Entry(ProvisioningPhase phase, String description, RunState runState,
                        List<GuestServerDetailResponse.Step> steps,
                        LocalDateTime startedAt, LocalDateTime finishedAt) {

        public boolean expanded() {
            if (runState == RunState.DONE) {
                return false;
            }
            // 회수로 멈춘 단계는 밟은 기록이 있을 때만 펼친다 — 밟지 않을 단계를 빈 채로 열어 두면 소음이다
            if (runState == RunState.STOPPED) {
                return !steps.isEmpty();
            }
            return true;
        }

        /** 아직 닫히지 않은 원장 행(마지막 것) — 스테퍼의 국면 문구 폴백. 없으면 null. */
        public GuestServerDetailResponse.Step openStep() {
            for (int i = steps.size() - 1; i >= 0; i--) {
                if (steps.get(i).finishedAt() == null) {
                    return steps.get(i);
                }
            }
            return null;
        }
    }

    /** 단계의 실제 진행 상태. */
    public enum RunState {
        /** 커서보다 앞, 또는 종단 뒤 — 완료. */
        DONE,
        /** 커서 위치 — 진행 중. */
        CURRENT,
        /** 커서 위치인데 실패 신호가 켜져 있다. */
        FAILED,
        /** 커서보다 뒤 — 예정(계획). */
        PENDING,
        /** 회수(decommission)로 멈춘 자리와 그 뒤 — 더는 밟지 않는다(CP5 F-1). */
        STOPPED
    }

    /** 할당 없이도 밟는 단계 — 정의서와 무관하게 rail 의 앞자리를 차지한다. */
    private static final List<ProvisioningPhase> IMPLICIT_PHASES =
            List.of(ProvisioningPhase.BOOTSTRAPPING, ProvisioningPhase.DIAGNOSE_LINUX);

    /**
     * 상세 응답 전체에서 rail 을 만든다 — 계획 · 원장에 더해 <b>단계 데이터가 실려 온 phase</b>(펌웨어 계획 · 집행,
     * 설정, RAID 계획 · 실물, Windows 설치)도 세운다. 그 데이터를 보일 자리는 그 단계의 패널뿐이라, 패널이 없으면
     * 화면에서 사라지기 때문이다.
     */
    public static PlannedPhaseRailResponse of(AssignmentPlanResponse plan, GuestServerDetailResponse server) {
        EnumSet<ProvisioningPhase> fromData = EnumSet.noneOf(ProvisioningPhase.class);
        if (server.firmwarePlan() != null || server.firmwareFlash() != null) {
            fromData.add(ProvisioningPhase.FIRMWARE_UPDATING);
        }
        if (server.firmwareSetting() != null) {
            fromData.add(ProvisioningPhase.FIRMWARE_SETTING);
        }
        if (server.raidPlan() != null || (server.raidVolumes() != null && !server.raidVolumes().isEmpty())) {
            fromData.add(ProvisioningPhase.RAID_CONFIGURATION);
        }
        if (server.windowsInstall() != null) {
            fromData.add(ProvisioningPhase.OS_INSTALLING);
        }
        return build(plan, server.progress(), server.steps() != null ? server.steps() : List.of(), fromData,
                server.decommissionedAt() != null);
    }

    /**
     * @param plan     활성 할당 계획(미할당이면 계획 phase 없음 — 암시 단계 둘만 선다)
     * @param progress 진행 상태(등록 직후엔 null 일 수 있다 — 전부 PENDING)
     * @param steps    원장 행(시작 시각 순) — 단계별로 나눠 싣는다
     */
    public static PlannedPhaseRailResponse of(AssignmentPlanResponse plan,
                                              GuestServerDetailResponse.Progress progress,
                                              List<GuestServerDetailResponse.Step> steps) {
        return build(plan, progress, steps, EnumSet.noneOf(ProvisioningPhase.class), false);
    }

    /** 회수된 서버의 rail — 커서와 그 뒤는 STOPPED(단위 테스트용 진입점). */
    public static PlannedPhaseRailResponse ofDecommissioned(AssignmentPlanResponse plan,
                                                            GuestServerDetailResponse.Progress progress,
                                                            List<GuestServerDetailResponse.Step> steps) {
        return build(plan, progress, steps, EnumSet.noneOf(ProvisioningPhase.class), true);
    }

    private static PlannedPhaseRailResponse build(AssignmentPlanResponse plan,
                                                  GuestServerDetailResponse.Progress progress,
                                                  List<GuestServerDetailResponse.Step> steps,
                                                  EnumSet<ProvisioningPhase> fromData,
                                                  boolean decommissioned) {
        EnumSet<ProvisioningPhase> phases = EnumSet.copyOf(IMPLICIT_PHASES);
        phases.addAll(plan.plannedPhases());
        phases.addAll(fromData);
        for (GuestServerDetailResponse.Step step : steps) {
            if (step.phase() != null) {
                phases.add(step.phase());
            }
        }

        ProvisioningPhase cursor = progress != null ? progress.currentPhase() : null;
        boolean failed = progress != null && progress.failedAt() != null;
        boolean completed = progress != null && progress.completedAt() != null;

        List<Entry> entries = new ArrayList<>();
        for (ProvisioningPhase phase : phases) {   // EnumSet 은 선언 순으로 돈다 = 실행 순서
            List<GuestServerDetailResponse.Step> own = steps.stream().filter(s -> s.phase() == phase).toList();
            entries.add(new Entry(phase, phase.getDescription(),
                    runStateOf(phase, cursor, failed, completed, decommissioned), own,
                    own.isEmpty() ? null : own.get(0).startedAt(),
                    own.isEmpty() ? null : own.get(own.size() - 1).finishedAt()));
        }
        String badgeClass = plan.state() != null ? AssignmentStateView.badgeClass(plan.state()) : null;
        return new PlannedPhaseRailResponse(plan.assigned(), plan.definitionName(), plan.state(),
                badgeClass, plan.reassignBlockReason(), plan.hardwareMismatchReason(), List.copyOf(entries));
    }

    private static RunState runStateOf(ProvisioningPhase phase, ProvisioningPhase cursor, boolean failed,
                                       boolean completed, boolean decommissioned) {
        if (completed) {
            return RunState.DONE;
        }
        if (cursor == null) {
            return decommissioned ? RunState.STOPPED : RunState.PENDING;
        }
        if (phase.ordinal() < cursor.ordinal()) {
            return RunState.DONE;
        }
        // 회수(CP5 F-1) — 종단하지 못한 채 멈춘 서버는 커서 자리도 그 뒤도 "진행 중 · 예정" 이 아니다
        if (decommissioned) {
            return RunState.STOPPED;
        }
        if (phase == cursor) {
            return failed ? RunState.FAILED : RunState.CURRENT;
        }
        return RunState.PENDING;
    }
}
