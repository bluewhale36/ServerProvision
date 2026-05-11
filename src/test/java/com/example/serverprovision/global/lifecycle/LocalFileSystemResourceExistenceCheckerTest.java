package com.example.serverprovision.global.lifecycle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MK3-2 (S 카테고리, DCM3-2.16) — LocalFileSystemResourceExistenceChecker 의 단순 위임 검증.
 * 본 테스트는 향후 NFS / 분산 FS 구현체 도입 시점에 동일한 시그니처로 검증 패턴 보존.
 */
class LocalFileSystemResourceExistenceCheckerTest {

    private LocalFileSystemResourceExistenceChecker checker;

    @BeforeEach
    void setUp() {
        checker = new LocalFileSystemResourceExistenceChecker();
    }

    @Test
    @DisplayName("S1 : 존재하는 파일 → true")
    void exists_regularFile_true(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("foo.iso");
        Files.writeString(file, "data");
        assertThat(checker.exists(file)).isTrue();
    }

    @Test
    @DisplayName("S2 : 부재 경로 → false")
    void exists_missing_false(@TempDir Path tmp) {
        Path missing = tmp.resolve("not-here.iso");
        assertThat(checker.exists(missing)).isFalse();
    }

    @Test
    @DisplayName("S3 : 존재하는 디렉토리 → true (BIOS / BMC / Subprogram 의 IN_TREE 자원 검증)")
    void exists_directory_true(@TempDir Path tmp) {
        // tmp 자체가 존재 디렉토리
        assertThat(checker.exists(tmp)).isTrue();
    }

    @Test
    @DisplayName("null path → false (방어적 가드)")
    void exists_null_false() {
        assertThat(checker.exists(null)).isFalse();
    }
}
