package com.example.serverprovision.execution.pxeinfra.inspect;

import com.example.serverprovision.execution.pxeinfra.command.AllowedCommand;
import com.example.serverprovision.execution.pxeinfra.command.CommandResult;
import com.example.serverprovision.execution.pxeinfra.command.StubSystemCommandRunner;
import com.example.serverprovision.execution.pxeinfra.config.PxeInfraProperties;
import com.example.serverprovision.execution.pxeinfra.spi.ConfigFileCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * dhcpd 조각 판정 검증 — 조각 존재 여부 × {@code dhcpd -t} 종료 양태의 곱집합을 {@link ConfigFileCondition} 으로
 * 흡수한다. 미구성이면 특권 명령을 아예 spawn 하지 않음을 스텁 호출 횟수로 확인한다.
 */
class DhcpdConfigInspectorTest {

    @TempDir
    Path tmp;

    @Test
    @DisplayName("미구성 — 설정 빈 부재면 NOT_CONFIGURED, 특권 명령 미실행")
    void inspect_notConfigured_returnsNotConfigured_withoutSpawning() {
        StubSystemCommandRunner runner = new StubSystemCommandRunner();
        DhcpdConfigInspector inspector = new DhcpdConfigInspector(runner, providerOf(null));

        assertThat(inspector.inspect()).isEqualTo(ConfigFileCondition.NOT_CONFIGURED);
        assertThat(runner.callCount()).isZero();
    }

    @Test
    @DisplayName("조각 존재 + dhcpd -t 종료 0 → SYNTAX_OK")
    void inspect_present_exitZero_syntaxOk() throws IOException {
        ConfigFileCondition condition = inspectWith(true, CommandResult.completed(0, "", ""));
        assertThat(condition).isEqualTo(ConfigFileCondition.SYNTAX_OK);
    }

    @Test
    @DisplayName("조각 존재 + dhcpd -t 종료 1 → SYNTAX_ERROR")
    void inspect_present_exitNonZero_syntaxError() throws IOException {
        ConfigFileCondition condition = inspectWith(true, CommandResult.completed(1, "", "구성 오류"));
        assertThat(condition).isEqualTo(ConfigFileCondition.SYNTAX_ERROR);
    }

    @Test
    @DisplayName("조각 존재 + dhcpd -t 타임아웃 → UNKNOWN(문법 미검사)")
    void inspect_present_timedOut_unknown() throws IOException {
        ConfigFileCondition condition = inspectWith(true, CommandResult.timedOut());
        assertThat(condition).isEqualTo(ConfigFileCondition.UNKNOWN);
    }

    @Test
    @DisplayName("조각 존재 + dhcpd 바이너리 부재 → UNKNOWN(문법 미검사)")
    void inspect_present_notFound_unknown() throws IOException {
        ConfigFileCondition condition = inspectWith(true, CommandResult.notFound());
        assertThat(condition).isEqualTo(ConfigFileCondition.UNKNOWN);
    }

    @Test
    @DisplayName("조각 부재 → ABSENT + dhcpd -t(sudo) 미실행(헛된 특권 spawn 회피)")
    void inspect_fragmentAbsent_absentWithoutSpawning() {
        // 앱이 관리하는 조각이 없으면 판정이 ABSENT 로 고정 — 특권 명령을 spawn 할 이유가 없다.
        Path fragment = tmp.resolve("dhcpd-fragment.conf");   // 생성하지 않음 → 부재
        PxeInfraProperties props = new PxeInfraProperties(
                fragment.toString(), tmp.resolve("dhcpd.conf").toString(), tmp.resolve("dhcpd.leases").toString());
        StubSystemCommandRunner runner = new StubSystemCommandRunner()
                .stub(AllowedCommand.DHCPD_SYNTAX_CHECK, CommandResult.completed(0, "", ""));

        ConfigFileCondition condition = new DhcpdConfigInspector(runner, providerOf(props)).inspect();

        assertThat(condition).isEqualTo(ConfigFileCondition.ABSENT);
        assertThat(runner.callCount()).isZero();
    }

    /** 조각 존재/부재 + dhcpd -t 스텁 결과로 판정을 돌린다. */
    private ConfigFileCondition inspectWith(boolean fragmentPresent, CommandResult syntaxResult) throws IOException {
        Path fragment = tmp.resolve("dhcpd-fragment.conf");
        if (fragmentPresent) {
            Files.writeString(fragment, "# dhcpd 조각\n");
        }
        Path conf = tmp.resolve("dhcpd.conf");
        PxeInfraProperties props = new PxeInfraProperties(
                fragment.toString(), conf.toString(), tmp.resolve("dhcpd.leases").toString());
        StubSystemCommandRunner runner = new StubSystemCommandRunner()
                .stub(AllowedCommand.DHCPD_SYNTAX_CHECK, syntaxResult);
        return new DhcpdConfigInspector(runner, providerOf(props)).inspect();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<PxeInfraProperties> providerOf(PxeInfraProperties props) {
        ObjectProvider<PxeInfraProperties> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(props);
        return provider;
    }
}
