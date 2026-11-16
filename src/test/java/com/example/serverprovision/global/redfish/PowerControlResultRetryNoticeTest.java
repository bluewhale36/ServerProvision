package com.example.serverprovision.global.redfish;

import com.example.serverprovision.global.ui.enums.FlashTone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** [재시도 후 전원 켜기] 알림 — 결과에 맞는 문장과 색(S22-3, S22-2 D-4 정정). */
class PowerControlResultRetryNoticeTest {

    @Test
    @DisplayName("전원 명령 전달 — 성공 초록, 원장 문장 대신 화면 문장")
    void sent() {
        PowerControlResult result = PowerControlResult.sent(RedfishPowerState.ON, "원장 문장");

        assertThat(result.retryBootNotice()).isEqualTo("재시도를 접수했습니다. 서버가 네트워크로 부팅하면 이어서 진행합니다.");
        assertThat(result.retryBootTone()).isEqualTo(FlashTone.SUCCESS);
    }

    @Test
    @DisplayName("전원 제어 실패 — 실패 사유를 담고 빨강")
    void powerFailed() {
        PowerControlResult result = PowerControlResult.failed(null, "BMC 에 연결하지 못했습니다.");

        assertThat(result.retryBootNotice())
                .startsWith("재시도는 접수했지만 전원 제어에 실패해")
                .contains("BMC 에 연결하지 못했습니다.");
        assertThat(result.retryBootTone()).isEqualTo(FlashTone.DANGER);
    }

    @Test
    @DisplayName("BMC 미검출 — 실패와 같은 빨강")
    void unsupported() {
        assertThat(PowerControlResult.unsupported().retryBootTone()).isEqualTo(FlashTone.DANGER);
    }
}
