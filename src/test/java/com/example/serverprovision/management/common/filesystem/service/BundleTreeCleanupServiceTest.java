package com.example.serverprovision.management.common.filesystem.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class BundleTreeCleanupServiceTest {

    /** S3.1 — purgeExistingTree 가 PathPolicy 가드를 통과해야 함. 단위 테스트는 mock 으로 통과. */
    private final com.example.serverprovision.global.security.PathPolicyService pathPolicyService =
            org.mockito.Mockito.mock(com.example.serverprovision.global.security.PathPolicyService.class);
    private final com.example.serverprovision.global.security.config.FileSystemSecurityProperties fsProps =
            new com.example.serverprovision.global.security.config.FileSystemSecurityProperties(2000, 8);
    private final BundleTreeCleanupService service = new BundleTreeCleanupService(pathPolicyService, fsProps);

    {
        // 모든 path 통과 시키는 default mock — 본 단위 테스트는 가드 동작 자체가 아니라 walk/delete 검증.
        org.mockito.Mockito.when(pathPolicyService.assertWritablePath(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> Path.of(inv.getArgument(0, String.class)));
    }

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
