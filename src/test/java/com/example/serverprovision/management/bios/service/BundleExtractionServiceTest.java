package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.management.bios.exception.BundleExtractionException;
import com.example.serverprovision.management.bios.exception.EmptyBundleException;
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

    private final BundleExtractionService service = new BundleExtractionService();

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
