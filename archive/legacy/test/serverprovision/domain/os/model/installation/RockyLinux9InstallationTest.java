package com.example.serverprovision.domain.os.model.installation;

import com.example.serverprovision.domain.os.dto.OSEnvironmentDTO;
import com.example.serverprovision.domain.os.model.enums.FileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RockyLinux9InstallationTest {

    private String script;
    private RockyLinux9Installation installation;
    private InstallationContext ctx;

    @BeforeEach
    void setUp() {
        // Partitions: /, /boot, /boot/efi, swap
        List<Partition> partitions = List.of(
                Partition.builder().mountPoint("/").fileSystem(FileSystem.EXT4).sizeInMB(0).isGrow(true).build(),
                Partition.builder().mountPoint("/boot").fileSystem(FileSystem.EXT4).sizeInMB(1024).isGrow(false).build(),
                Partition.builder().mountPoint("/boot/efi").fileSystem(FileSystem.EFI).sizeInMB(1024).isGrow(false).build(),
                Partition.builder().mountPoint("swap").fileSystem(FileSystem.SWAP).sizeInMB(8192).isGrow(false).build()
        );

        RootPassword rootPw = RootPassword.builder().password("TestPass1!").isPasswordEncrypted(false).build();

        OSEnvironmentDTO osEnvDto = new OSEnvironmentDTO(
                1L, null, "server-product-environment", "Server", "Server env", false
        );
        Environment env = new Environment(osEnvDto, List.of());

        Timezone timezone = Timezone.builder().timezone("Asia/Seoul").isUTC(true).build();

        installation = RockyLinux9Installation.builder()
                .partitions(partitions)
                .users(List.of())
                .rootPassword(rootPw)
                .installVersion("9.5")
                .environment(env)
                .timezone(timezone)
                .isKDumpEnabled(true)
                .build();

        ctx = new InstallationContext("test-server", "10.0.0.1", "http://192.168.1.1/rocky9");
        script = installation.getInstallScript(ctx).content();
    }

    @Test
    @DisplayName("반환된 RenderedScript 의 포맷은 KICKSTART")
    void scriptFormatIsKickstart() {
        RenderedScript rendered = installation.getInstallScript(ctx);
        assertThat(rendered.format()).isEqualTo(InstallScriptFormat.KICKSTART);
    }

    @Test
    @DisplayName("스크립트에 #version=RHEL9 헤더 포함")
    void scriptContainsVersionHeader() {
        assertThat(script).contains("#version=RHEL9");
    }

    @Test
    @DisplayName("스크립트에 설치 소스 URL 포함")
    void scriptContainsInstallSourceUrl() {
        assertThat(script).contains("url --url=http://192.168.1.1/rocky9");
    }

    @Test
    @DisplayName("스크립트에 deprecated install 키워드 미포함")
    void scriptNotContainsInstallKeyword() {
        assertThat(script).doesNotContain("\ninstall\n");
        // 첫 줄이 install로 시작하지도 않아야 함
        assertThat(script.startsWith("install\n")).isFalse();
    }

    @Test
    @DisplayName("스크립트에 text 모드 포함")
    void scriptContainsTextMode() {
        assertThat(script).contains("text\n");
    }

    @Test
    @DisplayName("스크립트에 firstboot --disable 포함")
    void scriptContainsFirstbootDisable() {
        assertThat(script).contains("firstboot --disable");
    }

    @Test
    @DisplayName("스크립트에 키보드 및 언어 설정 포함")
    void scriptContainsKeyboardAndLang() {
        assertThat(script).contains("keyboard --xlayouts='us'");
        assertThat(script).contains("lang en_US.UTF-8");
    }

    @Test
    @DisplayName("스크립트에 호스트명 포함 네트워크 설정")
    void scriptContainsNetworkWithHostname() {
        assertThat(script).contains("network --bootproto=dhcp --hostname=test-server");
    }

    @Test
    @DisplayName("hostname이 null이면 --hostname 미포함")
    void scriptContainsNetworkWithoutHostname_whenHostnameNull() {
        InstallationContext nullHostCtx = new InstallationContext(null, "10.0.0.1", "http://192.168.1.1/rocky9");
        String nullHostScript = installation.getInstallScript(nullHostCtx).content();

        assertThat(nullHostScript).contains("network --bootproto=dhcp");
        assertThat(nullHostScript).doesNotContain("--hostname");
    }

    @Test
    @DisplayName("스크립트에 rootpw 포함")
    void scriptContainsRootpw() {
        assertThat(script).contains("rootpw --plaintext TestPass1!");
    }

    @Test
    @DisplayName("스크립트에 timezone --isUtc 포함")
    void scriptContainsTimezoneWithIsUtc() {
        assertThat(script).contains("timezone Asia/Seoul --isUtc");
    }

    @Test
    @DisplayName("스크립트에 zerombr 포함")
    void scriptContainsZerombr() {
        assertThat(script).contains("zerombr\n");
    }

    @Test
    @DisplayName("zerombr가 clearpart 앞에 위치")
    void scriptZerombr_appearsBeforeClearpart() {
        assertThat(script.indexOf("zerombr")).isLessThan(script.indexOf("clearpart"));
    }

    @Test
    @DisplayName("스크립트에 clearpart --all --initlabel 포함")
    void scriptContainsClearpart() {
        assertThat(script).contains("clearpart --all --initlabel");
    }

    @Test
    @DisplayName("스크립트에 루트 파티션 설정 포함")
    void scriptContainsRootPartition() {
        assertThat(script).contains("part / --fstype=\"ext4\" --grow");
    }

    @Test
    @DisplayName("스크립트에 bootloader --append=\"crashkernel=auto\" 포함")
    void scriptContainsBootloaderWithCrashkernel() {
        assertThat(script).contains("bootloader --append=\"crashkernel=auto\"");
    }

    @Test
    @DisplayName("스크립트에 deprecated --location=mbr 미포함")
    void scriptNotContainsLocationMbr() {
        assertThat(script).doesNotContain("--location=mbr");
    }

    @Test
    @DisplayName("스크립트에 패키지 섹션 포함")
    void scriptContainsPackagesSection() {
        assertThat(script).contains("%packages\n");
        assertThat(script).contains("@^server-product-environment");
        assertThat(script).contains("%end");
    }

    @Test
    @DisplayName("KDump 활성화 시 --enable과 따옴표 없는 --reserve-mb=auto")
    void scriptContainsKDumpEnabled_withoutQuotes() {
        assertThat(script).contains("%addon com_redhat_kdump --enable --reserve-mb=auto");
        assertThat(script).doesNotContain("'auto'");
    }

    @Test
    @DisplayName("KDump 비활성화 시 --disable 포함")
    void scriptContainsKDumpDisabled() {
        List<Partition> partitions = List.of(
                Partition.builder().mountPoint("/").fileSystem(FileSystem.EXT4).sizeInMB(0).isGrow(true).build(),
                Partition.builder().mountPoint("/boot").fileSystem(FileSystem.EXT4).sizeInMB(1024).isGrow(false).build(),
                Partition.builder().mountPoint("/boot/efi").fileSystem(FileSystem.EFI).sizeInMB(1024).isGrow(false).build(),
                Partition.builder().mountPoint("swap").fileSystem(FileSystem.SWAP).sizeInMB(8192).isGrow(false).build()
        );
        RootPassword rootPw = RootPassword.builder().password("TestPass1!").isPasswordEncrypted(false).build();
        OSEnvironmentDTO osEnvDto = new OSEnvironmentDTO(1L, null, "server-product-environment", "Server", "desc", false);
        Environment env = new Environment(osEnvDto, List.of());
        Timezone tz = Timezone.builder().timezone("Asia/Seoul").isUTC(true).build();

        RockyLinux9Installation kdumpDisabled = RockyLinux9Installation.builder()
                .partitions(partitions)
                .users(List.of())
                .rootPassword(rootPw)
                .installVersion("9.5")
                .environment(env)
                .timezone(tz)
                .isKDumpEnabled(false)
                .build();

        String disabledScript = kdumpDisabled.getInstallScript(ctx).content();
        assertThat(disabledScript).contains("%addon com_redhat_kdump --disable");
    }

    @Test
    @DisplayName("스크립트가 reboot으로 끝남")
    void scriptEndsWithReboot() {
        assertThat(script).contains("reboot\n");
        assertThat(script.trim()).endsWith("reboot");
    }

    @Test
    @DisplayName("url이 text보다 앞에 위치")
    void scriptOrder_urlBeforeText() {
        assertThat(script.indexOf("url --url")).isLessThan(script.indexOf("text\n"));
    }

    @Test
    @DisplayName("zerombr가 bootloader보다 앞에 위치")
    void scriptOrder_zerombr_beforeBootloader() {
        assertThat(script.indexOf("zerombr")).isLessThan(script.indexOf("bootloader"));
    }

    @Test
    @DisplayName("packages가 kdump보다 앞에 위치")
    void scriptOrder_packagesBeforeKdump() {
        assertThat(script.indexOf("%packages")).isLessThan(script.indexOf("%addon com_redhat_kdump"));
    }
}
