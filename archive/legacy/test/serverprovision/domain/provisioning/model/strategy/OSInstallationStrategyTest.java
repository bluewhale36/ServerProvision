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
 * {@link OSInstallationStrategy} 단위 테스트.
 */
class OSInstallationStrategyTest {

    private OSInstallationStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new OSInstallationStrategy();
        ReflectionTestUtils.setField(strategy, "serverBaseUrl", "http://192.168.1.100:7777");
        ReflectionTestUtils.setField(strategy, "installSourceUrl", "http://192.168.1.1/rocky9");
    }

    @Test
    @DisplayName("OSInstallation 타입 프로세스를 지원한다")
    void supports_osInstallation_returnsTrue() {
        OSInstallation process = mock(OSInstallation.class);
        assertThat(strategy.supports(process)).isTrue();
    }

    @Test
    @DisplayName("OSInstallation 이 아닌 프로세스는 지원하지 않는다")
    void supports_otherProcess_returnsFalse() {
        com.example.serverprovision.application.setting.model.BasicUpdate process =
                mock(com.example.serverprovision.application.setting.model.BasicUpdate.class);
        assertThat(strategy.supports(process)).isFalse();
    }

    @Test
    @DisplayName("RHEL 계열 유효 입력으로 Kickstart iPXE 스크립트를 생성한다")
    void generateIPXEScript_rhel_returnsKickstartScript() {
        // given
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(42L);

        OSMetadataDTO osMetadata = OSMetadataDTO.builder()
                .id(1L)
                .osName(OSName.ROCKY_LINUX)
                .osVersion("9.3")
                .isoMountPath("/mnt/rocky9")
                .isEnabled(true)
                .build();

        OSInstallation process = mock(OSInstallation.class);
        when(process.getOsMetadata()).thenReturn(osMetadata);

        // when
        String script = strategy.generateIPXEScript(node, process);

        // then — RHEL 규약: images/pxeboot/vmlinuz + inst.ks=
        assertThat(script).contains("#!ipxe");
        assertThat(script).contains("kernel");
        assertThat(script).contains("images/pxeboot/vmlinuz");
        assertThat(script).contains("inst.ks=http://192.168.1.100:7777/pxe/v1/install/42");
        assertThat(script).contains("inst.repo=${base-url}");
        assertThat(script).contains("initrd");
        assertThat(script).contains("images/pxeboot/initrd.img");
        assertThat(script).contains("boot");
        assertThat(script).contains("http://192.168.1.1/rocky9");
        // Ubuntu 전용 토큰은 들어가지 않음
        assertThat(script).doesNotContain("autoinstall");
        assertThat(script).doesNotContain("nocloud-net");
    }

    @Test
    @DisplayName("Ubuntu 계열 유효 입력으로 autoinstall (nocloud-net) iPXE 스크립트를 생성한다")
    void generateIPXEScript_ubuntu_returnsAutoinstallScript() {
        // given
        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(7L);

        OSMetadataDTO osMetadata = OSMetadataDTO.builder()
                .id(2L)
                .osName(OSName.UBUNTU)
                .osVersion("22.04.5")
                .isoMountPath("/mnt/ubuntu2204")
                .isEnabled(true)
                .build();

        OSInstallation process = mock(OSInstallation.class);
        when(process.getOsMetadata()).thenReturn(osMetadata);

        // when
        String script = strategy.generateIPXEScript(node, process);

        // then — Ubuntu 규약: casper/vmlinuz + autoinstall "ds=nocloud-net;s=<url>/"
        assertThat(script).contains("#!ipxe");
        assertThat(script).contains("casper/vmlinuz");
        assertThat(script).contains("casper/initrd");
        assertThat(script).contains("autoinstall");
        assertThat(script).contains("\"ds=nocloud-net;s=http://192.168.1.100:7777/pxe/v1/install/7/\"");
        assertThat(script).contains("ip=dhcp");
        assertThat(script).contains("---"); // Subiquity 커널-런타임 인자 구분자
        // RHEL 전용 토큰은 들어가지 않음
        assertThat(script).doesNotContain("inst.ks=");
        assertThat(script).doesNotContain("inst.repo=");
        assertThat(script).doesNotContain("images/pxeboot/vmlinuz");
    }

    @Test
    @DisplayName("Windows 계열 입력 시 UnsupportedOperationException 발생 (Phase 10 placeholder)")
    void generateIPXEScript_windows_throwsUnsupported() {
        // given
        ServerNode node = mock(ServerNode.class);
        OSMetadataDTO osMetadata = OSMetadataDTO.builder()
                .id(3L)
                .osName(OSName.WINDOWS_SERVER)
                .osVersion("2022")
                .isoMountPath("/mnt/win2022")
                .isEnabled(true)
                .build();
        OSInstallation process = mock(OSInstallation.class);
        when(process.getOsMetadata()).thenReturn(osMetadata);

        // when & then
        assertThatThrownBy(() -> strategy.generateIPXEScript(node, process))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Windows");
    }

    @Test
    @DisplayName("serverBaseUrl 미설정 시 IllegalStateException 발생")
    void generateIPXEScript_missingBaseUrl_throwsException() {
        // given
        ReflectionTestUtils.setField(strategy, "serverBaseUrl", "");

        ServerNode node = mock(ServerNode.class);
        OSInstallation process = mock(OSInstallation.class);

        // when & then
        assertThatThrownBy(() -> strategy.generateIPXEScript(node, process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pxe.server.base-url");
    }

    @Test
    @DisplayName("serverBaseUrl 에 trailing slash 가 있어도 이중 슬래시가 발생하지 않는다")
    void generateIPXEScript_trailingSlash_normalizedUrl() {
        // given
        ReflectionTestUtils.setField(strategy, "serverBaseUrl", "http://192.168.1.100:7777/");

        ServerNode node = mock(ServerNode.class);
        when(node.getId()).thenReturn(1L);

        OSInstallation process = mock(OSInstallation.class);
        when(process.getOsMetadata()).thenReturn(OSMetadataDTO.builder()
                .id(1L).osName(OSName.ROCKY_LINUX).osVersion("9.3")
                .isoMountPath("/mnt/rocky9").isEnabled(true).build());

        // when
        String script = strategy.generateIPXEScript(node, process);

        // then
        assertThat(script).contains("inst.ks=http://192.168.1.100:7777/pxe/v1/install/1");
        assertThat(script).doesNotContain("7777//pxe");
    }

    @Test
    @DisplayName("installSourceUrl 미설정 시 IllegalStateException 발생")
    void generateIPXEScript_missingInstallSourceUrl_throwsException() {
        // given
        ReflectionTestUtils.setField(strategy, "installSourceUrl", "");

        ServerNode node = mock(ServerNode.class);
        OSInstallation process = mock(OSInstallation.class);

        // when & then
        assertThatThrownBy(() -> strategy.generateIPXEScript(node, process))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pxe.server.install-source-url");
    }
}
