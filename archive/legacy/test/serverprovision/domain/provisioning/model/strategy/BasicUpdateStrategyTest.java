package com.example.serverprovision.domain.provisioning.model.strategy;

import com.example.serverprovision.application.setting.model.BasicUpdate;
import com.example.serverprovision.application.setting.model.OSInstallation;
import com.example.serverprovision.domain.node.entity.ServerNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BasicUpdateStrategyTest {

    private final BasicUpdateStrategy strategy = new BasicUpdateStrategy();

    @Test
    @DisplayName("BasicUpdate 타입 프로세스를 지원한다")
    void supports_basicUpdate_returnsTrue() {
        assertThat(strategy.supports(mock(BasicUpdate.class))).isTrue();
    }

    @Test
    @DisplayName("OSInstallation 타입은 지원하지 않는다")
    void supports_osInstallation_returnsFalse() {
        assertThat(strategy.supports(mock(OSInstallation.class))).isFalse();
    }

    @Test
    @DisplayName("로컬 부팅 iPXE 스크립트를 반환한다")
    void generateIPXEScript_returnsLocalBootScript() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        BasicUpdate process = mock(BasicUpdate.class);

        String script = strategy.generateIPXEScript(node, process);

        assertThat(script).startsWith("#!ipxe\n");
        assertThat(script).contains("exit");
        assertThat(script).doesNotContain("TODO");
    }
}
