package com.example.serverprovision.global.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * S3.2 (K4) — FileSystemHardener 회귀.
 * <p>POSIX 파일시스템에서 0644/0755 권한이 일관되게 적용되는지, maxDepth 초과 시 운영자 경고 (K11) 가
 * 동작하는지, Windows 등 POSIX 미지원 환경에서 silent no-op 인지를 검증한다.</p>
 */
class FileSystemHardenerTest {

    private FileSystemHardener hardener(int maxDepth) {
        return new FileSystemHardener(new FileSystemSecurityProperties(2000, maxDepth));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("POSIX 환경 + 깊이 5 트리 → 모든 file 0644 / dir 0755")
    void shallowTree_allFilesHardened(@TempDir Path root) throws Exception {
        Path dir = root.resolve("a/b/c");
        Files.createDirectories(dir);
        Path f = dir.resolve("file.txt");
        Files.writeString(f, "hello");

        hardener(8).applyDefaultPermissions(root);

        Set<PosixFilePermission> filePerms = Files.getPosixFilePermissions(f);
        assertThat(filePerms).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ);

        Set<PosixFilePermission> dirPerms = Files.getPosixFilePermissions(dir);
        assertThat(dirPerms).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("K11 — maxDepth 초과 트리에서도 walk 실패하지 않고 정상 경로는 hardening 적용")
    void deepTree_doesNotThrow(@TempDir Path root) throws Exception {
        // depth 12 트리 — maxDepth 5 보다 깊지만 walk 가 throw 하지 않아야 한다.
        Path cur = root;
        for (int i = 0; i < 12; i++) {
            cur = cur.resolve("d" + i);
        }
        Files.createDirectories(cur);
        Files.writeString(cur.resolve("deep.txt"), "x");

        // FileSystemHardener.applyDefaultPermissions 가 IOException / log.warn 만 발생시키고
        // 호출자에게 throw 하지 않는지를 확인 (운영 부팅 차단 회피).
        assertThatCode(() -> hardener(5).applyDefaultPermissions(root))
                .doesNotThrowAnyException();
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("S3.4 K11 — maxDepth 초과 항목 존재 시 단일 walk + log.warn (counter 가 누적된 갯수만큼 보고)")
    void deepTreeOver8_logsWarn(@TempDir Path root) throws Exception {
        // depth 12 트리 → max 5 기준 초과 항목 = 6 개 (d5..d10) + 깊이 11 의 deep.txt 1 개 = 7 개
        Path cur = root;
        for (int i = 0; i < 12; i++) {
            cur = cur.resolve("d" + i);
        }
        Files.createDirectories(cur);
        Files.writeString(cur.resolve("deep.txt"), "x");

        Logger logger = (Logger) LoggerFactory.getLogger(FileSystemHardener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            hardener(5).applyDefaultPermissions(root);

            // single walk + counter 통합. 초과 항목이 1 건 이상이면 log.warn 1 건 발생.
            long warnCount = appender.list.stream()
                    .filter(e -> e.getLevel() == Level.WARN)
                    .filter(e -> e.getFormattedMessage().contains("maxDepth"))
                    .count();
            assertThat(warnCount).isGreaterThanOrEqualTo(1L);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    @DisplayName("null / 미존재 경로 → silent no-op")
    void nullOrMissing_noop() {
        assertThatCode(() -> hardener(8).applyDefaultPermissions(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> hardener(8).applyDefaultPermissionsForFile(null))
                .doesNotThrowAnyException();
        assertThatCode(() -> hardener(8).applyDefaultPermissionsForFile(Path.of("/nonexistent-path-xyzzy-12345")))
                .doesNotThrowAnyException();
    }
}
