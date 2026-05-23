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
 * S5-11 v2 — GIGABYTE 진입점 탐지 strategy 회귀.
 *
 * <p>핵심 변경 : .cap 동봉되어 있어도 자동 채택 안 함 (GIGABYTE 의 의도된 진입점은 .nsh). 운영 도구 /
 * 디버그용 .cap 이 부속으로 들어와도 잘못된 진입점 선택 방지.</p>
 */
class GigabyteEntrypointStrategyTest {

    private final EntrypointPolicyService entrypointPolicyService = new EntrypointPolicyService();
    private final FileSystemSecurityProperties fsProps = new FileSystemSecurityProperties(2000, 8);
    private final GigabyteEntrypointStrategy strategy = new GigabyteEntrypointStrategy(entrypointPolicyService, fsProps);

    @Test
    @DisplayName("supports : GIGABYTE 만 true")
    void supportsGigabyteOnly() {
        assertThat(strategy.supports(Vendor.GIGABYTE)).isTrue();
        assertThat(strategy.supports(Vendor.ASUS)).isFalse();
        assertThat(strategy.supports(Vendor.FUJITSU)).isFalse();
    }

    @Test
    @DisplayName("override happy")
    void overrideHappy(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("sub"));
        Files.writeString(tmp.resolve("sub/custom.nsh"), "");
        assertThat(strategy.detect(tmp, "sub/custom.nsh")).isEqualTo("sub/custom.nsh");
    }

    @Test
    @DisplayName("f.nsh 우선 — 단일 파일 단계 아닌 다중 파일 트리에서")
    void gigabyteFnsh(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("f.nsh"), "");
        Files.writeString(tmp.resolve("flash.nsh"), "");
        Files.createDirectories(tmp.resolve("SPI_UPD"));
        Files.writeString(tmp.resolve("SPI_UPD/image.bin"), "");
        assertThat(strategy.detect(tmp, null)).isEqualTo("f.nsh");
    }

    @Test
    @DisplayName("flash.nsh fallback : f.nsh 없으면 flash.nsh")
    void flashNshFallback(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("flash.nsh"), "");
        Files.writeString(tmp.resolve("other.bin"), "");
        assertThat(strategy.detect(tmp, null)).isEqualTo("flash.nsh");
    }

    @Test
    @DisplayName("단일 *.nsh : 그것을 채택")
    void singleNsh(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("custom.nsh"), "");
        Files.writeString(tmp.resolve("data.bin"), "");
        assertThat(strategy.detect(tmp, null)).isEqualTo("custom.nsh");
    }

    @Test
    @DisplayName("다중 .nsh : EntrypointAmbiguousException")
    void multipleNshAmbiguous(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("foo.nsh"), "");
        Files.writeString(tmp.resolve("bar.nsh"), "");
        Files.writeString(tmp.resolve("baz.bin"), "");
        assertThatThrownBy(() -> strategy.detect(tmp, null))
                .isInstanceOf(EntrypointAmbiguousException.class);
    }

    @Test
    @DisplayName("S5-11 v2 핵심 : f.nsh + asus-tool.cap 동봉 → f.nsh 채택 (.cap 무시)")
    void gigabyteFnshWinsOverCap(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("f.nsh"), "");
        Files.writeString(tmp.resolve("asus-tool.cap"), "binary");
        Files.writeString(tmp.resolve("README.txt"), "");
        assertThat(strategy.detect(tmp, null)).isEqualTo("f.nsh");
    }

    @Test
    @DisplayName("S5-11 v2 핵심 : flash.nsh + .cap 동봉 → flash.nsh 채택")
    void gigabyteFlashNshWinsOverCap(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("flash.nsh"), "");
        Files.writeString(tmp.resolve("tool.cap"), "");
        assertThat(strategy.detect(tmp, null)).isEqualTo("flash.nsh");
    }

    @Test
    @DisplayName("S5-11 v2 핵심 : .cap 만 있고 .nsh 0 → notFound (GIGABYTE 정책상 .cap 자동 채택 안 함)")
    void gigabyteCapOnlyNotFound(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("x99.cap"), "");
        Files.writeString(tmp.resolve("readme.txt"), "");
        assertThatThrownBy(() -> strategy.detect(tmp, null))
                .isInstanceOf(EntrypointNotFoundException.class);
    }

    @Test
    @DisplayName("트리 단일 파일 (예: .nsh 1 개만) : 그것 채택")
    void singleFileTree(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("flash.nsh"), "");
        assertThat(strategy.detect(tmp, null)).isEqualTo("flash.nsh");
    }

    @Test
    @DisplayName("일반 파일 다수 + .nsh 0 + .cap 0 : notFound")
    void gigabyteNotFound(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.bin"), "");
        Files.writeString(tmp.resolve("b.bin"), "");
        assertThatThrownBy(() -> strategy.detect(tmp, null))
                .isInstanceOf(EntrypointNotFoundException.class);
    }
}
