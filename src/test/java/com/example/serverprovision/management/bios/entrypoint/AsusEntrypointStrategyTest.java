package com.example.serverprovision.management.bios.entrypoint;

import com.example.serverprovision.global.security.EntrypointPolicyService;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.management.bios.exception.EntrypointAmbiguousException;
import com.example.serverprovision.management.bios.exception.EntrypointNotFoundException;
import com.example.serverprovision.management.board.enums.Vendor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S5-11 v2 — ASUS 진입점 탐지 strategy 회귀.
 *
 * <p>ASUS 의 의도된 진입점 = *.cap. 정책 우선순위 :
 * override → 트리 단일 파일 → 루트 *.cap 1 개 → fallback 단일 *.nsh → ambiguous / notFound</p>
 */
class AsusEntrypointStrategyTest {

    private final EntrypointPolicyService entrypointPolicyService = new EntrypointPolicyService();
    private final FileSystemSecurityProperties fsProps = new FileSystemSecurityProperties(2000, 8);
    private final AsusEntrypointStrategy strategy = new AsusEntrypointStrategy(entrypointPolicyService, fsProps);

    @Test
    @DisplayName("supports : ASUS 만 true")
    void supportsAsusOnly() {
        assertThat(strategy.supports(Vendor.ASUS)).isTrue();
        assertThat(strategy.supports(Vendor.GIGABYTE)).isFalse();
        assertThat(strategy.supports(Vendor.FUJITSU)).isFalse();
    }

    @Test
    @DisplayName("override happy : 명시 진입점 그대로 반환")
    void overrideHappy(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("sub"));
        Files.writeString(tmp.resolve("sub/custom.cap"), "");
        assertThat(strategy.detect(tmp, "sub/custom.cap")).isEqualTo("sub/custom.cap");
    }

    @Test
    @DisplayName("override missing : EntrypointNotFoundException")
    void overrideMissingThrows(@TempDir Path tmp) {
        assertThatThrownBy(() -> strategy.detect(tmp, "nope.cap"))
                .isInstanceOf(EntrypointNotFoundException.class);
    }

    @Test
    @DisplayName("ASUS 단일 .CAP : 트리 단일 파일 단계에서 채택")
    void asusSingleCap(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("X99E-WS.CAP"), "binary");
        assertThat(strategy.detect(tmp, null)).isEqualTo("X99E-WS.CAP");
    }

    @Test
    @DisplayName("ASUS .CAP + 부속 README : .CAP 자동 채택")
    void asusCapWithReadme(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("X99E-WS.CAP"), "binary");
        Files.writeString(tmp.resolve("README.txt"), "release notes");
        assertThat(strategy.detect(tmp, null)).isEqualTo("X99E-WS.CAP");
    }

    @Test
    @DisplayName("ASUS .cap (소문자) + 다중 부속 : .cap 자동 채택")
    void asusLowercaseCapWithMultipleAssets(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("x99.cap"), "binary");
        Files.writeString(tmp.resolve("flash.bin"), "");
        Files.writeString(tmp.resolve("tool.exe"), "");
        assertThat(strategy.detect(tmp, null)).isEqualTo("x99.cap");
    }

    @Test
    @DisplayName("ASUS .cap + 부속 .nsh : .cap 우선 (ASUS 의도된 진입점)")
    void asusCapWinsOverNsh(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("x99.cap"), "binary");
        Files.writeString(tmp.resolve("debug.nsh"), "");
        assertThat(strategy.detect(tmp, null)).isEqualTo("x99.cap");
    }

    @Test
    @DisplayName("ASUS .cap 없음 + 단일 .nsh : fallback 으로 .nsh 채택")
    void asusFallbackToSingleNsh(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("flash.nsh"), "");
        Files.writeString(tmp.resolve("readme.bin"), "");
        assertThat(strategy.detect(tmp, null)).isEqualTo("flash.nsh");
    }

    @Test
    @DisplayName("ASUS 다중 .cap : EntrypointAmbiguousException")
    void asusMultipleCapAmbiguous(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("foo.cap"), "");
        Files.writeString(tmp.resolve("bar.CAP"), "");
        Files.writeString(tmp.resolve("note.txt"), "");
        assertThatThrownBy(() -> strategy.detect(tmp, null))
                .isInstanceOf(EntrypointAmbiguousException.class);
    }

    @Test
    @DisplayName("ASUS 0 .cap + 0 .nsh + 일반 다수 : EntrypointNotFoundException")
    void asusNotFound(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.bin"), "");
        Files.writeString(tmp.resolve("b.bin"), "");
        assertThatThrownBy(() -> strategy.detect(tmp, null))
                .isInstanceOf(EntrypointNotFoundException.class);
    }
}
