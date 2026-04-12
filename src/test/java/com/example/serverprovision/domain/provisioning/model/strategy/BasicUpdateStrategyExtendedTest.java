package com.example.serverprovision.domain.provisioning.model.strategy;

import com.example.serverprovision.application.setting.model.BasicUpdate;
import com.example.serverprovision.domain.node.entity.ServerNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link BasicUpdateStrategy} 보강 단위 테스트.
 * 기존 BasicUpdateStrategyTest 에 없는 스크립트 구조 검증 케이스를 추가한다.
 */
class BasicUpdateStrategyExtendedTest {

    private final BasicUpdateStrategy strategy = new BasicUpdateStrategy();

    // --- supports() 추가 케이스 ---

    @Test
    @DisplayName("supports: null 입력 시 false 반환 (NPE 방어)")
    void supports_null_returnsFalse() {
        assertThat(strategy.supports(null)).isFalse();
    }

    // --- 스크립트 구조 세부 검증 ---

    @Test
    @DisplayName("스크립트 첫 줄이 정확히 '#!ipxe' 이다")
    void generateIPXEScript_firstLine_isIpxeShebang() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        BasicUpdate process = mock(BasicUpdate.class);

        String script = strategy.generateIPXEScript(node, process);
        String firstLine = script.strip().split("\n")[0].trim();

        assertThat(firstLine).isEqualTo("#!ipxe");
    }

    @Test
    @DisplayName("스크립트에 echo 메시지가 포함된다 (사용자에게 미구현 상태 안내)")
    void generateIPXEScript_containsEchoMessage() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        BasicUpdate process = mock(BasicUpdate.class);

        String script = strategy.generateIPXEScript(node, process);

        assertThat(script).contains("echo");
        assertThat(script).containsIgnoringCase("not yet implemented");
    }

    @Test
    @DisplayName("스크립트에 sleep 명령이 포함된다 (사용자가 메시지를 읽을 시간)")
    void generateIPXEScript_containsSleep() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        BasicUpdate process = mock(BasicUpdate.class);

        String script = strategy.generateIPXEScript(node, process);

        assertThat(script).contains("sleep");
    }

    @Test
    @DisplayName("스크립트 마지막 유효 라인이 'exit' 이다 (로컬 디스크 부팅 전이)")
    void generateIPXEScript_lastLine_isExit() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        BasicUpdate process = mock(BasicUpdate.class);

        String script = strategy.generateIPXEScript(node, process);
        String[] lines = script.strip().split("\n");
        String lastLine = lines[lines.length - 1].trim();

        assertThat(lastLine).isEqualTo("exit");
    }

    @Test
    @DisplayName("스크립트에 kernel/initrd/boot 명령이 없다 (로컬 부팅이므로 네트워크 부팅 불필요)")
    void generateIPXEScript_noNetworkBootCommands() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        BasicUpdate process = mock(BasicUpdate.class);

        String script = strategy.generateIPXEScript(node, process);

        assertThat(script).doesNotContain("kernel ");
        assertThat(script).doesNotContain("initrd ");
        assertThat(script).doesNotContain("\nboot\n");
    }

    @Test
    @DisplayName("스크립트 라인 순서: #!ipxe -> echo -> sleep -> exit")
    void generateIPXEScript_lineOrder_correctSequence() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        BasicUpdate process = mock(BasicUpdate.class);

        String script = strategy.generateIPXEScript(node, process);

        int ipxeIdx = script.indexOf("#!ipxe");
        int echoIdx = script.indexOf("echo");
        int sleepIdx = script.indexOf("sleep");
        int exitIdx = script.indexOf("exit");

        assertThat(ipxeIdx).isLessThan(echoIdx);
        assertThat(echoIdx).isLessThan(sleepIdx);
        assertThat(sleepIdx).isLessThan(exitIdx);
    }
}
