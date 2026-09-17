package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.command.AllowedCommand;
import com.example.serverprovision.execution.pxeinfra.command.CommandResult;
import com.example.serverprovision.execution.pxeinfra.command.SystemCommandRunner;
import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.spi.ConfigFileCondition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.util.List;

/**
 * dnsmasq 조각 파일의 무결성 판정 — 조각 존재 여부 + {@code dnsmasq --test -C} 전체 구성 문법 검사 결과를 합쳐
 * {@link ConfigFileCondition} 으로 흡수한다. 미구성이면 명령을 spawn 하지 않고 곧장 서빙 비활성으로 판정한다.
 */
@Component
public class DnsmasqConfigInspector {

    private final SystemCommandRunner runner;
    private final ObjectProvider<PxeInfraProperties> propertiesProvider;

    public DnsmasqConfigInspector(SystemCommandRunner runner, ObjectProvider<PxeInfraProperties> propertiesProvider) {
        this.runner = runner;
        this.propertiesProvider = propertiesProvider;
    }

    /** 조각 존재 + 전체 구성 문법으로 판정. 미구성 → NOT_CONFIGURED, 조각 부재 → ABSENT (둘 다 명령 미실행). */
    public ConfigFileCondition inspect() {
        PxeInfraProperties props = propertiesProvider.getIfAvailable();
        if (props == null) {
            return ConfigFileCondition.NOT_CONFIGURED;
        }
        if (!Files.exists(props.getFragmentPath())) {
            // 앱이 관리하는 조각이 없으면 어차피 ABSENT — 검사를 헛되이 spawn 하지 않고 곧장 판정.
            return ConfigFileCondition.ABSENT;
        }
        CommandResult syntaxCheck = runner.run(
                AllowedCommand.DNSMASQ_SYNTAX_CHECK, List.of(props.getConfPath().toString()));
        return ConfigFileCondition.of(true, syntaxCheck);
    }
}
