package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.FakeRockyTree;
import com.example.serverprovision.execution.installtree.config.InstallTreeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 실제 파일 시스템 위의 게시 · 회수 · 삭제 · 기동 정리. */
class InstallTreeStoreTest {

    @TempDir
    Path temp;
    private Path root;
    private InstallTreeStore store;

    @BeforeEach
    void setUp() {
        root = temp.resolve("install-trees");
        store = new InstallTreeStore(new InstallTreeProperties(root.toString(), null));
    }

    @Test
    @DisplayName("staging 은 .staging/<uuid>, 게시는 iso-<id> 로 rename 되고 staging 은 사라진다")
    void publish() throws IOException {
        Path staging = store.newStagingDir();
        assertThat(staging.getParent()).isEqualTo(root.resolve(".staging"));
        FakeRockyTree.writeTree(staging, "Rocky Linux", "9.5");

        Path published = store.publish(staging, 12L);

        assertThat(published).isEqualTo(root.resolve("iso-12"));
        assertThat(published.resolve("images/pxeboot/vmlinuz")).isRegularFile();
        assertThat(staging).doesNotExist();
    }

    @Test
    @DisplayName("같은 id 의 옛 트리가 남아 있으면 지우고 새 트리로 바꾼다")
    void publishReplacesLeftover() throws IOException {
        Files.createDirectories(root.resolve("iso-12/old"));
        Path staging = store.newStagingDir();
        Files.writeString(staging.resolve("new.txt"), "n");

        store.publish(staging, 12L);

        assertThat(root.resolve("iso-12/new.txt")).isRegularFile();
        assertThat(root.resolve("iso-12/old")).doesNotExist();
    }

    @Test
    @DisplayName("회수는 서빙 경로에서 즉시 빼고, 삭제는 읽기 전용(0555)으로 풀린 트리도 지운다")
    void retireAndDeleteReadOnlyTree() throws IOException {
        Path staging = store.newStagingDir();
        FakeRockyTree.writeTree(staging, "Rocky Linux", "9.5");
        Path tree = store.publish(staging, 7L);
        makeReadOnly(tree);

        Path retired = store.retire(tree);
        assertThat(tree).doesNotExist();
        assertThat(retired.getParent()).isEqualTo(root.resolve(".staging"));

        store.delete(retired);
        assertThat(retired).doesNotExist();
    }

    @Test
    @DisplayName("삭제는 심볼릭 링크를 따라가지 않는다 — 링크만 지우고 대상은 보존")
    void deleteDoesNotFollowLinks() throws IOException {
        Path outside = Files.createDirectories(temp.resolve("operator-data"));
        Files.writeString(outside.resolve("keep.txt"), "k");
        Path staging = store.newStagingDir();
        Files.createSymbolicLink(staging.resolve("link"), outside);

        store.delete(staging);

        assertThat(staging).doesNotExist();
        assertThat(outside.resolve("keep.txt")).isRegularFile();
    }

    @Test
    @DisplayName("기동 정리: 루트를 만들고, 직계 자식 중 iso-<숫자> · .staging 만 지운다")
    void cleanOnStartup() throws IOException {
        Path outside = Files.createDirectories(temp.resolve("outside"));
        Files.writeString(outside.resolve("keep.txt"), "k");
        Files.createDirectories(root.resolve("iso-3/images"));
        Files.createDirectories(root.resolve(".staging/half"));
        makeReadOnly(root.resolve(".staging/half"));
        Files.createDirectories(root.resolve("iso-abc"));
        Files.createDirectories(root.resolve("notes"));
        Files.createSymbolicLink(root.resolve("iso-9"), outside);

        InstallTreeStore.CleanupResult result = store.cleanOnStartup();

        assertThat(result).isEqualTo(new InstallTreeStore.CleanupResult(3, 0));
        try (Stream<Path> left = Files.list(root)) {
            assertThat(left.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("iso-abc", "notes");
        }
        assertThat(outside.resolve("keep.txt")).isRegularFile();
    }

    @Test
    @DisplayName("지우지 못한 항목은 removed 가 아니라 failed 로 센다(CP5 O-3)")
    void cleanCountsFailures() throws IOException {
        Path locked = Files.createDirectories(root.resolve("iso-4"));
        Files.createDirectories(locked.resolve("images"));
        Files.createDirectories(root.resolve("iso-8"));
        Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("r-xr-xr-x"));   // 루트에서 지울 수 없게
        try {
            assertThat(store.cleanOnStartup()).isEqualTo(new InstallTreeStore.CleanupResult(0, 2));
        } finally {
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwxr-xr-x"));
        }
        assertThat(store.delete(root.resolve("iso-4"))).isTrue();
        assertThat(store.delete(root.resolve("absent"))).isTrue();
    }

    @Test
    @DisplayName("기동 정리는 루트가 없으면 만든다")
    void cleanCreatesRoot() throws IOException {
        assertThat(store.cleanOnStartup()).isEqualTo(new InstallTreeStore.CleanupResult(0, 0));
        assertThat(root).isDirectory();
        assertThat(store.usableSpace()).isPositive();
    }

    @Test
    @DisplayName("꺼진 설정에서 저장소를 쓰면 프로그램 오류")
    void disabled() {
        InstallTreeStore off = new InstallTreeStore(new InstallTreeProperties("", null));
        assertThatThrownBy(() -> off.treeDir(1L)).isInstanceOf(IllegalStateException.class);
    }

    private static void makeReadOnly(Path tree) throws IOException {
        try (Stream<Path> walk = Files.walk(tree)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.setPosixFilePermissions(p, PosixFilePermissions.fromString(Files.isDirectory(p) ? "r-xr-xr-x" : "r--r--r--"));
            }
        }
    }
}
