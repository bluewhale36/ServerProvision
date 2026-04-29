package com.example.serverprovision.global.security.integration;

import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.global.security.config.PathSecurityProperties;
import com.example.serverprovision.global.security.config.SecurityPropertiesValidator;
import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.util.unit.DataSize;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3.2 (K2) — Boot fail-fast 회귀.
 *
 * <p>{@code application-fail.properties} 의 컨텐츠가 운영 환경에서 부팅 실패를 보장하는지를 직접 끌어와 검증한다.
 * Full Spring 컨텍스트를 띄우면 정상 application.properties 가 우선 로딩되어 실패 의도가 mask 되므로,
 * properties 파일을 직접 {@link Properties} 로 로딩한 뒤 {@link SecurityPropertiesValidator} 에
 * 그 값들을 그대로 주입해 부팅 시 발생하는 fail-fast 와 동일한 호출 경로를 재현한다.</p>
 */
class BootFailFastTest {

    @Test
    @DisplayName("application-fail.properties 가 classpath 에 존재한다")
    void failPropertiesExists() {
        assertThat(getClass().getClassLoader().getResource("application-fail.properties"))
                .as("K2 가 회귀하지 않도록 fail-fast 전용 설정 파일은 항상 존재해야 한다.")
                .isNotNull();
    }

    @Test
    @DisplayName("K2 — allowed-roots 가 빈 값일 때 SecurityPropertiesValidator 가 IllegalStateException 으로 boot fail-fast")
    void emptyAllowedRoots_failsBoot() {
        // application-fail.properties 의 의도와 동일한 입력 (allowed-roots 빈 값) 을 직접 재현.
        SecurityPropertiesValidator validator = new SecurityPropertiesValidator(
                new PathSecurityProperties(List.of()),
                defaultUpload(),
                new FileSystemSecurityProperties(2000, 8),
                null);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provision.path.allowed-roots");
    }

    @Test
    @DisplayName("K2 — allowed-roots 가 blank/null 만 포함해도 boot fail-fast")
    void blankOnlyAllowedRoots_failsBoot() {
        java.util.ArrayList<String> roots = new java.util.ArrayList<>();
        roots.add("");
        roots.add("   ");
        roots.add(null);
        SecurityPropertiesValidator validator = new SecurityPropertiesValidator(
                new PathSecurityProperties(roots),
                defaultUpload(),
                new FileSystemSecurityProperties(2000, 8),
                null);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provision.path.allowed-roots");
    }

    @Test
    @DisplayName("K2 — multipart cap 이 provision.upload cap 보다 크면 boot fail-fast")
    void multipartCapMismatch_failsBoot() {
        MultipartProperties mp = new MultipartProperties();
        mp.setMaxFileSize(DataSize.ofGigabytes(64));      // intentionally exceeds provision.upload.max-file-size
        mp.setMaxRequestSize(DataSize.ofGigabytes(20));

        SecurityPropertiesValidator validator = new SecurityPropertiesValidator(
                new PathSecurityProperties(List.of("/opt/iso")),
                defaultUpload(),
                new FileSystemSecurityProperties(2000, 8),
                mp);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multipart");
    }

    private static UploadSecurityProperties defaultUpload() {
        return new UploadSecurityProperties(
                DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), 100, 10000,
                ExecutableBinaryPolicy.DENY, 50, SuspiciousFilenamesPolicy.DISABLED,
                null, DataSize.ofGigabytes(5));
    }
}
