package com.example.serverprovision.global.security.config;

import com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3.1 (C1) 보강 검증 — boot 시점에 위험한 allowed-roots 입력을 차단하는지.
 */
class SecurityPropertiesValidatorTest {

    private SecurityPropertiesValidator validatorWith(List<String> roots) {
        PathSecurityProperties path = new PathSecurityProperties(roots);
        UploadSecurityProperties upload = new UploadSecurityProperties(
                DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 10000,
                ExecutableBinaryPolicy.DENY, 50, SuspiciousFilenamesPolicy.DISABLED,
                null, DataSize.ofGigabytes(20));
        FileSystemSecurityProperties fs = new FileSystemSecurityProperties(2000, 8);
        return new SecurityPropertiesValidator(path, upload, fs, null);
    }

    @Test
    @DisplayName("정상 — 절대경로 root 통과")
    void happy() {
        assertThatCode(() -> validatorWith(List.of("/opt/iso", "/opt/bios")).validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("빈 list / null / blank 만 → IllegalStateException (S3 fail-fast)")
    void blank_fails() {
        assertThatThrownBy(() -> validatorWith(List.of()).validate())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> validatorWith(List.of("", "  ", " ")).validate())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("S3.1 (C1) — root \"/\" → IllegalStateException (침투 #5 차단)")
    void rootSlash_rejected() {
        assertThatThrownBy(() -> validatorWith(List.of("/")).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("root");
    }

    @Test
    @DisplayName("S3.1 (C1) — 상대경로 → IllegalStateException (침투 #3 차단)")
    void relative_rejected() {
        assertThatThrownBy(() -> validatorWith(List.of("./relative")).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("절대경로");
    }

    @Test
    @DisplayName("S3.1 (C1) — 정규형 아님 (.. 시그먼트) → IllegalStateException")
    void notNormalized_rejected() {
        assertThatThrownBy(() -> validatorWith(List.of("/opt/../etc")).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("정규화");
    }

    @Test
    @DisplayName("C8 — Spring multipart cap > provision.upload cap → IllegalStateException")
    void multipartCapTooLarge_rejected() {
        PathSecurityProperties path = new PathSecurityProperties(List.of("/opt/iso"));
        UploadSecurityProperties upload = new UploadSecurityProperties(
                DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 10000,
                ExecutableBinaryPolicy.DENY, 50, SuspiciousFilenamesPolicy.DISABLED,
                null, DataSize.ofGigabytes(20));
        FileSystemSecurityProperties fs = new FileSystemSecurityProperties(2000, 8);

        org.springframework.boot.servlet.autoconfigure.MultipartProperties mp =
                new org.springframework.boot.servlet.autoconfigure.MultipartProperties();
        mp.setMaxFileSize(DataSize.ofGigabytes(64)); // 64GB > 5GB
        mp.setMaxRequestSize(DataSize.ofGigabytes(20));

        assertThatThrownBy(() -> new SecurityPropertiesValidator(path, upload, fs, mp).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multipart");
    }

    @Test
    @DisplayName("C8 — Spring multipart cap = provision.upload cap → 통과")
    void multipartCapSync_pass() {
        PathSecurityProperties path = new PathSecurityProperties(List.of("/opt/iso"));
        UploadSecurityProperties upload = new UploadSecurityProperties(
                DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 10000,
                ExecutableBinaryPolicy.DENY, 50, SuspiciousFilenamesPolicy.DISABLED,
                null, DataSize.ofGigabytes(20));
        FileSystemSecurityProperties fs = new FileSystemSecurityProperties(2000, 8);

        org.springframework.boot.servlet.autoconfigure.MultipartProperties mp =
                new org.springframework.boot.servlet.autoconfigure.MultipartProperties();
        mp.setMaxFileSize(DataSize.ofGigabytes(5));
        mp.setMaxRequestSize(DataSize.ofGigabytes(20));

        assertThatCode(() -> new SecurityPropertiesValidator(path, upload, fs, mp).validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("S3.3 (K3) — trailing slash 가 포함된 root 도 정규화 후 통과")
    void trailingSlashRoot_normalizedAndPassed() {
        // Rocky Linux 9.x POSIX 가정 — "/opt/iso/" 입력은 trim 후 "/opt/iso" 와 동등 처리되어 통과해야 한다.
        assertThatCode(() -> validatorWith(List.of("/opt/iso/")).validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("S3.3 (K3) — 다중 trailing slash 도 정규화 후 통과")
    void multiTrailingSlashRoot_normalizedAndPassed() {
        // "/opt/iso///" 같이 다중 슬래시도 replaceAll("/+$", "") 가 모두 제거하므로 정상 통과.
        assertThatCode(() -> validatorWith(List.of("/opt/iso///")).validate())
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정상 + 빈 entry 섞임 → 빈 entry 만 무시하고 통과")
    void mixed_blank_entries() {
        // List.of 는 null 거절 — ArrayList 로 null 포함.
        java.util.List<String> roots = new java.util.ArrayList<>();
        roots.add("/opt/iso");
        roots.add("");
        roots.add("/opt/bios");
        roots.add(null);
        assertThatCode(() -> validatorWith(roots).validate())
                .doesNotThrowAnyException();
    }
}
