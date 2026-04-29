package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.common.filesystem.exception.BundleExtractionException;
import com.example.serverprovision.management.common.filesystem.exception.EmptyBundleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundleExtractionServiceTest {

    /** S3 — 본 단위 테스트는 zip slip / 폴더 케이스 분기 검증이 핵심이라 보안 가드는 mock 으로 통과시킴. */
    private final com.example.serverprovision.global.security.config.UploadSecurityProperties uploadProps =
            new com.example.serverprovision.global.security.config.UploadSecurityProperties(
                    org.springframework.util.unit.DataSize.ofGigabytes(5),
                    org.springframework.util.unit.DataSize.ofGigabytes(20),
                    5000,
                    org.springframework.util.unit.DataSize.ofGigabytes(20),
                    org.springframework.util.unit.DataSize.ofGigabytes(20),
                    100,
                    10000,
                    com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy.ALLOW,
                    50,
                    com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy.DISABLED,
                    null,
                    org.springframework.util.unit.DataSize.ofGigabytes(20)
            );
    private final com.example.serverprovision.global.security.config.FileSystemSecurityProperties fsProps =
            new com.example.serverprovision.global.security.config.FileSystemSecurityProperties(2000, 8);
    private final com.example.serverprovision.global.security.FileSystemHardener fileSystemHardener =
            new com.example.serverprovision.global.security.FileSystemHardener(fsProps);
    private final com.example.serverprovision.global.security.UploadTempDirectoryProvider tempDirProvider =
            new com.example.serverprovision.global.security.UploadTempDirectoryProvider(uploadProps);
    private final com.example.serverprovision.global.security.ZipBombGuard zipBombGuard =
            new com.example.serverprovision.global.security.ZipBombGuard(uploadProps, tempDirProvider);
    private final com.example.serverprovision.global.security.ContentGuard contentGuard =
            new com.example.serverprovision.global.security.ContentGuard(uploadProps);
    private final com.example.serverprovision.global.security.UploadLimitsPolicy uploadLimitsPolicy =
            new com.example.serverprovision.global.security.UploadLimitsPolicy(uploadProps, fsProps);
    private final BundleExtractionService service = new BundleExtractionService(
            zipBombGuard, contentGuard, uploadLimitsPolicy, fileSystemHardener, tempDirProvider);

    // ---- folder 케이스 ---------------------------------------------

    @Test
    @DisplayName("extractFolder 케이스 2 : wrapping folder 는 제거되고 내용물만 전개")
    void extractFolder_stripsPrefix(@TempDir Path tmp) {
        MultipartFile[] files = {
                mp("BiosPkg/f.nsh", "nsh"),
                mp("BiosPkg/SPI_UPD/image.bin", "binary")
        };
        Path target = tmp.resolve("target");

        service.extractFolder(files, target);

        assertThat(Files.exists(target.resolve("f.nsh"))).isTrue();
        assertThat(Files.exists(target.resolve("SPI_UPD/image.bin"))).isTrue();
        assertThat(Files.exists(target.resolve("BiosPkg"))).isFalse();
    }

    @Test
    @DisplayName("extractFolder 케이스 1 : prefix 없는 개별 파일은 거절")
    void extractFolder_rejectsCase1(@TempDir Path tmp) {
        MultipartFile[] files = { mp("loose1.bin", "x"), mp("loose2.bin", "y") };
        assertThatThrownBy(() -> service.extractFolder(files, tmp.resolve("t")))
                .isInstanceOf(BundleExtractionException.class);
    }

    @Test
    @DisplayName("extractFolder : 파일 0개면 EmptyBundleException")
    void extractFolder_empty(@TempDir Path tmp) {
        assertThatThrownBy(() -> service.extractFolder(new MultipartFile[0], tmp.resolve("t")))
                .isInstanceOf(EmptyBundleException.class);
    }

    @Test
    @DisplayName("extractFolder : 여러 폴더 prefix 가 섞이면 거절")
    void extractFolder_multiPrefix(@TempDir Path tmp) {
        MultipartFile[] files = {
                mp("PkgA/f.nsh", ""),
                mp("PkgB/f.nsh", "")
        };
        assertThatThrownBy(() -> service.extractFolder(files, tmp.resolve("t")))
                .isInstanceOf(BundleExtractionException.class);
    }

    // ---- zip 케이스 ------------------------------------------------

    @Test
    @DisplayName("extractZip 케이스 3 : flat zip 그대로 전개")
    void extractZip_flat(@TempDir Path tmp) throws Exception {
        MultipartFile z = zip(new String[][]{
                {"f.nsh", "nsh"},
                {"SPI_UPD/image.bin", "binary"}
        });
        Path target = tmp.resolve("target");
        service.extractZip(z, target);

        assertThat(Files.exists(target.resolve("f.nsh"))).isTrue();
        assertThat(Files.exists(target.resolve("SPI_UPD/image.bin"))).isTrue();
    }

    @Test
    @DisplayName("extractZip 케이스 4 : 단일 폴더 감싼 zip 은 prefix 제거 후 전개")
    void extractZip_wrapped(@TempDir Path tmp) throws Exception {
        MultipartFile z = zip(new String[][]{
                {"BiosPkg/f.nsh", "nsh"},
                {"BiosPkg/sub/x.bin", "x"}
        });
        Path target = tmp.resolve("target");
        service.extractZip(z, target);

        assertThat(Files.exists(target.resolve("f.nsh"))).isTrue();
        assertThat(Files.exists(target.resolve("sub/x.bin"))).isTrue();
        assertThat(Files.exists(target.resolve("BiosPkg"))).isFalse();
    }

    @Test
    @DisplayName("extractZip : 빈 zip 은 EmptyBundleException")
    void extractZip_empty(@TempDir Path tmp) throws Exception {
        MultipartFile z = zip(new String[0][]);
        assertThatThrownBy(() -> service.extractZip(z, tmp.resolve("t")))
                .isInstanceOf(EmptyBundleException.class);
    }

    @Test
    @DisplayName("extractZip : zip slip 시도는 BundleExtractionException 으로 차단")
    void extractZip_slipBlocked(@TempDir Path tmp) throws Exception {
        MultipartFile z = zip(new String[][]{ {"../../evil.txt", "gotcha"} });
        assertThatThrownBy(() -> service.extractZip(z, tmp.resolve("t")))
                .isInstanceOf(BundleExtractionException.class);
    }

    // ---- S3.3 (K16) commonPrefix 후 재침투 가드 ---------------------

    @Test
    @DisplayName("S3.3 (K16) extractZip : commonPrefix strip 후 traversal 재출현 → BundleExtractionException")
    void commonPrefixStripExposesTraversal_rejected(@TempDir Path tmp) throws Exception {
        // 모든 entry 가 동일한 first-segment "wrapping-folder/" 로 시작하므로 commonPrefix 가 검출되어 strip 된다.
        // strip 결과는 "../etc/passwd" — sanitize 가 다시 실행되어야 하며, 두 번째 sanitize 또는 safeResolve 에서
        // BundleExtractionException 으로 거절되어야 한다 (zip slip 재침투 차단).
        // 일관된 prefix 보존을 위해 보조 entry "wrapping-folder/legit.bin" 을 함께 제공한다.
        MultipartFile z = zip(new String[][]{
                {"wrapping-folder/legit.bin", "ok"},
                {"wrapping-folder/../etc/passwd", "evil"}
        });
        assertThatThrownBy(() -> service.extractZip(z, tmp.resolve("t")))
                .isInstanceOf(BundleExtractionException.class);
    }

    @Test
    @DisplayName("S3.3 (K16) extractZip : commonPrefix strip 결과가 빈 문자열이면 skip (회귀 가드)")
    void commonPrefixStripBecomesEmpty_skipped(@TempDir Path tmp) throws Exception {
        // "wrapper/" 디렉토리 entry 는 prefix strip 후 빈 문자열이 되며, 이 경우 continue (skip) 가 정상 흐름.
        // 정상 파일 "wrapper/file.bin" 은 그대로 전개되어야 하고, 빈 entry 때문에 IOException 으로 새지 않아야 한다.
        MultipartFile z = zip(new String[][]{
                {"wrapper/file.bin", "data"}
        });
        Path target = tmp.resolve("target");
        service.extractZip(z, target);

        assertThat(Files.exists(target.resolve("file.bin"))).isTrue();
    }

    // ---- single file 케이스 ----------------------------------------

    @Test
    @DisplayName("extractSingleFile : ASUS .cap 단일 파일은 targetDirectory 에 그대로 저장")
    void extractSingleFile_happy(@TempDir Path tmp) {
        MultipartFile f = new MockMultipartFile("singleFile", "X99E-WS.CAP",
                "application/octet-stream", "cap-content".getBytes());
        Path target = tmp.resolve("target");

        service.extractSingleFile(f, target);

        assertThat(Files.exists(target.resolve("X99E-WS.CAP"))).isTrue();
    }

    @Test
    @DisplayName("extractSingleFile : 빈 파일은 EmptyBundleException")
    void extractSingleFile_empty(@TempDir Path tmp) {
        MultipartFile f = new MockMultipartFile("singleFile", "x.cap", null, new byte[0]);
        assertThatThrownBy(() -> service.extractSingleFile(f, tmp.resolve("t")))
                .isInstanceOf(EmptyBundleException.class);
    }

    // ---- helpers ---------------------------------------------------

    private static MultipartFile mp(String relPath, String body) {
        return new MockMultipartFile("folderFiles", relPath,
                "application/octet-stream", body.getBytes());
    }

    private static MultipartFile zip(String[][] entries) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (String[] e : entries) {
                ZipEntry ze = new ZipEntry(e[0]);
                zos.putNextEntry(ze);
                zos.write(e[1].getBytes());
                zos.closeEntry();
            }
        }
        return new MockMultipartFile("zipFile", "bundle.zip", "application/zip", baos.toByteArray());
    }
}
