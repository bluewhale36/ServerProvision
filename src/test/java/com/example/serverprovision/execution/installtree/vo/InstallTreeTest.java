package com.example.serverprovision.execution.installtree.vo;

import com.example.serverprovision.execution.installtree.FakeRockyTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** b-4 서빙의 경로 가드 {@link InstallTree#resolve} 와 {@link TreeRelease} 계약. */
class InstallTreeTest {

    @TempDir
    Path temp;
    private InstallTree tree;

    @BeforeEach
    void setUp() throws IOException {
        Path root = FakeRockyTree.writeTree(Files.createDirectories(temp.resolve("iso-12")), "Rocky Linux", "9.5");
        Files.writeString(root.resolve(".provision.json"), "{\"marker\":1}");
        Files.writeString(temp.resolve("secret.txt"), "outside");
        tree = new InstallTree(root, new TreeRelease("Rocky Linux", "9.5"));
    }

    @Test
    @DisplayName("트리 안 일반 파일과 커널 · initrd 는 실제 경로로 해석된다")
    void resolvesRegularFiles() {
        assertThat(tree.resolve("images/pxeboot/vmlinuz")).isPresent();
        assertThat(tree.resolve("BaseOS/repodata/repomd.xml")).isPresent();
        assertThat(tree.kernelPath()).isRegularFile();
        assertThat(tree.initrdPath()).isRegularFile();
    }

    @Test
    @DisplayName("최상위 트리 설명서(.treeinfo · .discinfo)만 숨김 파일 중 내준다")
    void servesTreeDescriptors() {
        assertThat(tree.resolve(".treeinfo")).isPresent();
        assertThat(tree.resolve(".discinfo")).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            ".provision.json", "BaseOS/.treeinfo", "../secret.txt", "BaseOS/../../secret.txt", "/etc/passwd",
            "images\\pxeboot\\vmlinuz", "images//pxeboot/vmlinuz", "./images/pxeboot/vmlinuz", "images/pxeboot",
            "images/pxeboot/none", "", " "
    })
    @DisplayName("숨김 · 이탈 · 절대 · 역슬래시 · 빈 조각 · 디렉터리 · 없는 파일은 거절한다")
    void rejectsUnsafeOrMissing(String path) {
        assertThat(tree.resolve(path)).isEmpty();
    }

    @Test
    @DisplayName("null 경로는 거절한다")
    void rejectsNull() {
        assertThat(tree.resolve(null)).isEmpty();
    }

    @Test
    @DisplayName("트리 밖을 가리키는 심볼릭 링크는 거절하고, 트리 안을 가리키는 링크는 해석한다")
    void symlinks() throws IOException {
        Files.createSymbolicLink(tree.root().resolve("images/escape.img"), temp.resolve("secret.txt"));
        Files.createSymbolicLink(tree.root().resolve("images/alias.img"), tree.root().resolve("images/install.img"));
        assertThat(tree.resolve("images/escape.img")).isEmpty();
        assertThat(tree.resolve("images/alias.img")).isPresent();
    }

    @Test
    @DisplayName("TreeRelease: 주 버전 · 표기, 숫자 아닌 버전은 -1, 빈 값은 거절")
    void release() {
        assertThat(new TreeRelease("Rocky Linux", "9.5").majorVersion()).isEqualTo(9);
        assertThat(new TreeRelease("Rocky Linux", "10").majorVersion()).isEqualTo(10);
        assertThat(new TreeRelease("Rocky Linux", "Stream").majorVersion()).isEqualTo(-1);
        assertThat(new TreeRelease("Rocky Linux", "8.10").label()).isEqualTo("Rocky Linux 8.10");
        assertThatThrownBy(() -> new TreeRelease(" ", "9")).isInstanceOf(IllegalArgumentException.class);
    }
}
