package com.example.serverprovision.execution.engine.windows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-a-3 CP4 — 게스트당 1토큰 · 재발급 시 옛 토큰 즉사 · 회수 · URL 조립(D-3). */
class WindowsInstallTokenRegistryTest {

    private static final UUID GUEST = UUID.randomUUID();
    private static final WindowsInstallBundle BUNDLE = new WindowsInstallBundle(
            Path.of("/srv/pxe/win2025/wimboot"), Path.of("/srv/pxe/win2025/sources/boot.wim"), "ini", "bat", "xml");

    private final WindowsInstallTokenRegistry registry = new WindowsInstallTokenRegistry("http://10.0.0.7:8080/");

    @Test
    @DisplayName("발급 → resolve · bundleUrl · urlFor (base-url 의 뒤 슬래시는 정규화)")
    void issue_resolve_urls() {
        UUID token = registry.issue(GUEST, BUNDLE);

        assertThat(registry.resolve(token)).contains(BUNDLE);
        assertThat(registry.bundleUrl(token)).isEqualTo("http://10.0.0.7:8080/api/pxe/v1/windows/" + token);
        assertThat(registry.urlFor(token, WindowsInstallFile.BOOT_WIM)).endsWith("/" + token + "/boot.wim");
    }

    @Test
    @DisplayName("재발급 — 같은 게스트의 앞 토큰은 그 자리에서 죽는다(재시도 뒤 옛 URL 404 의 근거)")
    void reissue_killsPrevious() {
        UUID first = registry.issue(GUEST, BUNDLE);
        UUID second = registry.issue(GUEST, BUNDLE);

        assertThat(registry.resolve(first)).isEmpty();
        assertThat(registry.resolve(second)).isPresent();
    }

    @Test
    @DisplayName("회수 — 게스트 키로 지우면 토큰도 죽는다 · 미발급 토큰의 URL 조립은 흐름 버그(IllegalState)")
    void revoke_andUnknownUrl() {
        UUID token = registry.issue(GUEST, BUNDLE);
        registry.revoke(GUEST);

        assertThat(registry.resolve(token)).isEmpty();
        assertThatThrownBy(() -> registry.bundleUrl(token)).isInstanceOf(IllegalStateException.class);
        assertThat(registry.resolve(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("번들 · 파일 enum — 파일명 5 만 매칭, 스트리밍/렌더본 구분, toString 은 렌더본을 감춘다")
    void bundleAndFileEnum() {
        assertThat(WindowsInstallFile.of("boot.wim")).contains(WindowsInstallFile.BOOT_WIM);
        assertThat(WindowsInstallFile.of("install.wim")).isEmpty();
        assertThat(WindowsInstallFile.of("../install.bat")).isEmpty();
        assertThat(WindowsInstallFile.BOOT_WIM.streamed()).isTrue();
        assertThat(WindowsInstallFile.INSTALL_BAT.streamed()).isFalse();
        assertThat(WindowsInstallFile.INSTALL_BAT.mediaType().toString()).isEqualTo("text/plain;charset=US-ASCII");
        assertThat(WindowsInstallFile.AUTOUNATTEND.mediaType().toString()).isEqualTo("application/xml;charset=UTF-8");
        assertThat(BUNDLE.pathOf(WindowsInstallFile.WIMBOOT)).contains(Path.of("/srv/pxe/win2025/wimboot"));
        assertThat(BUNDLE.textOf(WindowsInstallFile.AUTOUNATTEND)).contains("xml");
        assertThat(BUNDLE.pathOf(WindowsInstallFile.INSTALL_BAT)).isEmpty();
        assertThat(BUNDLE.toString()).doesNotContain("bat").contains("****");
    }
}
