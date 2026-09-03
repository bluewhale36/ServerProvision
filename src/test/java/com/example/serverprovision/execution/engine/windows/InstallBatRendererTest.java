package com.example.serverprovision.execution.engine.windows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-a-3 CP4 — install.bat 렌더: UNC 호스트 추출 · 접속 줄 · ASCII · 배치 금지 문자. */
class InstallBatRendererTest {

    @Test
    @DisplayName("호스트 추출 — \\\\host\\share 와 //host/share 모두 host")
    void hostOf_uncForms() {
        assertThat(InstallBatRenderer.hostOf("\\\\10.0.0.7\\win2025")).isEqualTo("10.0.0.7");
        assertThat(InstallBatRenderer.hostOf("//spv.local/win2025")).isEqualTo("spv.local");
        assertThat(InstallBatRenderer.hostOf("  \\\\host\\share\\sub ")).isEqualTo("host");
    }

    @Test
    @DisplayName("렌더 — ping 대상은 호스트, net use 는 UNC · 계정 · 인용된 비밀번호 · 자리표시자 0 · 전부 ASCII")
    void render_fillsConnectionLine() {
        String bat = InstallBatRenderer.render("\\\\10.0.0.7\\win2025", "deploy", "s3cret-9x");

        assertThat(bat).contains("ping -n 2 10.0.0.7 >nul")
                .contains("net use N: \\\\10.0.0.7\\win2025 /user:deploy \"s3cret-9x\" && goto mounted")
                .contains("N:\\sources\\setup.exe /unattend:X:\\Windows\\System32\\autounattend.xml")
                .doesNotContain("__");
        assertThat(bat.chars().allMatch(c -> c < 0x80)).isTrue();
    }

    @Test
    @DisplayName("배치 안전 문자 — 인쇄 가능 ASCII 에서 \" % ^ & | < > ( ) ! 와 공백 · 비ASCII · 빈 값 제외")
    void isBatchSafe_table() {
        assertThat(InstallBatRenderer.isBatchSafe("abcXYZ123-_.~@#$*+=:;,?/[]{}")).isTrue();
        assertThat(InstallBatRenderer.isBatchSafe("a b")).isFalse();
        assertThat(InstallBatRenderer.isBatchSafe("p%d")).isFalse();
        assertThat(InstallBatRenderer.isBatchSafe("a\"b")).isFalse();
        assertThat(InstallBatRenderer.isBatchSafe("a!b")).isFalse();
        assertThat(InstallBatRenderer.isBatchSafe("a&b")).isFalse();
        assertThat(InstallBatRenderer.isBatchSafe("비밀")).isFalse();
        assertThat(InstallBatRenderer.isBatchSafe("")).isFalse();
        assertThat(InstallBatRenderer.isBatchSafe(null)).isFalse();
    }

    @Test
    @DisplayName("금지 문자 비밀번호로 렌더 요청 = 준비도를 우회한 버그 → IllegalArgumentException")
    void render_rejectsUnsafePassword() {
        assertThatThrownBy(() -> InstallBatRenderer.render("\\\\h\\s", "deploy", "pa%ss"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
