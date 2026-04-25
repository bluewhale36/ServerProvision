package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.exception.EntrypointAmbiguousException;
import com.example.serverprovision.management.bios.exception.EntrypointNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundleEntrypointDetectorTest {

    private final BundleEntrypointDetector detector = new BundleEntrypointDetector();

    @Test
    @DisplayName("트리 파일 1개면 그 파일 (확장자 무관 — ASUS .CAP 케이스)")
    void singleFile_detectedRegardlessOfExtension(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("X99E-WS.CAP"), "binary");
        assertThat(detector.detect(tmp, null)).isEqualTo("X99E-WS.CAP");
    }

    @Test
    @DisplayName("f.nsh 컨벤션 : 트리 루트에 f.nsh 가 있으면 채택")
    void gigabyte_fNsh(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("f.nsh"), "");
        Files.writeString(tmp.resolve("flash.nsh"), "");
        Files.createDirectories(tmp.resolve("SPI_UPD"));
        Files.writeString(tmp.resolve("SPI_UPD/image.bin"), "");
        assertThat(detector.detect(tmp, null)).isEqualTo("f.nsh");
    }

    @Test
    @DisplayName("flash.nsh fallback : f.nsh 없으면 flash.nsh")
    void flashNsh_fallback(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("flash.nsh"), "");
        Files.writeString(tmp.resolve("other.bin"), "");
        assertThat(detector.detect(tmp, null)).isEqualTo("flash.nsh");
    }

    @Test
    @DisplayName("루트에 .nsh 가 여러 개면 EntrypointAmbiguousException")
    void ambiguous_throws(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("foo.nsh"), "");
        Files.writeString(tmp.resolve("bar.nsh"), "");
        Files.writeString(tmp.resolve("baz.bin"), "");
        assertThatThrownBy(() -> detector.detect(tmp, null))
                .isInstanceOf(EntrypointAmbiguousException.class);
    }

    @Test
    @DisplayName("루트에 .nsh 가 0개 + 파일 다수면 EntrypointNotFoundException")
    void notFound_throws(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.bin"), "");
        Files.writeString(tmp.resolve("b.bin"), "");
        assertThatThrownBy(() -> detector.detect(tmp, null))
                .isInstanceOf(EntrypointNotFoundException.class);
    }

    @Test
    @DisplayName("override 가 있으면 파일 실재 확인 후 그대로 반환")
    void override_happy(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("sub"));
        Files.writeString(tmp.resolve("sub/custom.nsh"), "");
        assertThat(detector.detect(tmp, "sub/custom.nsh")).isEqualTo("sub/custom.nsh");
    }

    @Test
    @DisplayName("override 가 존재하지 않으면 EntrypointNotFoundException")
    void override_missing_throws(@TempDir Path tmp) {
        assertThatThrownBy(() -> detector.detect(tmp, "nope.nsh"))
                .isInstanceOf(EntrypointNotFoundException.class);
    }
}
