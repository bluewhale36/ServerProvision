package com.example.serverprovision.global.asset;

import com.example.serverprovision.global.history.AssetVersionKey;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * E1-I-3-c — {@link AtomicAssetSwap} 부품 검증(진단 활성화 코어에서 추출한 archive→ATOMIC_MOVE→권한 + restore).
 * 실제 이력 페이크로 archive 바이트 보관과 복원 정합을 확인하고, 최초 적용(target 부재)에서 archive 가 생략되는
 * 계약을 못 박는다.
 */
class AtomicAssetSwapTest {

    @TempDir
    Path work;    // target·staged 가 함께 사는 같은 파일시스템(ATOMIC_MOVE 성립 전제)
    @TempDir
    Path store;   // 이력 store(자산 루트 밖)

    private final AssetVersionKey key = new AssetVersionKey("PXE", "fragment.conf");

    private FakeAssetHistoryService history;
    private AtomicAssetSwap swap;

    @BeforeEach
    void setUp() {
        history = new FakeAssetHistoryService(store);
        FileSystemHardener hardener = new FileSystemHardener(mock(FileSystemSecurityProperties.class));
        swap = new AtomicAssetSwap(history, hardener);
    }

    @Test
    @DisplayName("swap — target 존재 시 옛 본을 archive 하고 staged 로 원자 교체")
    void swap_existingTarget_archivesAndReplaces() throws IOException {
        Path target = Files.writeString(work.resolve("fragment.conf"), "OLD");
        Path staged = Files.writeString(work.resolve("fragment.conf.tmp"), "NEW");

        SwapResult result = swap.swap(target, staged, key);

        assertThat(Files.readString(target)).isEqualTo("NEW");        // 활성본 = staged
        assertThat(Files.exists(staged)).isFalse();                  // staged 는 move 로 소진
        assertThat(result.archivedVersionId()).isPresent();          // 옛 본 archive
        assertThat(result.archivedPath()).isPresent();
        assertThat(Files.readString(result.archivedPath().get())).isEqualTo("OLD");  // 옛 바이트 보존
    }

    @Test
    @DisplayName("swap — 최초 적용(target 부재)이면 archive 없이 새 파일만 앉힌다")
    void swap_firstApply_noArchive() throws IOException {
        Path target = work.resolve("fragment.conf");                 // 아직 없음
        Path staged = Files.writeString(work.resolve("fragment.conf.tmp"), "FIRST");

        SwapResult result = swap.swap(target, staged, key);

        assertThat(Files.readString(target)).isEqualTo("FIRST");
        assertThat(result.archivedVersionId()).isEmpty();
        assertThat(result.archivedPath()).isEmpty();
        assertThat(history.archiveCount()).isZero();                 // archive 미호출
    }

    @Test
    @DisplayName("restore — 이력 바이트를 target 으로 원자 복원(이력 파일은 move 하지 않음)")
    void restore_restoresBytes() throws IOException {
        Path target = Files.writeString(work.resolve("fragment.conf"), "NEW");
        Path archived = Files.writeString(store.resolve("prev"), "PREVIOUS");

        swap.restore(target, archived);

        assertThat(Files.readString(target)).isEqualTo("PREVIOUS");
        assertThat(Files.exists(archived)).isTrue();                 // 이력 원본은 복사(보존)
    }
}
