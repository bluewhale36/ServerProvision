package com.example.serverprovision.execution.engine.boot;

import com.example.serverprovision.execution.engine.phase.ProvisioningCompletedEvent;
import com.example.serverprovision.execution.entity.GuestServerDetail;
import com.example.serverprovision.execution.repository.GuestServerDetailRepository;
import com.example.serverprovision.execution.vo.IpAddressVO;
import com.example.serverprovision.global.redfish.BootOrderPolicy;
import com.example.serverprovision.global.redfish.PowerControlResult;
import com.example.serverprovision.global.redfish.RedfishPowerService;
import com.example.serverprovision.global.redfish.RedfishPowerState;
import com.example.serverprovision.global.redfish.RedfishTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** HF20 CP4 — 종단 사건 → DISK_FIRST 정착. BMC 미검출이면 건너뛰고, 실패 · 예외는 종단을 되돌리지 않는다(best effort). */
@ExtendWith(MockitoExtension.class)
class BootOrderSettlementTest {

    private static final UUID GUEST = UUID.randomUUID();
    private static final LocalDateTime T = LocalDateTime.of(2026, 9, 17, 18, 0);

    @Mock GuestServerDetailRepository detailRepository;
    @Mock RedfishPowerService powerService;
    @InjectMocks BootOrderSettlement settlement;

    private GuestServerDetail detailWithBmc() {
        GuestServerDetail detail = mock(GuestServerDetail.class);
        given(detail.getBmcIp()).willReturn(IpAddressVO.of("10.1.1.9"));
        given(detail.getBoardSerial()).willReturn("QG260700082");
        return detail;
    }

    @Test
    @DisplayName("종단 사건 — 상세의 BMC IP · 보드 시리얼로 DISK_FIRST 를 정착한다")
    void settlesDiskFirstOnCompletion() {
        GuestServerDetail detail = detailWithBmc();   // 중첩 stubbing 금지 — 먼저 만든다
        given(detailRepository.findByGuestServer_Id(GUEST)).willReturn(Optional.of(detail));
        given(powerService.settleBootOrder(any(), eq(BootOrderPolicy.DISK_FIRST)))
                .willReturn(PowerControlResult.sent(RedfishPowerState.UNKNOWN, "디스크를 맨 앞으로 — 정착"));

        settlement.onCompleted(new ProvisioningCompletedEvent(GUEST, T));

        verify(powerService).settleBootOrder(eq(new RedfishTarget("10.1.1.9", "QG260700082")), eq(BootOrderPolicy.DISK_FIRST));
    }

    @Test
    @DisplayName("BMC 미검출(상세 없음 · IP null) — BMC 를 부르지 않는다")
    void skipsWithoutBmc() {
        given(detailRepository.findByGuestServer_Id(GUEST)).willReturn(Optional.empty());
        settlement.onCompleted(new ProvisioningCompletedEvent(GUEST, T));

        GuestServerDetail noIp = mock(GuestServerDetail.class);
        given(noIp.getBmcIp()).willReturn(null);
        given(detailRepository.findByGuestServer_Id(GUEST)).willReturn(Optional.of(noIp));
        settlement.onCompleted(new ProvisioningCompletedEvent(GUEST, T));

        verify(powerService, never()).settleBootOrder(any(), any());
    }

    @Test
    @DisplayName("정착 FAILED · 런타임 예외 — 종단 리스너는 예외를 새지 않는다(종단은 이미 확정)")
    void failureAndExceptionAreSwallowed() {
        GuestServerDetail detail = detailWithBmc();
        given(detailRepository.findByGuestServer_Id(GUEST)).willReturn(Optional.of(detail));
        given(powerService.settleBootOrder(any(), any()))
                .willReturn(PowerControlResult.failed(RedfishPowerState.UNKNOWN, "정착하지 못했습니다 : 404"))
                .willThrow(new IllegalStateException("boom"));

        assertThatCode(() -> settlement.onCompleted(new ProvisioningCompletedEvent(GUEST, T))).doesNotThrowAnyException();
        assertThatCode(() -> settlement.onCompleted(new ProvisioningCompletedEvent(GUEST, T))).doesNotThrowAnyException();
    }
}
