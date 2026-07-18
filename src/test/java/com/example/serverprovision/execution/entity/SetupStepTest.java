package com.example.serverprovision.execution.entity;

import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** E1-0b CP4 — RUNNING 열림/닫힘(DEC-3): 닫힘은 1회, 중복 닫힘 = no-op(false) 멱등의 실체. */
class SetupStepTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 7, 18, 12, 0);
    private static final LocalDateTime T1 = T0.plusMinutes(3);

    private GuestServer guest() {
        return GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();
    }

    @Test
    @DisplayName("openRunning — RUNNING · startedAt 만 기록(finishedAt null)")
    void openRunning_opensLedgerRow() {
        SetupStep step = SetupStep.openRunning(guest(), ProvisioningPhaseStep.INFORMATION_COLLECTING, T0);
        assertThat(step.getStatus()).isEqualTo(ProvisioningStatus.RUNNING);
        assertThat(step.getStartedAt()).isEqualTo(T0);
        assertThat(step.getFinishedAt()).isNull();
        assertThat(step.getId()).isNotNull();
    }

    @Test
    @DisplayName("close — 종결 1회 기록(true), 중복 close 는 행 불변 + false (멱등)")
    void close_onceThenNoOp() {
        SetupStep step = SetupStep.openRunning(guest(), ProvisioningPhaseStep.INFORMATION_COLLECTING, T0);

        assertThat(step.close(ProvisioningStatus.FAILED, "{\"reason\":\"x\"}", T1)).isTrue();
        assertThat(step.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(step.getStatusMeta()).isEqualTo("{\"reason\":\"x\"}");
        assertThat(step.getFinishedAt()).isEqualTo(T1);

        // 중복 종료 보고 — 아무것도 바뀌지 않는다
        assertThat(step.close(ProvisioningStatus.SUCCEEDED, null, T1.plusMinutes(1))).isFalse();
        assertThat(step.getStatus()).isEqualTo(ProvisioningStatus.FAILED);
        assertThat(step.getFinishedAt()).isEqualTo(T1);
    }
}
