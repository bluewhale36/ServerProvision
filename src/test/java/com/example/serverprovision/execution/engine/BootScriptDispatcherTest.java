package com.example.serverprovision.execution.engine;

import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhase;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E1-0b CP4 — dispatch 매트릭스 판정 순서(2~7행) 고정. 이 표가 바뀌면 게스트가 받는 스크립트
 * 계약이 바뀐 것이다. (1행 "미등록 → 등록" 은 BootService 이전 단계 — 등록 서비스 테스트 소관.)
 */
class BootScriptDispatcherTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 7, 18, 12, 0);
    private static final String Q = "systemUUID=abc";

    private final ProvisioningPhaseExecutor diagnoseExecutor = new ProvisioningPhaseExecutor() {
        @Override public ProvisioningPhase phase() { return ProvisioningPhase.DIAGNOSE_LINUX; }
        @Override public String bootScript(GuestServer s, ProvisioningProgress p, String q) {
            return "#!ipxe\nFAKE q=" + q;
        }
    };
    private final BootScriptDispatcher dispatcher =
            new BootScriptDispatcher(new PhaseExecutorRegistry(List.of(diagnoseExecutor)));

    private GuestServer server(LocalDateTime decommissionedAt) {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID())
                .decommissionedAt(decommissionedAt).build();
    }

    private ProvisioningProgress.ProvisioningProgressBuilder progress() {
        return ProvisioningProgress.builder()
                .currentPhase(ProvisioningPhase.BOOTSTRAPPING).lastTransitionAt(T);
    }

    @Test
    @DisplayName("우선순위 — 회수 > 실패 > 종단 > 미개시 (복합 상태에서 상위 행이 이긴다)")
    void priorityOrder() {
        // 회수 + 실패 → 회수가 이긴다 (derive 진리표와 동일 정렬)
        assertThat(dispatcher.dispatch(server(T),
                progress().startedAt(T).failedAt(T).failedStepCode(ProvisioningPhaseStep.OS_INSTALLING).build(), Q))
                .contains("decommissioned server");
        // 실패 + 종단은 표현 불가(상호배타) — 실패 vs 미개시: 실패가 이긴다
        assertThat(dispatcher.dispatch(server(null),
                progress().failedAt(T).failedStepCode(ProvisioningPhaseStep.BIOS_UPDATING).build(), Q))
                .contains("FAILED at BIOS_UPDATING");
    }

    @Test
    @DisplayName("4행 이분(E1-2) — 완주 + OS 설치 전(진단만 완주) → 입고 검수 대기 (exit 금지)")
    void completed_beforeOsInstall_awaitsIntake() {
        String script = dispatcher.dispatch(server(null),
                progress().startedAt(T).currentPhase(ProvisioningPhase.DIAGNOSE_LINUX).completedAt(T).build(), Q);
        assertThat(script)
                .contains("awaiting assignment")
                .contains("chain /api/pxe/v1/boot?" + Q)
                .doesNotContain("exit");   // OS 없는 베어메탈에 exit = 부팅 실패 루프 (로드맵 D3)
    }

    @Test
    @DisplayName("4행 이분(E1-2) — 완주 + OS 설치 이후 커서 → exit (로컬 부팅 폴스루)")
    void completed_afterOsInstall_exitsWithoutChain() {
        String script = dispatcher.dispatch(server(null),
                progress().startedAt(T).currentPhase(ProvisioningPhase.OS_SETTING).completedAt(T).build(), Q);
        assertThat(script).contains("exit").doesNotContain("chain");
    }

    @Test
    @DisplayName("5행 미개시 — 개시 게이트 대기 + 원본 쿼리로 chain 재진입")
    void notStarted_waits() {
        String script = dispatcher.dispatch(server(null), progress().build(), Q);
        assertThat(script)
                .contains("waiting for provisioning start")
                .contains("sleep 30")
                .contains("chain /api/pxe/v1/boot?" + Q);
    }

    @Test
    @DisplayName("6행 HOLD — 실행기 미등록 phase 는 명시 대기 (silent 통과 금지)")
    void unregisteredPhase_holds() {
        String script = dispatcher.dispatch(server(null),
                progress().startedAt(T).currentPhase(ProvisioningPhase.FIRMWARE_UPDATING).build(), Q);
        assertThat(script).contains("FIRMWARE_UPDATING not implemented yet (HOLD)");
    }

    @Test
    @DisplayName("7행 위임 — 등록된 실행기의 bootScript 로 (쿼리 관통)")
    void registeredPhase_delegates() {
        String script = dispatcher.dispatch(server(null),
                progress().startedAt(T).currentPhase(ProvisioningPhase.DIAGNOSE_LINUX).build(), Q);
        assertThat(script).contains("FAKE q=" + Q);
    }

    @Test
    @DisplayName("개시 + 커서 BOOTSTRAPPING — 다음 진입 대상(진단) 실행기로 위임 (커서는 체크인에야 움직인다, DEC-2)")
    void startedAtBootstrapping_targetsNextPhase() {
        String script = dispatcher.dispatch(server(null), progress().startedAt(T).build(), Q);
        assertThat(script).contains("FAKE q=" + Q);   // BOOTSTRAPPING 이 아니라 DIAGNOSE_LINUX 실행기
    }

    @Test
    @DisplayName("개시 + 커서 BOOTSTRAPPING + 진단 실행기 미등록 — HOLD 는 진단 phase 를 안내한다")
    void startedAtBootstrapping_withoutExecutor_holdsOnDiagnose() {
        BootScriptDispatcher empty = new BootScriptDispatcher(new PhaseExecutorRegistry(List.of()));
        String script = empty.dispatch(server(null), progress().startedAt(T).build(), Q);
        assertThat(script).contains("DIAGNOSE_LINUX not implemented yet (HOLD)");
    }
}
