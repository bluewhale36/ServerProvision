package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.PathSecurityProperties;
import com.example.serverprovision.global.security.exception.PathOutsideAllowedRootsException;
import com.example.serverprovision.global.security.exception.PathTraversalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PathPolicyServiceTest {

    private PathPolicyService serviceWithRoots(String... roots) {
        return new PathPolicyService(new PathSecurityProperties(List.of(roots)));
    }

    @Test
    @DisplayName("정상 경로 — allowlist root 하위는 통과 + 정규화 절대경로 반환")
    void happy_underRoot(@TempDir Path tmp) {
        PathPolicyService svc = serviceWithRoots(tmp.toString());
        Path ok = svc.assertWritablePath(tmp.resolve("driver/intel/1.0").toString());
        assertThat(ok.toString()).startsWith(tmp.toString());
    }

    @Test
    @DisplayName("정상 — root 자체 입력")
    void happy_rootItself(@TempDir Path tmp) {
        PathPolicyService svc = serviceWithRoots(tmp.toString());
        assertThat(svc.assertWritablePath(tmp.toString())).isEqualTo(tmp.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("allowlist 밖 절대경로 → PathOutsideAllowedRoots")
    void outside_root(@TempDir Path tmp) {
        PathPolicyService svc = serviceWithRoots(tmp.toString());
        assertThatThrownBy(() -> svc.assertWritablePath("/etc/passwd"))
                .isInstanceOf(PathOutsideAllowedRootsException.class);
    }

    @Test
    @DisplayName(".. 시그먼트 입력 → PathTraversal (S4.x 보강 — input syntactic fail-fast)")
    void traversal_escape(@TempDir Path tmp) {
        PathPolicyService svc = serviceWithRoots(tmp.toString());
        // S4.x — `..` 시그먼트 자체를 input 단계에서 거절 (normalize 가 silent 변형하기 전에 fail-fast).
        // 입력에 따라 PathTraversal 또는 PathOutsideAllowedRoots 둘 다 정당한 거절.
        assertThatThrownBy(() -> svc.assertWritablePath(tmp.resolve("../../../../etc").toString()))
                .isInstanceOfAny(PathTraversalException.class, PathOutsideAllowedRootsException.class);
    }

    @Test
    @DisplayName("null byte 포함 입력 → PathTraversal")
    void nullByte() {
        PathPolicyService svc = serviceWithRoots("/opt");
        assertThatThrownBy(() -> svc.assertWritablePath("/opt/x\0bad"))
                .isInstanceOf(PathTraversalException.class);
    }

    @Test
    @DisplayName("빈 입력 → PathTraversal")
    void blank() {
        PathPolicyService svc = serviceWithRoots("/opt");
        assertThatThrownBy(() -> svc.assertWritablePath(""))
                .isInstanceOf(PathTraversalException.class);
    }

    // ==== S3.4 (K10) — `~`/`$` 거절 완화 회귀 차단 ====

    @Test
    @DisplayName("S3.4 K10 — `~` 가 첫 글자면 거절 (홈 디렉토리 표현 오인 방지)")
    void tildeFirstChar_rejected(@TempDir Path tmp) {
        PathPolicyService svc = serviceWithRoots(tmp.toString());
        assertThatThrownBy(() -> svc.assertWritablePath("~/iso/foo"))
                .isInstanceOf(PathTraversalException.class);
    }

    @Test
    @DisplayName("S3.4 K10 — 경로 중간의 `~` 는 합법 디렉토리명으로 통과")
    void tildeMiddleOfPath_passed(@TempDir Path tmp) {
        PathPolicyService svc = serviceWithRoots(tmp.toString());
        // /tmp/.../dell~r750/v1 — `~` 가 중간에 있는 합법 디렉토리명
        Path ok = svc.assertWritablePath(tmp.resolve("dell~r750/v1").toString());
        assertThat(ok.toString()).startsWith(tmp.toString());
    }

    @Test
    @DisplayName("S3.4 K10 — `$` 가 포함된 합법 경로 통과")
    void dollarSign_passed(@TempDir Path tmp) {
        PathPolicyService svc = serviceWithRoots(tmp.toString());
        Path ok = svc.assertWritablePath(tmp.resolve("$arch/v1").toString());
        assertThat(ok.toString()).startsWith(tmp.toString());
    }

    @Test
    @DisplayName("S3.4 K10 — `$` 다중 등장도 통과")
    void dollarSignMultiple_passed(@TempDir Path tmp) {
        PathPolicyService svc = serviceWithRoots(tmp.toString());
        Path ok = svc.assertWritablePath(tmp.resolve("$1/$2").toString());
        assertThat(ok.toString()).startsWith(tmp.toString());
    }
}
