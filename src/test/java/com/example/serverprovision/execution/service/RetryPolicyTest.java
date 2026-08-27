package com.example.serverprovision.execution.service;

import com.example.serverprovision.execution.engine.firmware.FlashLedger;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.entity.ProvisioningProgress;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2-1-b CP5 결함 F-1 정정 — 재시도 차단은 "실패 지점" 만으로 정할 수 없다. 굽다가 난 실패는 원인
 * 확인 전 재시도가 위험하지만(DEC-4), 자원 결손 시한 만료는 <b>한 번도 굽지 않은 실패</b>라 자원을
 * 되살린 뒤 다시 시도할 수 있어야 한다. 그 구분이 없으면 이 슬라이스의 회복 경로가 끊긴다.
 */
@ExtendWith(MockitoExtension.class)
class RetryPolicyTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 22, 12, 0);

    @Mock ProvisioningHistoryRepository provisioningHistoryRepository;
    @InjectMocks RetryPolicy retryPolicy;

    private final GuestServer server = GuestServer.builder()
            .id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();

    private ProvisioningProgress failedAt(ProvisioningPhaseStep step) {
        ProvisioningProgress p = ProvisioningProgress.builder()
                .id(UUID.randomUUID()).guestServer(server)
                .currentStep(step).lastTransitionAt(T).build();
        p.start(T);
        p.markFailed(T);
        return p;
    }

    private ProvisioningHistory failureRow(ProvisioningPhaseStep step, String meta) {
        return ProvisioningHistory.instant(server, step, ProvisioningStatus.FAILED, meta, T);
    }

    @Test
    @DisplayName("굽다가 난 펌웨어 실패 → 차단 (원인 미상 재시도는 장비를 못 쓰게 만들 수 있다)")
    void firmwareFlashFailure_isBlocked() {
        ProvisioningProgress progress = failedAt(ProvisioningPhaseStep.BIOS_UPDATING);
        List<ProvisioningHistory> history = List.of(
                failureRow(ProvisioningPhaseStep.BIOS_UPDATING, "{\"reason\":\"flash timeout\"}"));

        assertThat(retryPolicy.isBlocked(progress, history)).isTrue();
        assertThat(retryPolicy.isRetryable(progress, history)).isFalse();
    }

    @Test
    @DisplayName("자원 결손 시한 만료 → 차단하지 않는다 : 굽지 않은 실패라 자원을 되살리면 재개할 수 있다(F-1)")
    void shortageTimeout_isRetryable() {
        ProvisioningProgress progress = failedAt(ProvisioningPhaseStep.BIOS_UPDATING);
        List<ProvisioningHistory> history = List.of(
                failureRow(ProvisioningPhaseStep.BIOS_UPDATING,
                        ProvisioningHistory.holdTtlMeta("BIOS=SIGNATURE_INVALID", Duration.ofHours(48))));

        assertThat(retryPolicy.isBlocked(progress, history)).isFalse();
        assertThat(retryPolicy.isRetryable(progress, history)).isTrue();
    }

    @Test
    @DisplayName("같은 시각의 실패 행만 본다 — 예전에 시한 만료로 실패했던 이력이 이번 차단을 풀지 않는다")
    void onlyTheFailureOfThisMoment_counts() {
        ProvisioningProgress progress = failedAt(ProvisioningPhaseStep.BIOS_UPDATING);
        List<ProvisioningHistory> history = List.of(
                ProvisioningHistory.instant(server, ProvisioningPhaseStep.BIOS_UPDATING, ProvisioningStatus.FAILED,
                        ProvisioningHistory.holdTtlMeta("BIOS=FILE_MISSING", Duration.ofHours(48)), T.minusDays(3)),
                failureRow(ProvisioningPhaseStep.BIOS_UPDATING, "{\"reason\":\"flash timeout\"}"));

        assertThat(retryPolicy.isBlocked(progress, history)).isTrue();
    }

    @Test
    @DisplayName("비펌웨어 실패 · 미실패는 애초에 차단 후보가 아니다 — 원장을 묻지도 않는다")
    void nonFirmwareOrNotFailed_isNeverBlocked() {
        ProvisioningProgress diagnostic = failedAt(ProvisioningPhaseStep.INFORMATION_COLLECTING);
        assertThat(retryPolicy.isBlocked(diagnostic)).isFalse();

        ProvisioningProgress running = ProvisioningProgress.builder()
                .id(UUID.randomUUID()).guestServer(server)
                .currentStep(ProvisioningPhaseStep.BIOS_UPDATING).lastTransitionAt(T).build();
        running.start(T);
        assertThat(retryPolicy.isBlocked(running)).isFalse();

        org.mockito.Mockito.verifyNoInteractions(provisioningHistoryRepository);
    }

    @Test
    @DisplayName("복귀 시한 만료 → 차단하지 않는다 : 모든 축을 다 구운 뒤의 실패라 재시도가 굽기를 다시 열지 않는다(2026-08-27 실기)")
    void returnTimeout_isRetryable() {
        ProvisioningProgress progress = failedAt(ProvisioningPhaseStep.BMC_UPDATING);
        List<ProvisioningHistory> history = List.of(
                ProvisioningHistory.instant(server, ProvisioningPhaseStep.BMC_UPDATING, ProvisioningStatus.SUCCEEDED,
                        ProvisioningHistory.flashTargetMeta("13.06.27", 3L, "/redfish/v1/TaskService/TaskMonitors/3"), T.minusMinutes(20)),
                failureRow(ProvisioningPhaseStep.BMC_UPDATING,
                        ProvisioningHistory.flashOutcomeMeta(FlashLedger.RETURN_TIMEOUT, "전원을 넣은 뒤 시한 안에 돌아오지 않았습니다")));

        assertThat(retryPolicy.isBlocked(progress, history)).isFalse();
        assertThat(retryPolicy.isRetryable(progress, history)).isTrue();
    }
}
