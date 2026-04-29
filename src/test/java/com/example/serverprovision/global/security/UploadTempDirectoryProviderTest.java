package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3.4 (P0-K9) — 업로드 임시 파일 0600 회귀 가드.
 *
 * <p>multi-user 호스트에서 임시 파일 권한이 group/world readable 로 회귀하면 업로드 중 파일이
 * 다른 사용자에게 노출된다. {@link UploadTempDirectoryProvider} 가 0600 (owner-only) 을 강제하는지
 * POSIX 환경에서 직접 검증한다.</p>
 */
class UploadTempDirectoryProviderTest {

    private UploadSecurityProperties propsWithTempDir(String tempDir) {
        return new UploadSecurityProperties(
                DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 5000,
                ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED,
                tempDir, DataSize.ofGigabytes(20)
        );
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("S3.4 K9 — Linux 환경에서 임시 파일은 0600 (owner read/write 만)")
    void tempFile_hasOwnerOnlyPermissions(@TempDir Path tempDir) throws Exception {
        UploadTempDirectoryProvider provider = new UploadTempDirectoryProvider(
                propsWithTempDir(tempDir.toString()));

        Path tempFile = provider.createTempFile("upload-", ".zip");
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(tempFile);
            assertThat(perms)
                    .containsExactlyInAnyOrder(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE)
                    .doesNotContain(
                            PosixFilePermission.GROUP_READ,
                            PosixFilePermission.GROUP_WRITE,
                            PosixFilePermission.OTHERS_READ,
                            PosixFilePermission.OTHERS_WRITE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("S3.4 K9 — group/world readable 로 회귀 시 어설션 실패 (회귀 차단 의도 서술)")
    void tempFile_groupReadable_failsAssertion(@TempDir Path tempDir) throws Exception {
        UploadTempDirectoryProvider provider = new UploadTempDirectoryProvider(
                propsWithTempDir(tempDir.toString()));
        Path tempFile = provider.createTempFile("upload-", ".zip");
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(tempFile);
            // 본 테스트의 핵심 의도 — 0600 가 깨지면 group/world bit 가 추가된다.
            // 회귀 시 본 어설션은 실패하여 PR 단계에서 잡힌다.
            assertThat(perms).doesNotContain(
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_WRITE,
                    PosixFilePermission.OTHERS_EXECUTE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
