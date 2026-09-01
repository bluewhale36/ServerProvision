package com.example.serverprovision.execution.engine.firmware;

import com.example.serverprovision.execution.engine.ProvisioningHistoryRecorder;
import com.example.serverprovision.execution.entity.GuestServer;
import com.example.serverprovision.execution.enums.ProvisioningPhaseStep;
import com.example.serverprovision.execution.enums.ProvisioningStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** E2-4 Q4 — 전원 사건 행의 모양과 phase 수준 판정. */
@ExtendWith(MockitoExtension.class)
class FlashLedgerTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 8, 31, 12, 0);

    @Mock ProvisioningHistoryRecorder recorder;

    @Test
    @DisplayName("instantPower — SUCCEEDED 단발 행에 사유 코드와 결과 메시지(무장 요약 포함)를 싣는다")
    void instantPowerShape() {
        FlashLedger ledger = new FlashLedger(recorder);
        GuestServer server = GuestServer.builder().id(UUID.randomUUID()).systemUUID(UUID.randomUUID()).build();

        ledger.instantPower(server, ProvisioningPhaseStep.BMC_UPDATING, FlashLedger.POWER_ON,
                "다음 부팅 PXE 강제 : 반영 확인 · 전원이 켜졌습니다", T);

        ArgumentCaptor<String> meta = ArgumentCaptor.forClass(String.class);
        verify(recorder).recordInstant(eq(server), eq(ProvisioningPhaseStep.BMC_UPDATING),
                eq(ProvisioningStatus.SUCCEEDED), meta.capture(), any());
        assertThat(meta.getValue())
                .contains("\"origin\":\"power-on\"")
                .contains("다음 부팅 PXE 강제 : 반영 확인");
    }

    @Test
    @DisplayName("isPhaseLevel — 전원 사유 둘이 phase 수준 목록에 든다(축 결과 오염 방지)")
    void powerReasonsArePhaseLevel() {
        assertThat(FlashLedger.isPhaseLevel(FlashLedger.POWER_OFF)).isTrue();
        assertThat(FlashLedger.isPhaseLevel(FlashLedger.POWER_ON)).isTrue();
        assertThat(FlashLedger.isPhaseLevel(FlashLedger.RETURN_TIMEOUT)).isTrue();
        assertThat(FlashLedger.isPhaseLevel(FlashLedger.FLASH_COMPLETED)).isFalse();
        assertThat(FlashLedger.isPhaseLevel(null)).isFalse();
    }
}
