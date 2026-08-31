package com.example.serverprovision.execution.engine.raid;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E3.5-3 V2 보조 — 보류의 중복 억제, 동결의 재동결 억제, 최신 동결 조회. 30초 체크인이 같은 행을
 * 무한히 쌓지 않는 것과 검증이 옳은 동결본을 읽는 것이 여기서 고정된다.
 */
@ExtendWith(MockitoExtension.class)
class RaidLedgerTest {

    private static final UUID GUEST_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 31, 17, 0);

    @Mock ProvisioningHistoryRecorder recorder;
    @Mock ProvisioningHistoryRepository historyRepository;
    @InjectMocks RaidLedger ledger;

    private GuestServer guest() {
        return GuestServer.builder().id(GUEST_ID).systemUUID(UUID.randomUUID()).build();
    }

    private ProvisioningHistory row(ProvisioningStatus status, String meta) {
        if (status == ProvisioningStatus.RUNNING) {
            return ProvisioningHistory.openRunning(guest(), ProvisioningPhaseStep.RAID_APPLYING, NOW, meta);
        }
        return ProvisioningHistory.instant(guest(), ProvisioningPhaseStep.RAID_APPLYING, status, meta, NOW);
    }

    @Test
    @DisplayName("holdInstant — 최신 행이 같은 사유의 보류면 다시 남기지 않는다(중복 억제)")
    void holdInstant_suppressesDuplicate() {
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(
                GUEST_ID, ProvisioningPhaseStep.RAID_APPLYING))
                .willReturn(Optional.of(row(ProvisioningStatus.PENDING,
                        "{\"reason\":\"POLICY_UNDECIDED\",\"detail\":\"외부 볼륨 1개\"}")));

        ledger.holdInstant(guest(), ProvisioningPhaseStep.RAID_APPLYING,
                RaidLedger.POLICY_UNDECIDED, "외부 볼륨 1개", NOW);

        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("holdInstant — 사유가 다르거나 최신 행이 보류가 아니면 남긴다")
    void holdInstant_recordsWhenDifferent() {
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(
                GUEST_ID, ProvisioningPhaseStep.RAID_APPLYING))
                .willReturn(Optional.of(row(ProvisioningStatus.PENDING,
                        "{\"reason\":\"PLAN_REJECTED\",\"detail\":\"VOLUME_LIMIT\"}")));

        ledger.holdInstant(guest(), ProvisioningPhaseStep.RAID_APPLYING,
                RaidLedger.POLICY_UNDECIDED, "외부 볼륨 1개", NOW);

        verify(recorder).recordInstant(any(), eq(ProvisioningPhaseStep.RAID_APPLYING),
                eq(ProvisioningStatus.PENDING), contains("POLICY_UNDECIDED"), eq(NOW));
    }

    @Test
    @DisplayName("freezePlanned — 최신 행이 동결(PENDING · PLANNED)이거나 집행 중(RUNNING)이면 재동결하지 않는다")
    void freezePlanned_suppressed() {
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(
                GUEST_ID, ProvisioningPhaseStep.RAID_APPLYING))
                .willReturn(Optional.of(row(ProvisioningStatus.PENDING, "{\"reason\":\"PLANNED\",\"plan\":{}}")))
                .willReturn(Optional.of(row(ProvisioningStatus.RUNNING, null)));

        ledger.freezePlanned(guest(), "{}", NOW);
        ledger.freezePlanned(guest(), "{}", NOW);

        verify(recorder, never()).recordInstant(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("freezePlanned — 직전 집행이 실패했으면 새 동결을 남긴다(V8 재시도)")
    void freezePlanned_recordsAfterFailure() {
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(
                GUEST_ID, ProvisioningPhaseStep.RAID_APPLYING))
                .willReturn(Optional.of(row(ProvisioningStatus.FAILED,
                        "{\"reason\":\"CREATE_REJECTED\",\"detail\":\"rc=255\"}")));

        ledger.freezePlanned(guest(), "{\"volumes\":[]}", NOW);

        verify(recorder).recordInstant(any(), eq(ProvisioningPhaseStep.RAID_APPLYING),
                eq(ProvisioningStatus.PENDING), contains("\"plan\":{\"volumes\":[]}"), eq(NOW));
    }

    @Test
    @DisplayName("latestFrozenPlanMeta — 여러 행 중 마지막 PLANNED 를 고른다")
    void latestFrozenPlanMeta_picksLastPlanned() {
        given(historyRepository.findAllByServerIdOrderByStartedAt(GUEST_ID)).willReturn(List.of(
                row(ProvisioningStatus.PENDING, "{\"reason\":\"PLANNED\",\"plan\":{\"old\":1}}"),
                row(ProvisioningStatus.FAILED, "{\"reason\":\"CREATE_REJECTED\",\"detail\":\"x\"}"),
                row(ProvisioningStatus.PENDING, "{\"reason\":\"PLANNED\",\"plan\":{\"new\":2}}")));

        assertThat(ledger.latestFrozenPlanMeta(GUEST_ID)).hasValueSatisfying(meta ->
                assertThat(meta).contains("\"new\":2"));
    }

    @Test
    @DisplayName("failInstant — 지정 step 으로 FAILED 단발 + 실패 신호")
    void failInstant_recordsAndMarksFailed() {
        ProvisioningProgress progress = mock(ProvisioningProgress.class);

        ledger.failInstant(guest(), progress, ProvisioningPhaseStep.RAID_VERIFYING,
                RaidLedger.RESULT_MISMATCH, "볼륨 수 불일치", NOW);

        verify(recorder).recordInstant(any(), eq(ProvisioningPhaseStep.RAID_VERIFYING),
                eq(ProvisioningStatus.FAILED), contains("RESULT_MISMATCH"), eq(NOW));
        verify(progress).markFailed(NOW);
    }
}
