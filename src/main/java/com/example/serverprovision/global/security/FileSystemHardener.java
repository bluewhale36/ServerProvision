package com.example.serverprovision.global.security;

import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 업로드 후 디스크에 저장된 파일/디렉토리의 POSIX 권한을 명시적으로 적용 (S3 v2 § 2.5.3).
 * <p>의도치 않은 실행 권한 차단. POSIX 가 지원되지 않는 OS (Windows) 에서는 silent no-op.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileSystemHardener {

    private static final Set<PosixFilePermission> FILE_PERMS = PosixFilePermissions.fromString("rw-r--r--");
    private static final Set<PosixFilePermission> DIR_PERMS = PosixFilePermissions.fromString("rwxr-xr-x");

    private final FileSystemSecurityProperties fileSystemSecurityProperties;

    public void applyDefaultPermissions(Path treeRoot) {
        if (treeRoot == null) return;
        if (!Files.exists(treeRoot)) return;
        if (!supportsPosix(treeRoot)) return;
        // S3.1 (A3) — maxDepth 인자 명시. follow-links 옵션 부재 = symlink 미추적 (default).
        // S3.4 (K11) — 단일 walk 통합. 기존엔 max-depth walk 적용 후 (max+1)-walk probe 로 초과 항목을 별도 카운트했지만
        // 큰 트리에서 IO 비용이 2배. probe 깊이로 한 번만 walk 하여 max 초과 항목은 권한 적용을 skip 하고
        // AtomicInteger 로만 누적, 종료 시 log.warn 로 보고한다.
        int max = fileSystemSecurityProperties.maxDepth();
        AtomicInteger overDepthCount = new AtomicInteger(0);
        try (Stream<Path> stream = Files.walk(treeRoot, max + 1)) {
            stream.forEach(p -> {
                int rel = treeRoot.relativize(p).getNameCount();
                if (rel > max) {
                    overDepthCount.incrementAndGet();
                    return;
                }
                applyOne(p);
            });
        } catch (IOException e) {
            log.warn("[security] hardener walk 실패 : {} ({})", treeRoot, e.getMessage());
        }
        int deeper = overDepthCount.get();
        if (deeper > 0) {
            log.warn("[security] hardener maxDepth({}) 초과 항목 {} 개 — 일부 파일 권한 미적용. tree={}",
                    max, deeper, treeRoot);
        }
    }

    public void applyDefaultPermissionsForFile(Path file) {
        if (file == null || !Files.exists(file)) return;
        if (!supportsPosix(file)) return;
        applyOne(file);
    }

    private void applyOne(Path p) {
        try {
            if (Files.isDirectory(p)) {
                Files.setPosixFilePermissions(p, DIR_PERMS);
            } else {
                Files.setPosixFilePermissions(p, FILE_PERMS);
            }
        } catch (UnsupportedOperationException | IOException e) {
            log.debug("[security] 권한 set 실패 (무시) : {} ({})", p, e.getMessage());
        }
    }

    private boolean supportsPosix(Path p) {
        try {
            return Files.getFileStore(p).supportsFileAttributeView("posix");
        } catch (IOException e) {
            return false;
        }
    }
}
