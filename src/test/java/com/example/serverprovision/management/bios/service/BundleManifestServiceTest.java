package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.common.filesystem.exception.BundleExtractionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundleManifestServiceTest {

    private final BundleManifestService service = new BundleManifestService();

    @Test
    @DisplayName("compute : 같은 트리면 해시 동일, 파일 수/총 바이트 정확")
    void compute_happy(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "hello");
        Files.createDirectories(tmp.resolve("sub"));
        Files.writeString(tmp.resolve("sub/b.bin"), "world!");

        var r1 = service.compute(tmp);
        var r2 = service.compute(tmp);

        assertThat(r1.manifestHash()).hasSize(64).isEqualTo(r2.manifestHash());
        assertThat(r1.fileCount()).isEqualTo(2);
        assertThat(r1.totalBytes()).isEqualTo(11); // 5 + 6
    }

    @Test
    @DisplayName("compute : .provision.json 은 집계 대상에서 제외")
    void compute_excludesMarker(@TempDir Path tmp) throws Exception {
        Files.writeString(tmp.resolve("a.txt"), "x");
        Files.writeString(tmp.resolve(".provision.json"), "{\"noise\":true}");

        var r = service.compute(tmp);
        assertThat(r.fileCount()).isEqualTo(1);
        assertThat(r.totalBytes()).isEqualTo(1);
    }

    @Test
    @DisplayName("compute : 트리 루트가 디렉토리가 아니면 예외")
    void compute_notDirectory(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("notDir");
        Files.writeString(file, "x");
        assertThatThrownBy(() -> service.compute(file))
                .isInstanceOf(BundleExtractionException.class);
    }
}
