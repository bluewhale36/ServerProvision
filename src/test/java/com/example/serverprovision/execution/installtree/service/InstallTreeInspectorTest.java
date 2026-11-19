package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.FakeRockyTree;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class InstallTreeInspectorTest {

    @TempDir
    Path temp;
    private final InstallTreeInspector inspector = new InstallTreeInspector();

    @Test
    @DisplayName("필수 파일이 모두 있으면 [release] 의 배포판 · 버전을 돌려준다")
    void completeTree() throws IOException {
        FakeRockyTree.writeTree(temp, "Rocky Linux", "9.5");
        assertThat(inspector.inspect(temp)).contains(new TreeRelease("Rocky Linux", "9.5"));
    }

    @ParameterizedTest
    @ValueSource(strings = {".treeinfo", "images/pxeboot/vmlinuz", "images/pxeboot/initrd.img",
            "images/install.img", "BaseOS/repodata/repomd.xml"})
    @DisplayName("필수 파일이 하나라도 없으면 RHEL 설치 트리가 아니다")
    void missingRequired(String missing) throws IOException {
        FakeRockyTree.writeTree(temp, "Rocky Linux", "9.5");
        Files.delete(temp.resolve(missing));
        assertThat(inspector.inspect(temp)).isEmpty();
    }

    @Test
    @DisplayName("[release] 가 없는 옛 형식은 [general] family · version 을 쓴다")
    void generalFallback() throws IOException {
        FakeRockyTree.writeTree(temp, "Rocky Linux", "8.10");
        Files.writeString(temp.resolve(".treeinfo"), "[general]\nfamily = Rocky Linux\nversion = 8.10\n");
        assertThat(inspector.inspect(temp)).contains(new TreeRelease("Rocky Linux", "8.10"));
    }

    @Test
    @DisplayName("[stage2] mainimage 가 가리키는 설치기 본체를 확인한다")
    void customStage2() throws IOException {
        FakeRockyTree.writeTree(temp, "Rocky Linux", "10.0");
        Files.writeString(temp.resolve(".treeinfo"),
                "[release]\nname = Rocky Linux\nversion = 10.0\n[stage2]\nmainimage = images/stage2.img\n");
        assertThat(inspector.inspect(temp)).isEmpty();
        Files.writeString(temp.resolve("images/stage2.img"), "s");
        assertThat(inspector.inspect(temp)).contains(new TreeRelease("Rocky Linux", "10.0"));
    }

    @Test
    @DisplayName("배포판 · 버전을 읽을 수 없으면 empty")
    void noRelease() throws IOException {
        FakeRockyTree.writeTree(temp, "Rocky Linux", "9.5");
        Files.writeString(temp.resolve(".treeinfo"), "# comment\n[header]\nversion = 1.2\n");
        assertThat(inspector.inspect(temp)).isEmpty();
    }
}
