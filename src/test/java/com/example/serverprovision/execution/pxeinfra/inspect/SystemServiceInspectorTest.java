package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.command.AllowedCommand;
import com.example.serverprovision.execution.pxeinfra.command.CommandResult;
import com.example.serverprovision.execution.pxeinfra.command.StubSystemCommandRunner;
import com.example.serverprovision.execution.pxeinfra.spi.ServiceState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dhcpd 서비스 상태 조회 검증 — {@code systemctl is-active} stdout·종료 양태를 {@link ServiceState} 로 흡수한다.
 * 종료 양태(부재·타임아웃)가 stdout 파싱보다 우선하며, 미지 stdout 은 UNKNOWN 으로 흡수한다.
 */
class SystemServiceInspectorTest {

    @Test
    @DisplayName("stdout active → ACTIVE")
    void status_active() {
        assertThat(statusFor(CommandResult.completed(0, "active\n", ""))).isEqualTo(ServiceState.ACTIVE);
    }

    @Test
    @DisplayName("stdout inactive(종료 3) → INACTIVE (종료코드 아닌 stdout 으로 판정)")
    void status_inactive() {
        assertThat(statusFor(CommandResult.completed(3, "inactive\n", ""))).isEqualTo(ServiceState.INACTIVE);
    }

    @Test
    @DisplayName("stdout failed → FAILED")
    void status_failed() {
        assertThat(statusFor(CommandResult.completed(3, "failed\n", ""))).isEqualTo(ServiceState.FAILED);
    }

    @Test
    @DisplayName("stdout 기타(activating 등) → UNKNOWN")
    void status_otherStdout_unknown() {
        assertThat(statusFor(CommandResult.completed(0, "activating\n", ""))).isEqualTo(ServiceState.UNKNOWN);
    }

    @Test
    @DisplayName("타임아웃 → UNKNOWN(종료 양태 우선)")
    void status_timedOut_unknown() {
        assertThat(statusFor(CommandResult.timedOut())).isEqualTo(ServiceState.UNKNOWN);
    }

    @Test
    @DisplayName("바이너리 부재 → UNKNOWN(종료 양태 우선)")
    void status_notFound_unknown() {
        assertThat(statusFor(CommandResult.notFound())).isEqualTo(ServiceState.UNKNOWN);
    }

    private ServiceState statusFor(CommandResult result) {
        StubSystemCommandRunner runner = new StubSystemCommandRunner()
                .stub(AllowedCommand.DHCPD_SERVICE_STATUS, result);
        return new SystemServiceInspector(runner).status();
    }
}
