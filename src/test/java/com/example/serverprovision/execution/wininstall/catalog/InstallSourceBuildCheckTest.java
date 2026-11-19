package com.example.serverprovision.execution.wininstall.catalog;

import com.example.serverprovision.execution.wininstall.vo.WindowsImageName;
import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** S23-1 — 설치 소스 빌드와 정의서 OS 의 Windows Server 버전 대조. 정의서 검사기와 실행 게이트가 같은 판정을 쓴다. */
class InstallSourceBuildCheckTest {

    private static InstallSourceSnapshot sourceWithBuild(String build) {
        return InstallSourceSnapshot.present(List.of(new WindowsImage(2, new WindowsImageName("Windows Server SERVERSTANDARD"),
                "Windows Server Standard", "ServerStandard", "Server", "ko-KR", build)), 1L, Instant.now());
    }

    @Test
    @DisplayName("빌드가 맞으면 empty")
    void matchingBuild_passes() {
        assertThat(InstallSourceBuildCheck.blockReason(OSName.WINDOWS_SERVER_2025, sourceWithBuild("10.0.26100.1742"))).isEmpty();
    }

    @Test
    @DisplayName("빌드가 다르면 사유: 실제 빌드, OS 라벨, 기대 빌드, 대시보드 안내")
    void differentBuild_blocks() {
        assertThat(InstallSourceBuildCheck.blockReason(OSName.WINDOWS_SERVER_2025, sourceWithBuild("10.0.20348.587")))
                .contains("설치 소스의 Windows 빌드(10.0.20348.587)가 Windows Server 2025(26100)와 다릅니다. "
                        + "시스템 자산 대시보드의 'Windows 설치 소스' 영역을 확인해야 합니다.");
    }

    @Test
    @DisplayName("빌드를 읽지 못하면 '알 수 없음' 으로 막는다")
    void unreadableBuild_blocks() {
        assertThat(InstallSourceBuildCheck.blockReason(OSName.WINDOWS_SERVER_2025, sourceWithBuild("")))
                .hasValueSatisfying(reason -> assertThat(reason).contains("빌드(알 수 없음)"));
    }

    @Test
    @DisplayName("판정하지 않는 경우: OS 모름, 소스 미준비(소스 사유가 먼저), 미지원 OS(정책이 먼저)")
    void notJudged() {
        assertThat(InstallSourceBuildCheck.blockReason(null, sourceWithBuild("10.0.20348.587"))).isEmpty();
        assertThat(InstallSourceBuildCheck.blockReason(OSName.WINDOWS_SERVER_2025, InstallSourceSnapshot.missing())).isEmpty();
        assertThat(InstallSourceBuildCheck.blockReason(OSName.WINDOWS_SERVER, sourceWithBuild("10.0.20348.587"))).isEmpty();
    }
}
