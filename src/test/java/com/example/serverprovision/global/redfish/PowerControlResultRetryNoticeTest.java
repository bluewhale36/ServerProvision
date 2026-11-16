package com.example.serverprovision.global.redfish;

import com.example.serverprovision.global.ui.enums.FlashTone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** [재시도 후 네트워크 부팅] 알림 — 결과에 맞는 문장과 색(S22-3, S22-2 D-4 정정). */
class PowerControlResultRetryNoticeTest {

    @Test
    @DisplayName("부팅 지정 확인 + 전원 발행 — 성공 초록")
    void applied() {
        PowerControlResult result = PowerControlResult.sent(RedfishPowerState.ON, "원장 문장", BootOverrideOutcome.applied());

        assertThat(result.retryBootNotice()).isEqualTo("재시도를 접수했습니다. 서버를 네트워크 부팅으로 다시 시작했습니다.");
        assertThat(result.retryBootTone()).isEqualTo(FlashTone.SUCCESS);
    }

    @Test
    @DisplayName("부팅 지정 거절 — BMC 사유를 괄호에 넣고 노랑")
    void rejected() {
        PowerControlResult result = PowerControlResult.sent(RedfishPowerState.ON, "원장 문장", BootOverrideOutcome.rejected("412"));

        assertThat(result.retryBootNotice()).contains("거절했습니다(412)");
        assertThat(result.retryBootTone()).isEqualTo(FlashTone.WARNING);
    }

    @Test
    @DisplayName("전원 제어 실패 — 실패 사유를 담고 빨강(전원 명령이 가지 않았으니 부팅 지정은 보지 않는다)")
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
