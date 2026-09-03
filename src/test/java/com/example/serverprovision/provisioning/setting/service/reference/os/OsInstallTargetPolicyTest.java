package com.example.serverprovision.provisioning.setting.service.reference.os;

import com.example.serverprovision.management.os.enums.OSName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** E4-1-a-2 CP4 — 설치 대상 정책 진리표(계열 × 소스 준비). UI tooltip · 서버 가드 · plan 데모가 같은 판정을 본다. */
class OsInstallTargetPolicyTest {

    @Test
    @DisplayName("리눅스 계열은 소스 준비 여부와 무관하게 차단, Windows 는 소스 준비됐을 때만 허용")
    void truthTable() {
        assertThat(OsInstallTargetPolicy.blockReason(OSName.ROCKY_LINUX, true)).isEqualTo(OsInstallTargetPolicy.LINUX_BLOCK_REASON);
        assertThat(OsInstallTargetPolicy.blockReason(OSName.UBUNTU, false)).isEqualTo(OsInstallTargetPolicy.LINUX_BLOCK_REASON);
        assertThat(OsInstallTargetPolicy.blockReason(OSName.CENTOS, true)).isEqualTo(OsInstallTargetPolicy.LINUX_BLOCK_REASON);
        assertThat(OsInstallTargetPolicy.blockReason(OSName.WINDOWS_SERVER, false)).isEqualTo(OsInstallTargetPolicy.SOURCE_BLOCK_REASON);
        assertThat(OsInstallTargetPolicy.blockReason(OSName.WINDOWS_SERVER, true)).isNull();
        assertThat(OsInstallTargetPolicy.blockReason(OSName.WINDOWS, true)).isNull();
    }
}
