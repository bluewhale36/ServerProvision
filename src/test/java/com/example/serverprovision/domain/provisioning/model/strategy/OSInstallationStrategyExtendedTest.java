package com.example.serverprovision.domain.provisioning.model.strategy;

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
 * 기존 {@link OSInstallationStrategyTest} 에 없는 케이스를 추가로 검증한다 — 주로 iPXE
 * 스크립트 라인 구조, 패밀리별 커널 경로, URL trailing slash 동작.
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

    // --- RHEL iPXE 스크립트 라인 구조 ---

    @Test
    @DisplayName("RHEL iPXE 스크립트 라인 순서: #!ipxe → set base-url → kernel → initrd → boot")
    void generateIPXEScript_rhelLineOrder_correctSequence() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(10L);

        OSInstallation process = mockRhelOSInstallation();

        String script = strategy.generateIPXEScript(node, process);
        String[] lines = script.strip().split("\n");

        assertThat(lines).hasSizeGreaterThanOrEqualTo(5);
        assertThat(lines[0].trim()).isEqualTo("#!ipxe");
        assertThat(lines[1].trim()).startsWith("set base-url");
        assertThat(lines[2].trim()).startsWith("kernel");
        assertThat(lines[3].trim()).startsWith("initrd");
        assertThat(lines[4].trim()).isEqualTo("boot");
    }

    @Test
    @DisplayName("RHEL kernel 라인에 vmlinuz, inst.repo, inst.ks, ip=dhcp 가 모두 포함된다")
    void generateIPXEScript_rhelKernelLine_containsAllParams() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(5L);

        OSInstallation process = mockRhelOSInstallation();

        String script = strategy.generateIPXEScript(node, process);
        String kernelLine = extractLine(script, "kernel");

        assertThat(kernelLine).contains("vmlinuz");
        assertThat(kernelLine).contains("inst.repo=${base-url}");
        assertThat(kernelLine).contains("inst.ks=");
        assertThat(kernelLine).contains("ip=dhcp");
    }

    @Test
    @DisplayName("RHEL initrd 라인에 images/pxeboot/initrd.img 경로가 포함된다")
    void generateIPXEScript_rhelInitrdLine_containsInitrdImg() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        OSInstallation process = mockRhelOSInstallation();

        String script = strategy.generateIPXEScript(node, process);
        String initrdLine = extractLine(script, "initrd");

        assertThat(initrdLine).contains("images/pxeboot/initrd.img");
    }

    // --- Ubuntu iPXE 스크립트 라인 구조 ---

    @Test
    @DisplayName("Ubuntu kernel 라인에 casper/vmlinuz, autoinstall, ds=nocloud-net, 구분자 --- 포함")
    void generateIPXEScript_ubuntuKernelLine_containsAllParams() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(11L);

        OSInstallation process = mockUbuntuOSInstallation();

        String script = strategy.generateIPXEScript(node, process);
        String kernelLine = extractLine(script, "kernel");

        assertThat(kernelLine).contains("casper/vmlinuz");
        assertThat(kernelLine).contains("autoinstall");
        assertThat(kernelLine).contains("ds=nocloud-net;s=");
        assertThat(kernelLine).contains("ip=dhcp");
        assertThat(kernelLine).contains("---");
    }

    @Test
    @DisplayName("Ubuntu nocloud-net URL 말미에 '/' 가 반드시 포함된다 (nocloud 디렉터리 인식 필수)")
    void generateIPXEScript_ubuntuNocloudUrl_endsWithSlash() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(42L);

        OSInstallation process = mockUbuntuOSInstallation();

        String script = strategy.generateIPXEScript(node, process);

        // 주의: 세미콜론을 iPXE 명령 구분자로 오인하지 않도록 쌍따옴표 필수.
        assertThat(script).contains("\"ds=nocloud-net;s=http://192.168.1.100:7777/pxe/v1/install/42/\"");
    }

    @Test
    @DisplayName("Ubuntu initrd 라인에 casper/initrd 경로가 포함된다")
    void generateIPXEScript_ubuntuInitrdLine_containsCasperInitrd() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(8L);

        OSInstallation process = mockUbuntuOSInstallation();

        String script = strategy.generateIPXEScript(node, process);
        String initrdLine = extractLine(script, "initrd");

        assertThat(initrdLine).contains("casper/initrd");
    }

    // --- installSourceUrl trailing slash 케이스 ---

    @Test
    @DisplayName("installSourceUrl 에 trailing slash 가 있으면 set base-url 에 그대로 반영된다")
    void generateIPXEScript_installSourceUrlTrailingSlash_preservedInBaseUrl() {
        ReflectionTestUtils.setField(strategy, "installSourceUrl", "http://192.168.1.1/rocky9/");

        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        OSInstallation process = mockRhelOSInstallation();

        String script = strategy.generateIPXEScript(node, process);

        assertThat(script).contains("set base-url http://192.168.1.1/rocky9/");
    }

    // --- serverBaseUrl null (not blank) 방어 ---

    @Test
    @DisplayName("serverBaseUrl 이 null 이면 IllegalStateException 발생")
    void generateIPXEScript_nullBaseUrl_throwsException() {
        ReflectionTestUtils.setField(strategy, "serverBaseUrl", null);

        ServerNode node = mock(ServerNode.class);
        OSInstallation process = mock(OSInstallation.class);

        assertThatThrownBy(() -> strategy.generateIPXEScript(node, process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pxe.server.base-url");
    }

    @Test
    @DisplayName("installSourceUrl 이 null 이면 IllegalStateException 발생")
    void generateIPXEScript_nullInstallSourceUrl_throwsException() {
        ReflectionTestUtils.setField(strategy, "installSourceUrl", null);

        ServerNode node = mock(ServerNode.class);
        OSInstallation process = mock(OSInstallation.class);

        assertThatThrownBy(() -> strategy.generateIPXEScript(node, process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pxe.server.install-source-url");
    }

    // --- install URL 정확성 ---

    @Test
    @DisplayName("RHEL install URL 에 노드 ID 가 올바르게 포함된다")
    void generateIPXEScript_rhelInstallUrl_containsNodeId() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(999L);

        OSInstallation process = mockRhelOSInstallation();

        String script = strategy.generateIPXEScript(node, process);

        assertThat(script).contains("inst.ks=http://192.168.1.100:7777/pxe/v1/install/999");
    }

    @Test
    @DisplayName("Ubuntu install URL 에 노드 ID 가 올바르게 포함된다 (slash 유지)")
    void generateIPXEScript_ubuntuInstallUrl_containsNodeIdAndSlash() {
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(123L);

        OSInstallation process = mockUbuntuOSInstallation();

        String script = strategy.generateIPXEScript(node, process);

        assertThat(script).contains("s=http://192.168.1.100:7777/pxe/v1/install/123/");
    }

    // --- 헬퍼 메서드 ---

    private OSInstallation mockRhelOSInstallation() {
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

    private OSInstallation mockUbuntuOSInstallation() {
        OSMetadataDTO osMetadata = OSMetadataDTO.builder()
                .id(2L)
                .osName(OSName.UBUNTU)
                .osVersion("22.04.5")
                .isoMountPath("/mnt/ubuntu2204")
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
