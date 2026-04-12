package com.example.serverprovision.domain.provisioning.model.strategy;

import com.example.serverprovision.application.setting.model.BasicUpdate;
import com.example.serverprovision.application.setting.model.OSInstallation;
import com.example.serverprovision.domain.node.entity.ServerNode;
import com.example.serverprovision.domain.os.dto.OSMetadataDTO;
import com.example.serverprovision.domain.os.model.enums.OSName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OSInstallationStrategy} 보강 단위 테스트.
 * 기존 OSInstallationStrategyTest 에 없는 케이스를 추가로 검증한다.
 */
class OSInstallationStrategyExtendedTest {

    private OSInstallationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new OSInstallationStrategy();
        ReflectionTestUtils.setField(strategy, "serverBaseUrl", "http://192.168.1.100:7777");
        ReflectionTestUtils.setField(strategy, "installSourceUrl", "http://192.168.1.1/rocky9");
    }

    // --- supports() 추가 케이스 ---

    @Test
    @DisplayName("supports: null 입력 시 false 반환 (NPE 방어)")
    void supports_null_returnsFalse() {
        assertThat(strategy.supports(null)).isFalse();
    }

    // --- iPXE 스크립트 라인 순서 검증 ---

    @Test
    @DisplayName("iPXE 스크립트 라인 순서: #!ipxe -> set base-url -> kernel -> initrd -> boot")
    void generateIPXEScript_lineOrder_correctSequence() {
        // given
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(10L);

        OSInstallation process = mockOSInstallation();

        // when
        String script = strategy.generateIPXEScript(node, process);
        String[] lines = script.strip().split("\n");

        // then
        assertThat(lines).hasSizeGreaterThanOrEqualTo(5);
        assertThat(lines[0].trim()).isEqualTo("#!ipxe");
        assertThat(lines[1].trim()).startsWith("set base-url");
        assertThat(lines[2].trim()).startsWith("kernel");
        assertThat(lines[3].trim()).startsWith("initrd");
        assertThat(lines[4].trim()).isEqualTo("boot");
    }

    @Test
    @DisplayName("kernel 라인에 vmlinuz, inst.repo, inst.ks, ip=dhcp 가 모두 포함된다")
    void generateIPXEScript_kernelLine_containsAllParams() {
        // given
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(5L);

        OSInstallation process = mockOSInstallation();

        // when
        String script = strategy.generateIPXEScript(node, process);
        String kernelLine = extractLine(script, "kernel");

        // then
        assertThat(kernelLine).contains("vmlinuz");
        assertThat(kernelLine).contains("inst.repo=${base-url}");
        assertThat(kernelLine).contains("inst.ks=");
        assertThat(kernelLine).contains("ip=dhcp");
    }

    @Test
    @DisplayName("initrd 라인에 initrd.img 경로가 포함된다")
    void generateIPXEScript_initrdLine_containsInitrdImg() {
        // given
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        OSInstallation process = mockOSInstallation();

        // when
        String script = strategy.generateIPXEScript(node, process);
        String initrdLine = extractLine(script, "initrd");

        // then
        assertThat(initrdLine).contains("images/pxeboot/initrd.img");
    }

    // --- installSourceUrl trailing slash 케이스 ---

    @Test
    @DisplayName("installSourceUrl에 trailing slash가 있으면 set base-url에 그대로 반영된다")
    void generateIPXEScript_installSourceUrlTrailingSlash_preservedInBaseUrl() {
        // given
        ReflectionTestUtils.setField(strategy, "installSourceUrl", "http://192.168.1.1/rocky9/");

        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        OSInstallation process = mockOSInstallation();

        // when
        String script = strategy.generateIPXEScript(node, process);

        // then - installSourceUrl은 set base-url에 그대로 들어감
        assertThat(script).contains("set base-url http://192.168.1.1/rocky9/");
    }

    // --- serverBaseUrl null (not blank) 방어 ---

    @Test
    @DisplayName("serverBaseUrl이 null이면 IllegalStateException 발생")
    void generateIPXEScript_nullBaseUrl_throwsException() {
        // given
        ReflectionTestUtils.setField(strategy, "serverBaseUrl", null);

        ServerNode node = mock(ServerNode.class);
        OSInstallation process = mock(OSInstallation.class);

        // when & then
        assertThatThrownBy(() -> strategy.generateIPXEScript(node, process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pxe.server.base-url");
    }

    @Test
    @DisplayName("installSourceUrl이 null이면 IllegalStateException 발생")
    void generateIPXEScript_nullInstallSourceUrl_throwsException() {
        // given
        ReflectionTestUtils.setField(strategy, "installSourceUrl", null);

        ServerNode node = mock(ServerNode.class);
        OSInstallation process = mock(OSInstallation.class);

        // when & then
        assertThatThrownBy(() -> strategy.generateIPXEScript(node, process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pxe.server.install-source-url");
    }

    // --- kickstart URL 정확성 ---

    @Test
    @DisplayName("kickstart URL에 노드 ID가 올바르게 포함된다")
    void generateIPXEScript_kickstartUrl_containsNodeId() {
        // given
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(999L);

        OSInstallation process = mockOSInstallation();

        // when
        String script = strategy.generateIPXEScript(node, process);

        // then
        assertThat(script).contains("inst.ks=http://192.168.1.100:7777/pxe/v1/ks/999");
    }

    // --- 헬퍼 메서드 ---

    private OSInstallation mockOSInstallation() {
        OSMetadataDTO osMetadata = OSMetadataDTO.builder()
                .id(1L)
                .osName(OSName.ROCKY_LINUX)
                .osVersion("9.3")
                .isoMountPath("/mnt/rocky9")
                .isEnabled(true)
                .build();

        OSInstallation process = mock(OSInstallation.class);
        when(process.getOsMetadata()).thenReturn(osMetadata);
        return process;
    }

    private String extractLine(String script, String keyword) {
        for (String line : script.split("\n")) {
            if (line.trim().startsWith(keyword)) {
                return line;
            }
        }
        throw new AssertionError("'" + keyword + "' 로 시작하는 라인을 찾을 수 없음: " + script);
    }
}
