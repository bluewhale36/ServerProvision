package com.example.serverprovision.execution.engine.rhel.ossetting;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.entity.ProvisioningHistory;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import com.example.serverprovision.execution.repository.ProvisioningHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/** E4-1-b-7 — 판정을 단발 행 meta 로 남기고 같은 판정으로 읽어 온다. */
@ExtendWith(MockitoExtension.class)
class OsSettingLedgerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 18, 0);

    @Mock ProvisioningHistoryRecorder recorder;
    @Mock ProvisioningHistoryRepository historyRepository;

    private final GuestServer server = GuestServer.builder().id(UUID.randomUUID()).build();

    private OsSettingVerdict failed() {
        RhelOsSettingPlan plan = new RhelOsSettingPlan(SelinuxMode.ENFORCING, List.of(), List.of("vim"),
                List.of(new UserScript("a.sh", "true")));
        return OsSettingVerdict.judge(plan, Optional.of(
                new OsSettingObservation("enforcing", Map.of(), Map.of("vim", false), List.of(0))));
    }

    @Test
    @DisplayName("실패 판정 = FAILED 단발 행, meta 를 읽으면 같은 판정")
    void recordAndRead_roundTrip() {
        OsSettingLedger ledger = new OsSettingLedger(recorder, historyRepository, new ObjectMapper());
        OsSettingVerdict verdict = failed();

        ledger.record(server, verdict, NOW);

        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordInstant(eq(server), eq(ProvisioningPhaseStep.OS_SETTING), eq(ProvisioningStatus.FAILED),
                meta.capture(), eq(NOW));
        assertThat(meta.getValue()).contains("\"origin\":\"rhel-first-boot\"", "\"detail\":\"패키지 vim 의 설치를 확인하지 못했습니다.\"");
        ProvisioningHistory row = ProvisioningHistory.instant(server, ProvisioningPhaseStep.OS_SETTING,
                ProvisioningStatus.FAILED, meta.getValue(), NOW);
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(server.getId(), ProvisioningPhaseStep.OS_SETTING))
                .willReturn(Optional.of(row));

        assertThat(ledger.latestOf(server.getId())).contains(verdict);
    }

    @Test
    @DisplayName("다른 출처의 행(운영자 전환 등)은 판정으로 읽지 않는다")
    void foreignRow_empty() {
        OsSettingLedger ledger = new OsSettingLedger(recorder, historyRepository, new ObjectMapper());
        ProvisioningHistory row = ProvisioningHistory.instant(server, ProvisioningPhaseStep.OS_SETTING,
                ProvisioningStatus.FAILED, "{\"origin\":\"operator\"}", NOW);
        given(historyRepository.findFirstByGuestServer_IdAndStepCodeOrderByCreatedAtDesc(server.getId(), ProvisioningPhaseStep.OS_SETTING))
                .willReturn(Optional.of(row));

        assertThat(ledger.latestOf(server.getId())).isEmpty();
    }
}
