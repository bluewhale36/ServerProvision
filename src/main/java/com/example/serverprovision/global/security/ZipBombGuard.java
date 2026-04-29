package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import com.example.serverprovision.global.security.exception.ZipBombInspectionFailedException;
import com.example.serverprovision.global.security.exception.ZipBombSuspectedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Zip 압축 해제 전 사전 점검 — entry 합계 / 비율 / 갯수 임계 검사.
 *
 * <p>S3.1 (B3) — entry 의 {@code getSize()} 가 {@code -1} 인 경우 거절 (uncompressed size 가 알려지지 않은 stream 은
 * 합계 가드를 우회 가능). 정확한 size 검출을 위해 {@code ZipInputStream} 이 아니라 {@code ZipFile}
 * (central directory 기반) 을 사용.</p>
 *
 * <p>C5 — {@code MultipartFile} 의 {@code getInputStream()} 다중 소비를 줄이기 위해 {@link #assertSafeZip(Path)}
 * overload 를 추가. 호출자(BundleExtractionService) 가 multipart 를 임시 파일로 옮긴 뒤 본 overload 를 부른다.</p>
 *
 * <p>C7 — {@code compressed=0} (stored entry — 무압축) 인 경우 ratio 검사를 skip 하던 분기에 단일 entry size
 * 임계 ({@link UploadSecurityProperties#maxStoredEntryBytes()}) 를 추가 적용한다.</p>
 */
@Service
@RequiredArgsConstructor
public class ZipBombGuard {

    private final UploadSecurityProperties uploadSecurityProperties;
    private final UploadTempDirectoryProvider uploadTempDirectoryProvider;

    public void assertSafeZip(MultipartFile zipFile) {
        if (zipFile == null || zipFile.isEmpty()) return;
        Path tempZip = null;
        try {
            // C6 — provider 가 결정한 임시 디렉토리에 저장 + 0644 권한 강제.
            tempZip = uploadTempDirectoryProvider.createTempFile("zipbomb-guard-", ".zip");
            try (InputStream in = zipFile.getInputStream()) {
                Files.copy(in, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }
            inspect(tempZip);
        } catch (java.util.zip.ZipException ze) {
            // S3.4 (K17) — corrupted zip (구조 손상) 은 사용자 콘텐츠 위협 분류로 415 (ZipBombSuspected) 와 동일 매핑.
            // ZipException 은 IOException 의 하위지만 의미가 운영 IO 오류가 아니라 콘텐츠 무결성 문제다.
            throw new ZipBombSuspectedException("zip 구조 손상 (corrupted) : " + ze.getMessage());
        } catch (IOException ex) {
            // S3.2 (K17) — IO 실패는 zip bomb 의심과 의미가 다르다 (운영 환경 이슈). 별도 예외로 분류.
            throw new ZipBombInspectionFailedException(ex.getMessage(), ex);
        } finally {
            if (tempZip != null) {
                try { Files.deleteIfExists(tempZip); } catch (IOException ignored) { /* best-effort */ }
            }
        }
    }

    /**
     * C5 — 이미 디스크에 떨어진 zip 파일을 직접 검사하는 overload. 재 transfer 비용을 회피.
     */
    public void assertSafeZip(Path zipPath) {
        if (zipPath == null || !Files.exists(zipPath)) return;
        try {
            inspect(zipPath);
        } catch (java.util.zip.ZipException ze) {
            // S3.4 (K17) — corrupted zip 은 콘텐츠 무결성 문제로 415 매핑.
            throw new ZipBombSuspectedException("zip 구조 손상 (corrupted) : " + ze.getMessage());
        } catch (IOException ex) {
            // S3.2 (K17) — IO 실패는 zip bomb 의심과 의미가 다르다 (운영 환경 이슈). 별도 예외로 분류.
            throw new ZipBombInspectionFailedException(ex.getMessage(), ex);
        }
    }

    private void inspect(Path zipPath) throws IOException {
        long maxUncompressed = uploadSecurityProperties.maxZipUncompressedBytes().toBytes();
        int maxEntries = uploadSecurityProperties.maxZipEntries();
        int ratio = uploadSecurityProperties.zipBombRatio();
        long maxStored = uploadSecurityProperties.maxStoredEntryBytes() == null
                ? -1 : uploadSecurityProperties.maxStoredEntryBytes().toBytes();

        long totalUncompressed = 0;
        int entryCount = 0;
        try (ZipFile zf = new ZipFile(zipPath.toFile())) {
            java.util.Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                entryCount++;
                if (entryCount > maxEntries) {
                    throw new ZipBombSuspectedException("entry 갯수 > " + maxEntries);
                }
                long size = e.getSize();
                long compressed = e.getCompressedSize();
                // S3.1 (B3) — central directory 에도 size 가 미기록(-1) 이면 거절 (메타데이터 조작 의심).
                if (size < 0) {
                    throw new ZipBombSuspectedException(
                            "entry 의 uncompressed size 가 알려지지 않음 (size=-1) — 거절");
                }
                // S3.2 (K8) — compressedSize 도 -1 이면 메타데이터 조작 의심.
                if (compressed < 0) {
                    throw new ZipBombSuspectedException(
                            "entry 의 compressed size 가 알려지지 않음 (compressed=-1) — 거절");
                }
                totalUncompressed += size;
                if (totalUncompressed > maxUncompressed) {
                    throw new ZipBombSuspectedException(
                            "entry 합 " + totalUncompressed + " > 한도 " + maxUncompressed);
                }
                // C7 — stored entry (compressed=0) 의 단일 size 임계. ratio 검사가 skip 되는 분기 보강.
                if (compressed == 0 && size > 0 && maxStored > 0 && size > maxStored) {
                    throw new ZipBombSuspectedException(
                            "stored entry size " + size + " > stored 임계 " + maxStored);
                }
                if (size > 0 && compressed > 0 && (size / compressed) > ratio) {
                    throw new ZipBombSuspectedException(
                            "압축률 " + (size / compressed) + " > 임계 " + ratio);
                }
            }
        }
    }
}
