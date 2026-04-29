package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy;
import com.example.serverprovision.global.security.exception.UploadLimitExceededException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S3.2 (K4) — UploadLimitsPolicy 회귀. file count / single size / tree byte 한도 3 가지 시나리오.
 */
class UploadLimitsPolicyTest {

    private UploadLimitsPolicy policy(int maxFolderFiles, DataSize maxFileSize, DataSize maxTreeBytes) {
        UploadSecurityProperties up = new UploadSecurityProperties(
                maxFileSize, DataSize.ofGigabytes(20),
                maxFolderFiles, maxTreeBytes,
                DataSize.ofGigabytes(20), 100, 10000,
                ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED,
                null, DataSize.ofGigabytes(5));
        FileSystemSecurityProperties fs = new FileSystemSecurityProperties(2000, 8);
        return new UploadLimitsPolicy(up, fs);
    }

    @Test
    @DisplayName("file count > 한도 → UploadLimitExceeded")
    void fileCountExceeded() {
        UploadLimitsPolicy p = policy(2, DataSize.ofMegabytes(10), DataSize.ofMegabytes(100));
        MultipartFile[] files = new MultipartFile[5];
        for (int i = 0; i < 5; i++) {
            files[i] = new MockMultipartFile("f" + i, "x.txt", "text/plain", new byte[]{0});
        }
        assertThatThrownBy(() -> p.assertFileCount(files))
                .isInstanceOf(UploadLimitExceededException.class)
                .hasMessageContaining("파일 갯수");
    }

    @Test
    @DisplayName("single file size > 한도 → UploadLimitExceeded")
    void singleFileTooLarge() {
        UploadLimitsPolicy p = policy(1000, DataSize.ofKilobytes(1), DataSize.ofMegabytes(100));
        byte[] big = new byte[2048];
        MultipartFile f = new MockMultipartFile("big", "big.bin", "application/octet-stream", big);
        assertThatThrownBy(() -> p.assertSingleFileSize(f))
                .isInstanceOf(UploadLimitExceededException.class)
                .hasMessageContaining("단일 파일");
    }

    @Test
    @DisplayName("tree byte 합 > 한도 → UploadLimitExceeded")
    void treeBytesExceeded(@TempDir Path tempDir) throws Exception {
        // 3KB 파일 2 개 → tree byte 합 6KB. 한도 4KB.
        Files.write(tempDir.resolve("a.bin"), new byte[3072]);
        Files.write(tempDir.resolve("b.bin"), new byte[3072]);
        UploadLimitsPolicy p = policy(1000, DataSize.ofMegabytes(10), DataSize.ofKilobytes(4));
        assertThatThrownBy(() -> p.assertTreeBytes(tempDir))
                .isInstanceOf(UploadLimitExceededException.class)
                .hasMessageContaining("트리 byte");
    }

    @Test
    @DisplayName("tree byte 합이 한도 이내 → 통과")
    void treeBytesUnder(@TempDir Path tempDir) throws Exception {
        Files.write(tempDir.resolve("a.bin"), new byte[1024]);
        UploadLimitsPolicy p = policy(1000, DataSize.ofMegabytes(10), DataSize.ofMegabytes(10));
        assertThatCode(() -> p.assertTreeBytes(tempDir)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("정상 단일 파일 → 통과")
    void singleFileOk() {
        UploadLimitsPolicy p = policy(1000, DataSize.ofKilobytes(10), DataSize.ofMegabytes(100));
        MultipartFile f = new MockMultipartFile("ok", "ok.txt", "text/plain", "hello".getBytes());
        assertThatCode(() -> p.assertSingleFileSize(f))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("null / 빈 file array → silent no-op")
    void nullArray_noop() {
        UploadLimitsPolicy p = policy(1000, DataSize.ofKilobytes(10), DataSize.ofMegabytes(100));
        assertThatCode(() -> p.assertFileCount(null)).doesNotThrowAnyException();
        assertThatCode(() -> p.assertFileCount(new MultipartFile[0])).doesNotThrowAnyException();
        assertThatCode(() -> p.assertSingleFileSize(null)).doesNotThrowAnyException();
    }
}
