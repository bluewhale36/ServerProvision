package com.example.serverprovision.management.os.service;

import com.example.serverprovision.management.os.exception.ExtractionFailedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

/**
 * ISO 경로를 탐색 가능한 상태로 준비해 주는 서비스.
 * <ul>
 *   <li>HTTP(S) URL → 통과</li>
 *   <li>디렉토리 → 통과 (이미 마운트된 상태로 간주)</li>
 *   <li>.iso 파일 → {@code mount -o loop,ro} 시도 → 실패 시 {@code 7z} / {@code bsdtar} 로 폴백</li>
 * </ul>
 * 반환되는 {@link PreparedIsoPath} 는 try-with-resources 종료 시점에 마운트 해제 + 임시 디렉토리 정리를 보장한다.
 */
@Slf4j
@Service
public class IsoPreparationService {

    /**
     * 탐색 가능한 경로 + 정리 콜백 쌍.
     * {@code close()} 는 idempotent 해야 하므로 통과 케이스는 {@link #passthrough(String)} 로 no-op 콜백을 걸어둔다.
     */
    public record PreparedIsoPath(String effectivePath, Runnable cleanup) implements AutoCloseable {

        @Override
        public void close() {
            cleanup.run();
        }

        public static PreparedIsoPath passthrough(String path) {
            return new PreparedIsoPath(path, () -> {});
        }
    }

    public PreparedIsoPath prepare(String isoPath) {
        if (isoPath == null || isoPath.isBlank()) {
            throw new ExtractionFailedException("ISO 경로가 지정되지 않았습니다.");
        }

        // HTTP(S) URL 은 사전 접속 검증 없이 통과한다 (실패는 전략 내부에서 명시적 메시지로 변환됨).
        if (isoPath.startsWith("http://") || isoPath.startsWith("https://")) {
            return PreparedIsoPath.passthrough(isoPath);
        }

        Path path;
        try {
            path = Paths.get(isoPath);
        } catch (Exception e) {
            throw new ExtractionFailedException("ISO 경로 형식이 올바르지 않습니다: " + isoPath, e);
        }

        if (!Files.exists(path)) {
            throw new ExtractionFailedException("ISO 경로가 실재하지 않습니다: " + isoPath + " — 파일 또는 디렉토리 경로를 다시 확인하세요.");
        }

        if (Files.isDirectory(path)) {
            log.debug("[IsoPreparationService] 마운트된 디렉토리로 인식. path={}", isoPath);
            return PreparedIsoPath.passthrough(isoPath);
        }

        if (Files.isRegularFile(path)) {
            if (!isoPath.toLowerCase().endsWith(".iso")) {
                throw new ExtractionFailedException("지정한 경로가 .iso 파일이 아닙니다: " + isoPath + " — ISO 이미지 파일(.iso) 또는 마운트된 디렉토리 경로를 지정하세요.");
            }
            log.info("[IsoPreparationService] .iso 파일 — 마운트/압축 해제를 시도합니다. path={}", isoPath);
            return prepareFromIsoFile(path);
        }

        // 심볼릭 링크 미해결 / FIFO / 소켓 등 처리 불가 유형
        throw new ExtractionFailedException("인식할 수 없는 ISO 경로 타입입니다: " + isoPath + " — 일반 파일(.iso) 또는 디렉토리만 지원합니다.");
    }

    private PreparedIsoPath prepareFromIsoFile(Path isoFile) {
        try {
            return mountIso(isoFile);
        } catch (Exception e) {
            log.warn("[IsoPreparationService] loop 마운트 실패, 압축 해제로 폴백합니다. iso={}, 원인={}", isoFile, e.getMessage());
        }
        return extractIso(isoFile);
    }

    private PreparedIsoPath mountIso(Path isoFile) throws IOException, InterruptedException {
        Path mountDir = Files.createTempDirectory("iso_mount_");
        log.info("[IsoPreparationService] loop 마운트 시도. iso={}, mountDir={}", isoFile, mountDir);

        Process proc = new ProcessBuilder("mount", "-o", "loop,ro", isoFile.toString(), mountDir.toString())
                .redirectErrorStream(true)
                .start();
        int exitCode = proc.waitFor();

        if (exitCode != 0) {
            String stderr = new String(proc.getInputStream().readAllBytes()).trim();
            Files.deleteIfExists(mountDir);
            throw new IOException("mount 실패 (exit=" + exitCode + "): " + stderr);
        }

        log.info("[IsoPreparationService] loop 마운트 성공. mountDir={}", mountDir);
        return new PreparedIsoPath(mountDir.toString(), () -> unmountAndClean(mountDir));
    }

    private PreparedIsoPath extractIso(Path isoFile) {
        try {
            Path extractDir = Files.createTempDirectory("iso_extract_");
            log.info("[IsoPreparationService] ISO 압축 해제 시도. iso={}, extractDir={}", isoFile, extractDir);

            boolean extracted = runExtract7z(isoFile, extractDir)
                    || runExtractBsdtar(isoFile, extractDir);

            if (!extracted) {
                deleteDirectory(extractDir);
                throw new ExtractionFailedException(
                        "ISO 파일을 열 수 없습니다: " + isoFile + " — loop 마운트 실패 후 7z/bsdtar 폴백도 실패했습니다. 마운트 권한 또는 7z/bsdtar 설치 상태를 확인하세요."
                );
            }

            log.info("[IsoPreparationService] ISO 압축 해제 성공. extractDir={}", extractDir);
            return new PreparedIsoPath(extractDir.toString(), () -> deleteDirectory(extractDir));

        } catch (IOException | InterruptedException e) {
            throw new ExtractionFailedException(
                    "ISO 압축 해제 중 오류: " + e.getMessage(), e);
        }
    }

    private boolean runExtract7z(Path isoFile, Path extractDir) throws IOException, InterruptedException {
        if (!isCommandAvailable("7z")) return false;
        Process proc = new ProcessBuilder("7z", "x", isoFile.toString(), "-o" + extractDir, "-y")
                .redirectErrorStream(true)
                .start();
        return proc.waitFor() == 0;
    }

    private boolean runExtractBsdtar(Path isoFile, Path extractDir) throws IOException, InterruptedException {
        if (!isCommandAvailable("bsdtar")) return false;
        Process proc = new ProcessBuilder("bsdtar", "-xf", isoFile.toString(), "-C", extractDir.toString())
                .redirectErrorStream(true)
                .start();
        return proc.waitFor() == 0;
    }

    private boolean isCommandAvailable(String command) {
        try {
            return new ProcessBuilder("which", command)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void unmountAndClean(Path mountDir) {
        try {
            log.info("[IsoPreparationService] loop 마운트 해제. mountDir={}", mountDir);
            new ProcessBuilder("umount", mountDir.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (Exception e) {
            log.warn("[IsoPreparationService] 마운트 해제 실패. mountDir={}, 원인={}", mountDir, e.getMessage());
        } finally {
            deleteDirectory(mountDir);
        }
    }

    private void deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("[IsoPreparationService] 파일 삭제 실패. path={}", p);
                }
            });
        } catch (IOException e) {
            log.warn("[IsoPreparationService] 임시 디렉토리 삭제 실패. dir={}, 원인={}", dir, e.getMessage());
        }
    }
}
