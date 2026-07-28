package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.command.AllowedCommand;
import com.example.serverprovision.execution.pxeinfra.command.SystemCommandRunner;
import com.example.serverprovision.execution.pxeinfra.spi.ServiceState;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * dhcpd systemd 서비스 상태 조회 — {@code systemctl is-active dhcpd} 결과를 {@link ServiceState} 로 흡수한다.
 * {@code is-active} 는 비특권 조회라 sudo 를 거치지 않는다(실패·부재·타임아웃은 UNKNOWN 으로 흡수).
 */
@Component
public class SystemServiceInspector {

    private final SystemCommandRunner runner;

    public SystemServiceInspector(SystemCommandRunner runner) {
        this.runner = runner;
    }

    public ServiceState status() {
        return ServiceState.from(runner.run(AllowedCommand.DHCPD_SERVICE_STATUS, List.of()));
    }
}
