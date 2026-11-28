package com.example.serverprovision.execution.installtree.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 설정 검증과 다른 루트와의 겹침 fail-fast. */
class InstallTreeConfigTest {

    private static final String TREE = "/var/lib/serverprovision/served/install-trees";

    @Test
    @DisplayName("빈 루트 = 꺼짐, 유예 기본 10 분")
    void disabledByDefault() {
        InstallTreeProperties props = new InstallTreeProperties("", null);
        assertThat(props.configured()).isFalse();
        assertThat(props.treeRootPath()).isEmpty();
        assertThat(props.releaseGrace()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName("끝 슬래시는 정규화해 받는다")
    void trailingSlash() {
        assertThat(new InstallTreeProperties(TREE + "/", null).treeRootPath()).contains(Path.of(TREE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"relative/trees", "/", "/var/lib/../tmp/trees"})
    @DisplayName("상대경로 · 루트 · '..' 경로는 기동 실패")
    void invalidRoots(String root) {
        assertThatThrownBy(() -> new InstallTreeProperties(root, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INSTALL_TREE_ROOT");
    }

    @Test
    @DisplayName("음수 유예는 기동 실패")
    void negativeGrace() {
        assertThatThrownBy(() -> new InstallTreeProperties(TREE, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            TREE,                                         // 같다
            "/var/lib/serverprovision/served",            // 트리 루트를 포함한다
            TREE + "/nested"                              // 트리 루트 안에 있다
    })
    @DisplayName("다른 루트와 같거나 서로 포함하면 기동 실패")
    void overlapFailsFast(String other) {
        InstallTreeConfig config = config(TREE, "/srv/a," + other, "", "", "", "", "");
        assertThatThrownBy(config::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("provision.path.allowed-roots")
                .hasMessageContaining("INSTALL_TREE_ROOT");
    }

    @Test
    @DisplayName("각 루트 키를 모두 검사한다(PXE 자산 · 휴지통 · 격리 · 이력 · Windows 원본)")
    void everyRootIsChecked() {
        assertThatThrownBy(config(TREE, "", TREE, "", "", "", "")::validate).hasMessageContaining("pxe.assets.root");
        assertThatThrownBy(config(TREE, "", "", TREE, "", "", "")::validate).hasMessageContaining("trash.root");
        assertThatThrownBy(config(TREE, "", "", "", TREE, "", "")::validate).hasMessageContaining("orphan.quarantine.root");
        assertThatThrownBy(config(TREE, "", "", "", "", TREE, "")::validate).hasMessageContaining("provision.asset.history.root");
        assertThatThrownBy(config(TREE, "", "", "", "", "", TREE)::validate).hasMessageContaining("provision.windows-install.source-root");
    }

    @Test
    @DisplayName("형제 루트(served/resources · served/pxe)는 통과, 꺼짐이면 검사하지 않음")
    void siblingsPass() {
        assertThatCode(config(TREE, "/var/lib/serverprovision/served/resources", "/var/lib/serverprovision/served/pxe",
                "/var/lib/serverprovision/internal/trash", "", "", "")::validate).doesNotThrowAnyException();
        assertThatCode(config("", TREE, TREE, "", "", "", "")::validate).doesNotThrowAnyException();
    }

    private static InstallTreeConfig config(String tree, String allowed, String assets, String trash,
                                            String quarantine, String history, String windows) {
        return new InstallTreeConfig(new InstallTreeProperties(tree, null), allowed, assets, trash, quarantine, history, windows);
    }
}
