package com.example.serverprovision.global.asset;

import com.example.serverprovision.global.history.AssetHistoryService;
import com.example.serverprovision.global.history.AssetVersionKey;
import com.example.serverprovision.global.history.entity.AssetVersion;
import com.example.serverprovision.global.security.FileSystemHardener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/**
 * 이미 실체화된 staged 파일을 target 으로 원자 교체하는 도메인 무관 부품. {@code DiagnosticAssetActivationService}
 * 의 활성화 코어 중간 3단(현재 target archive → ATOMIC_MOVE → 기본 권한)을 추출·일반화한다.
 *
 * <p><b>순서</b> = 현재 target archive(존재할 때만) → {@code ATOMIC_MOVE(staged→target, REPLACE_EXISTING)} →
 * 기본 권한. archive 실패면 스왑 전에 중단하고 staged 를 정리한다(fail-closed — 이전본을 보존하지 못한 채
 * 덮어쓰지 않는다). staged 는 target 과 같은 파일시스템(같은 디렉토리)이어야 ATOMIC_MOVE 가 성립하며 이는
 * 호출자 책임이다.</p>
 *
 * <p>재봉인은 하지 않는다(호출자 책임) — dhcpd 조각처럼 봉인 개념이 없는 자산도 이 부품을 쓰기 때문이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AtomicAssetSwap {

    private final AssetHistoryService historyService;
    private final FileSystemHardener fileSystemHardener;

    /**
     * staged 파일을 target 으로 원자 교체한다. target 이 이미 있으면 스왑 전에 archive 해 복원 가능성을 남긴다.
     *
     * @throws IOException ATOMIC_MOVE 실패(staged 는 정리됨)
     */
    public SwapResult swap(Path target, Path staged, AssetVersionKey key) throws IOException {
        Optional<AssetVersion> archived;
        try {
            archived = Files.exists(target)
                    ? historyService.archive(key, target)   // 현재 활성본 보관(실패 시 스왑 전 중단)
                    : Optional.empty();                       // 최초 적용 — archive 대상 없음
        } catch (RuntimeException e) {
            deleteQuietly(staged);
            throw e;
        }

        try {
            Files.move(staged, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(staged);
            throw e;
        }
        fileSystemHardener.applyDefaultPermissionsForFile(target);

        Optional<Long> versionId = archived.map(AssetVersion::getId);
        Optional<Path> archivedPath = versionId.map(id -> historyService.openVersion(key, id));
        return new SwapResult(versionId, archivedPath);
    }

    /**
     * 이전본(archivedPath) 바이트를 target 으로 원자 복원한다. 이력 파일 자체는 move 하지 않고 복사한다
     * (dangling 방지). temp 는 target 과 같은 디렉토리에 만들어 ATOMIC_MOVE 를 성립시킨다.
     *
     * @throws IOException 복사·이동 실패(temp 는 정리됨)
     */
    public void restore(Path target, Path archivedPath) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".restore-" + UUID.randomUUID() + ".tmp");
        try {
            Files.copy(archivedPath, temp, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temp, target,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(temp);
            throw e;
        }
        fileSystemHardener.applyDefaultPermissionsForFile(target);
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignore) {
            // 임시 파일 정리 실패는 무해 — 다음 스왑이 새 UUID 를 쓴다.
        }
    }
}
