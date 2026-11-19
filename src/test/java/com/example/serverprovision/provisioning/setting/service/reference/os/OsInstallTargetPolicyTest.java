package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.execution.wininstall.catalog.InstallSourceSnapshot;
import com.example.serverprovision.execution.wininstall.catalog.WindowsImageCatalog;
import com.example.serverprovision.management.os.entity.OSMetadata;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.repository.ISORepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 설치 대상 정책 진리표(E4-1-b-2 D-1 · D-2 · D-10) — 판정은 OS 가 속한 계열 검사기가 맡는다.
 * 옵션 조립(UI 차단)과 1단 검사기(서버 가드)가 이 빈 하나를 부른다.
 */
class OsInstallTargetPolicyTest {

    private final WindowsImageCatalog catalog = Mockito.mock(WindowsImageCatalog.class);
    private final OsInstallTargetPolicy policy = new OsInstallTargetPolicy(List.of(
            new RHELInstallationFamilyInspector(Mockito.mock(ISORepository.class)),
            new DebianInstallationFamilyInspector(),
            new WindowsInstallationFamilyInspector(catalog)));

    private static OSMetadata os(OSName name, String version) {
        return OSMetadata.builder().osName(name).osVersion(version).build();
    }

    @Test
    @DisplayName("S23-1 — 지원 RHEL 계열 상수는 허용, 미지원 상수는 계열 검사기보다 먼저 '지원하지 않는 OS', 메이저와 버전이 어긋난 행은 지원 문장, Ubuntu 는 아직 불가")
    void linuxTruthTable() {
        String rhelReason = "Rocky Linux 8, 9, 10, Red Hat Enterprise Linux 8, 9, 10 만 설치할 수 있습니다.";
        assertThat(policy.blockReason(os(OSName.ROCKY_LINUX_8, "8.10"))).isNull();
        assertThat(policy.blockReason(os(OSName.ROCKY_LINUX_9, "9.6"))).isNull();
        assertThat(policy.blockReason(os(OSName.ROCKY_LINUX_10, "10.0"))).isNull();
        assertThat(policy.blockReason(os(OSName.RHEL_9, "9.4"))).isNull();
        assertThat(policy.blockReason(os(OSName.ROCKY_LINUX_10, "11.0"))).isEqualTo(rhelReason);   // 직접 넣은 어긋난 행
        assertThat(policy.blockReason(os(OSName.ROCKY_LINUX, "7.9"))).isEqualTo(OsInstallTargetPolicy.UNSUPPORTED_OS_REASON);
        assertThat(policy.blockReason(os(OSName.CENTOS, "7.9"))).isEqualTo(OsInstallTargetPolicy.UNSUPPORTED_OS_REASON);
        assertThat(policy.blockReason(os(OSName.WINDOWS_SERVER, "2022"))).isEqualTo(OsInstallTargetPolicy.UNSUPPORTED_OS_REASON);
        assertThat(policy.blockReason(os(OSName.UBUNTU, "24.04"))).isEqualTo("Ubuntu 는 아직 설치할 수 없습니다.");
    }

    @Test
    @DisplayName("Windows 는 설치 소스가 준비됐을 때만 허용")
    void windowsDependsOnSource() {
        given(catalog.snapshot()).willReturn(InstallSourceSnapshot.missing());
        assertThat(policy.blockReason(os(OSName.WINDOWS_SERVER_2025, "2025")))
                .isEqualTo(WindowsInstallationFamilyInspector.SOURCE_BLOCK_REASON);

        given(catalog.snapshot()).willReturn(InstallSourceSnapshot.present(List.of(new com.example.serverprovision.execution.wininstall.catalog.WindowsImage(2,
                new com.example.serverprovision.execution.wininstall.vo.WindowsImageName("Windows Server 2025 SERVERSTANDARD"), "Windows Server 2025 Standard", "ServerStandard", "Server", "ko-KR", "10.0.26100.1742")), 1L, Instant.now()));
        assertThat(policy.blockReason(os(OSName.WINDOWS_SERVER_2025, "2025"))).isNull();
    }

    @Test
    @DisplayName("S23-1 — 설치 소스의 install.wim 빌드가 Windows Server 2025(26100)가 아니면 빌드 불일치 사유로 막는다")
    void windowsBlocksWhenSourceBuildDiffers() {
        given(catalog.snapshot()).willReturn(InstallSourceSnapshot.present(List.of(new com.example.serverprovision.execution.wininstall.catalog.WindowsImage(2,
                new com.example.serverprovision.execution.wininstall.vo.WindowsImageName("Windows Server 2022 SERVERSTANDARD"), "Windows Server 2022 Standard", "ServerStandard", "Server", "ko-KR", "10.0.20348.587")), 1L, Instant.now()));
        assertThat(policy.blockReason(os(OSName.WINDOWS_SERVER_2025, "2025")))
                .isEqualTo("설치 소스의 Windows 빌드(10.0.20348.587)가 Windows Server 2025(26100)와 다릅니다. 시스템 자산 대시보드의 'Windows 설치 소스' 영역을 확인해야 합니다.");
    }
}
