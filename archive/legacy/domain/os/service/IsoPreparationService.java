package com.example.serverprovision.domain.os.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

// ISO 파일이 마운트되지 않은 채로 .iso 단일 파일로만 존재하는 경우를 처리하는 서비스.
// loop 마운트를 우선 시도하고, 실패 시 7z / bsdtar 압축 해제로 폴백한다.
// PreparedIsoPath 는 AutoCloseable 이므로 try-with-resources 로 정리를 보장한다.
@Slf4j
@Service
public class IsoPreparationService {

    // ISO 접근 준비 결과 — effectivePath 로 파일 시스템에 접근하고, close() 로 정리한다
    public record PreparedIsoPath(String effectivePath, Runnable cleanup) implements AutoCloseable {

        @Override
        public void close() {
            cleanup.run();
        }

        // 정리 불필요 경로 (HTTP URL, 이미 마운트된 디렉토리 등)
        public static PreparedIsoPath passthrough(String path) {
            return new PreparedIsoPath(path, () -> {});
        }
    }

    /**
     * isoMountPath 유형을 판별하여 실제 접근 가능한 경로를 반환한다.
     *
     * - HTTP(S) URL → 그대로 반환 (전략이 직접 처리)
     * - 디렉토리    → 그대로 반환 (이미 마운트된 상태)
     * - .iso 파일  → loop 마운트 또는 압축 해제 후 임시 경로 반환
     */
    public PreparedIsoPath prepare(String isoMountPath) {
        if (isoMountPath.startsWith("http://") || isoMountPath.startsWith("https://")) {
            return PreparedIsoPath.passthrough(isoMountPath);
        }

        Path path = Paths.get(isoMountPath);

        if (Files.isDirectory(path)) {
            log.debug("[IsoPreparationService] 마운트된 디렉토리 확인. path={}", isoMountPath);
            return PreparedIsoPath.passthrough(isoMountPath);
        }

        if (Files.isRegularFile(path) && isoMountPath.toLowerCase().endsWith(".iso")) {
            log.info("[IsoPreparationService] .iso 파일 감지 — 마운트 또는 압축 해제를 시작합니다. path={}", isoMountPath);
            return prepareFromIsoFile(path);
        }

        // 판별 불가 경로는 전략에 위임 (오류는 전략 내부에서 발생)
        log.warn("[IsoPreparationService] 경로 유형 판별 불가 — 그대로 전달합니다. path={}", isoMountPath);
        return PreparedIsoPath.passthrough(isoMountPath);
    }

    // .iso 파일 → 1순위: loop 마운트, 2순위: 압축 해제
    private PreparedIsoPath prepareFromIsoFile(Path isoFile) {
        try {
            return mountIso(isoFile);
        } catch (Exception e) {
            log.warn("[IsoPreparationService] loop 마운트 실패, 압축 해제로 폴백합니다. iso={}, 원인={}", isoFile, e.getMessage());
        }
        return extractIso(isoFile);
    }

    // mount -o loop,ro <iso> <tmpdir>
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

    // 7z 또는 bsdtar 를 사용하여 ISO 내용을 임시 디렉토리에 압축 해제
    private PreparedIsoPath extractIso(Path isoFile) {
        try {
            Path extractDir = Files.createTempDirectory("iso_extract_");
            log.info("[IsoPreparationService] ISO 압축 해제 시도. iso={}, extractDir={}", isoFile, extractDir);

            boolean extracted = runExtract7z(isoFile, extractDir)
                    || runExtractBsdtar(isoFile, extractDir);

            if (!extracted) {
                deleteDirectory(extractDir);
                throw new IllegalStateException(
                        "7z 및 bsdtar 모두 사용 불가하거나 실패했습니다. path=" + isoFile);
            }

            log.info("[IsoPreparationService] ISO 압축 해제 성공. extractDir={}", extractDir);
            return new PreparedIsoPath(extractDir.toString(), () -> deleteDirectory(extractDir));

        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("ISO 압축 해제 중 오류 발생: " + e.getMessage(), e);
        }
    }

    // 7z x <iso> -o<extractDir> -y
    private boolean runExtract7z(Path isoFile, Path extractDir) throws IOException, InterruptedException {
        if (!isCommandAvailable("7z")) {
            log.debug("[IsoPreparationService] 7z 명령을 찾을 수 없습니다.");
            return false;
        }
        log.debug("[IsoPreparationService] 7z 압축 해제 실행. iso={}", isoFile);
        Process proc = new ProcessBuilder(
                "7z", "x", isoFile.toString(), "-o" + extractDir, "-y")
                .redirectErrorStream(true)
                .start();
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            log.debug("[IsoPreparationService] 7z 실패 (exit={})", exitCode);
        }
        return exitCode == 0;
    }

    // bsdtar -xf <iso> -C <extractDir>
    private boolean runExtractBsdtar(Path isoFile, Path extractDir) throws IOException, InterruptedException {
        if (!isCommandAvailable("bsdtar")) {
            log.debug("[IsoPreparationService] bsdtar 명령을 찾을 수 없습니다.");
            return false;
        }
        log.debug("[IsoPreparationService] bsdtar 압축 해제 실행. iso={}", isoFile);
        Process proc = new ProcessBuilder(
                "bsdtar", "-xf", isoFile.toString(), "-C", extractDir.toString())
                .redirectErrorStream(true)
                .start();
        int exitCode = proc.waitFor();
        if (exitCode != 0) {
            log.debug("[IsoPreparationService] bsdtar 실패 (exit={})", exitCode);
        }
        return exitCode == 0;
    }

    // which <command> 로 커맨드 존재 여부 확인
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

    // umount 후 임시 마운트 디렉토리 삭제
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

    // 임시 디렉토리 재귀 삭제 (깊이 우선)
    private void deleteDirectory(Path dir) {
        if (dir == null || !Files.exists(dir)) return;
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            log.warn("[IsoPreparationService] 파일 삭제 실패. path={}", p);
                        }
                    });
            log.debug("[IsoPreparationService] 임시 디렉토리 삭제 완료. dir={}", dir);
        } catch (IOException e) {
            log.warn("[IsoPreparationService] 임시 디렉토리 삭제 실패. dir={}, 원인={}", dir, e.getMessage());
        }
    }
}
