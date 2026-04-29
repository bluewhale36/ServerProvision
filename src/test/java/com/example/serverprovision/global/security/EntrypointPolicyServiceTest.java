package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.exception.EntrypointInvalidException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EntrypointPolicyServiceTest {

    private final EntrypointPolicyService svc = new EntrypointPolicyService();

    @Test
    @DisplayName("null / 빈 입력 → null 반환 (진입점 없음 허용)")
    void nullOrBlank(@TempDir Path tmp) {
        assertThat(svc.validateAndNormalize(tmp, null)).isNull();
        assertThat(svc.validateAndNormalize(tmp, "")).isNull();
        assertThat(svc.validateAndNormalize(tmp, "   ")).isNull();
    }

    @Test
    @DisplayName("정상 상대경로 → 정규화된 forward-slash 표기 반환")
    void happy(@TempDir Path tmp) {
        assertThat(svc.validateAndNormalize(tmp, "install.sh")).isEqualTo("install.sh");
        assertThat(svc.validateAndNormalize(tmp, "bin\\setup.exe")).isEqualTo("bin/setup.exe");
        assertThat(svc.validateAndNormalize(tmp, "firmware/flash.nsh")).isEqualTo("firmware/flash.nsh");
    }

    @Test
    @DisplayName(".. 시그먼트 거절")
    void rejectDotDot(@TempDir Path tmp) {
        assertThatThrownBy(() -> svc.validateAndNormalize(tmp, "../etc/passwd"))
                .isInstanceOf(EntrypointInvalidException.class);
        assertThatThrownBy(() -> svc.validateAndNormalize(tmp, "bin/../../etc"))
                .isInstanceOf(EntrypointInvalidException.class);
    }

    @Test
    @DisplayName("절대경로 거절")
    void rejectAbsolute(@TempDir Path tmp) {
        assertThatThrownBy(() -> svc.validateAndNormalize(tmp, "/etc/passwd"))
                .isInstanceOf(EntrypointInvalidException.class);
        assertThatThrownBy(() -> svc.validateAndNormalize(tmp, "C:\\Windows\\system"))
                .isInstanceOf(EntrypointInvalidException.class);
    }

    @Test
    @DisplayName("null byte 거절")
    void rejectNullByte(@TempDir Path tmp) {
        assertThatThrownBy(() -> svc.validateAndNormalize(tmp, "install\0sh"))
                .isInstanceOf(EntrypointInvalidException.class);
    }

    @Test
    @DisplayName("길이 512 초과 거절")
    void rejectTooLong(@TempDir Path tmp) {
        String longInput = "a".repeat(513);
        assertThatThrownBy(() -> svc.validateAndNormalize(tmp, longInput))
                .isInstanceOf(EntrypointInvalidException.class);
    }
}
