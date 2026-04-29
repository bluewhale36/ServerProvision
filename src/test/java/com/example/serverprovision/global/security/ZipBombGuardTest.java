package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.ExecutableBinaryPolicy;
import com.example.serverprovision.global.security.config.UploadSecurityProperties.SuspiciousFilenamesPolicy;
import com.example.serverprovision.global.security.exception.ZipBombSuspectedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipBombGuardTest {

    private UploadSecurityProperties props(int maxEntries, int ratio) {
        return propsWithStored(maxEntries, ratio, DataSize.ofGigabytes(20));
    }

    private UploadSecurityProperties propsWithStored(int maxEntries, int ratio, DataSize storedCap) {
        return new UploadSecurityProperties(
                DataSize.ofGigabytes(5), DataSize.ofGigabytes(20),
                5000, DataSize.ofGigabytes(20),
                DataSize.ofGigabytes(20), ratio, maxEntries,
                ExecutableBinaryPolicy.ALLOW, 50, SuspiciousFilenamesPolicy.DISABLED,
                null, storedCap
        );
    }

    private UploadTempDirectoryProvider tempProvider(UploadSecurityProperties p) {
        return new UploadTempDirectoryProvider(p);
    }

    @Test
    @DisplayName("정상 zip → 통과")
    void safeZip() throws Exception {
        UploadSecurityProperties p = props(10000, 100);
        ZipBombGuard guard = new ZipBombGuard(p, tempProvider(p));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("a.txt"));
            zos.write("hello".getBytes());
            zos.closeEntry();
        }
        guard.assertSafeZip(new MockMultipartFile("zip", "ok.zip", "application/zip", baos.toByteArray()));
    }

    @Test
    @DisplayName("entry 갯수 > 한도 → ZipBombSuspected")
    void entryCountOver() throws Exception {
        UploadSecurityProperties p = props(2, 100);
        ZipBombGuard guard = new ZipBombGuard(p, tempProvider(p));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < 5; i++) {
                zos.putNextEntry(new ZipEntry("e" + i + ".txt"));
                zos.write(("data" + i).getBytes());
                zos.closeEntry();
            }
        }
        assertThatThrownBy(() -> guard.assertSafeZip(
                new MockMultipartFile("zip", "many.zip", "application/zip", baos.toByteArray())))
                .isInstanceOf(ZipBombSuspectedException.class);
    }

    @Test
    @DisplayName("S3.4 K17 — corrupted zip → ZipBombSuspectedException (415 매핑)")
    void corruptedZip_classified415() {
        UploadSecurityProperties p = props(10000, 100);
        ZipBombGuard guard = new ZipBombGuard(p, tempProvider(p));
        // PK signature 시작이지만 central directory 가 없는 손상 zip 페이로드 (ZipException 유발)
        byte[] corrupted = new byte[] {
                0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };
        assertThatThrownBy(() -> guard.assertSafeZip(
                new MockMultipartFile("zip", "corrupted.zip", "application/zip", corrupted)))
                .isInstanceOf(ZipBombSuspectedException.class);
    }

    @Test
    @DisplayName("C7 — stored 임계 0 (검사 skip) → 정상 ratio 1 의 STORED entry 통과")
    void storedThresholdZeroSkip() throws Exception {
        // maxStoredEntryBytes=0 (검사 skip). STORED 엔트리는 compressed=size 라 ratio 검사도 통과.
        UploadSecurityProperties p = propsWithStored(10000, 100, DataSize.ofBytes(0));
        ZipBombGuard guard = new ZipBombGuard(p, tempProvider(p));
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.setMethod(ZipOutputStream.STORED);
            byte[] data = new byte[2048];
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(data);
            ZipEntry e = new ZipEntry("big.bin");
            e.setMethod(ZipEntry.STORED);
            e.setSize(data.length);
            e.setCompressedSize(data.length);
            e.setCrc(crc.getValue());
            zos.putNextEntry(e);
            zos.write(data);
            zos.closeEntry();
        }
        // skip 옵션이 정상 통과시키는지 (regression 보호) 검증.
        guard.assertSafeZip(new MockMultipartFile("zip", "stored.zip", "application/zip", baos.toByteArray()));
    }
}
