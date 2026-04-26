package com.example.serverprovision.management.common.filesystem.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BundleTreeCleanupServiceTest {

    private final BundleTreeCleanupService service = new BundleTreeCleanupService();

    @Test
    @DisplayName("purgeExistingTree : 하위 파일/디렉토리를 역순 삭제한다")
    void purgeExistingTree_removesWholeTree(@TempDir Path tmp) throws Exception {
        Path root = tmp.resolve("bundle");
        Files.createDirectories(root.resolve("sub"));
        Files.writeString(root.resolve("sub").resolve("flash.nsh"), "echo");
        Files.writeString(root.resolve("readme.txt"), "x");

        service.purgeExistingTree(root, "test-purge");

        assertThat(Files.exists(root)).isFalse();
    }

    @Test
    @DisplayName("cleanupFailedUpload : 존재하지 않는 경로여도 예외 없이 통과")
    void cleanupFailedUpload_missingPath_ok(@TempDir Path tmp) {
        Path missing = tmp.resolve("missing");

        assertThatCode(() -> service.cleanupFailedUpload(
                missing, "test-purge", "addBios", new IllegalStateException("boom")))
                .doesNotThrowAnyException();
    }
}
