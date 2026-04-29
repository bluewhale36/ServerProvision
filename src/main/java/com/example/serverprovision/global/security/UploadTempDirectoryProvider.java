package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.UploadSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * C6 — 업로드 임시 파일 (zip 검사용 / extract 용) 의 단일 위치 결정 헬퍼.
 *
 * <p>multi-user 호스트에서 default {@code java.io.tmpdir} (/tmp) 를 그대로 쓰면 다른 사용자가
 * {@code listdir} 로 업로드 중 파일을 엿볼 수 있다. {@link UploadSecurityProperties#tempDir} 가 설정되면
 * 해당 디렉토리에 임시 파일 생성, 미설정이면 default tmpdir 로 fallback.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadTempDirectoryProvider {

    /** S3.2 (K9) — 업로드 임시 파일은 default 0644 가 아니라 owner-only (0600) 로 강제. multi-user 호스트에서
     *  tempDir 가 다른 사용자에게도 readable 하면 업로드 중 파일을 엿볼 위험이 있어 boundary 좁힘. */
    private static final Set<PosixFilePermission> TEMP_FILE_PERMS = PosixFilePermissions.fromString("rw-------");

    private final UploadSecurityProperties uploadSecurityProperties;

    /**
     * {@code prefix} / {@code suffix} 로 임시 파일 생성. 설정된 디렉토리가 없으면 만들고, POSIX 권한 0644 강제.
     */
    public Path createTempFile(String prefix, String suffix) throws IOException {
        String configured = uploadSecurityProperties.tempDir();
        Path tempFile;
        if (configured == null || configured.isBlank()) {
            tempFile = Files.createTempFile(prefix, suffix);
        } else {
            Path dir = Path.of(configured).toAbsolutePath().normalize();
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            tempFile = Files.createTempFile(dir, prefix, suffix);
        }
        // S3.2 (K9) — 업로드 임시 파일은 0600 (owner-only) 강제. POSIX 미지원 OS 면 silent no-op.
        try {
            if (Files.getFileStore(tempFile).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(tempFile, TEMP_FILE_PERMS);
            }
        } catch (UnsupportedOperationException | IOException ignored) {
            // best-effort — 권한 적용 실패가 업로드 자체를 막지 않도록.
        }
        return tempFile;
    }
}
