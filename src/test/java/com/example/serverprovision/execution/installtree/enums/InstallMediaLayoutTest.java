package com.example.serverprovision.execution.installtree.enums;

import com.example.serverprovision.execution.installtree.FakeRockyTree;
import com.example.serverprovision.execution.installtree.FakeUbuntuIso;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.management.os.service.iso.IsoPreparationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 계열별 매체 모양: 꺼낼 범위 · 필요 공간 · 판별 · 판별 실패 사유 · 디렉터리 등록 허용. */
class InstallMediaLayoutTest {

    private static final InstallMediaLayout RHEL = InstallMediaLayout.RHEL_TREE;
    private static final InstallMediaLayout UBUNTU = InstallMediaLayout.UBUNTU_LIVE_SERVER;

    @TempDir
    Path temp;

    @Test
    @DisplayName("RHEL 은 ISO 전체를 풀고, Ubuntu 는 커널 · initrd · .disk/info 만 꺼낸다")
    void extractionScope() {
        IsoPreparationService iso = mock(IsoPreparationService.class);
        Path file = temp.resolve("a.iso");
        Path staging = temp.resolve("st");

        RHEL.extract(iso, file, staging);
        verify(iso).extractInto(file, staging);

        UBUNTU.extract(iso, file, staging);
        verify(iso).extractMembers(file, staging, List.of("casper/vmlinuz", "casper/initrd", ".disk/info"));
    }

    @Test
    @DisplayName("필요 공간: RHEL 은 ISO × 1.2, Ubuntu 는 ISO 크기와 무관한 512 MiB")
    void requiredSpace() {
        assertThat(RHEL.requiredSpace(1000L)).isEqualTo(1200L);
        assertThat(UBUNTU.requiredSpace(1000L)).isEqualTo(512L * 1024 * 1024);
        assertThat(UBUNTU.requiredSpace(3L * 1024 * 1024 * 1024)).isEqualTo(512L * 1024 * 1024);
    }

    @Test
    @DisplayName("디렉터리로 등록된 ISO: RHEL 은 받고, Ubuntu 는 DIRECTORY_ISO_UNSUPPORTED")
    void directorySource() {
        assertThat(RHEL.directorySourceRefusal()).isEmpty();
        assertThat(UBUNTU.directorySourceRefusal()).contains(TreeFailureReason.DIRECTORY_ISO_UNSUPPORTED);
    }

    @Test
    @DisplayName("커널 · initrd 경로와 판별 실패 사유")
    void bootFilesAndReason() {
        assertThat(RHEL.kernel()).isEqualTo("images/pxeboot/vmlinuz");
        assertThat(RHEL.initrd()).isEqualTo("images/pxeboot/initrd.img");
        assertThat(RHEL.notRecognized()).isEqualTo(TreeFailureReason.NOT_RHEL_TREE);
        assertThat(UBUNTU.kernel()).isEqualTo("casper/vmlinuz");
        assertThat(UBUNTU.initrd()).isEqualTo("casper/initrd");
        assertThat(UBUNTU.notRecognized()).isEqualTo(TreeFailureReason.NOT_UBUNTU_SERVER_ISO);
    }

    @Test
    @DisplayName("Ubuntu 판별: live-server amd64 면 배포판 Ubuntu · 점 버전을 돌려준다")
    void ubuntuInspect() throws IOException {
        FakeUbuntuIso.writeTree(temp, FakeUbuntuIso.SERVER_2404);
        assertThat(UBUNTU.inspect(temp)).contains(new TreeRelease("Ubuntu", "24.04.1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"casper/vmlinuz", "casper/initrd", ".disk/info"})
    @DisplayName("Ubuntu 판별: 커널 · initrd · .disk/info 중 하나라도 없으면 empty")
    void ubuntuMissingFile(String missing) throws IOException {
        FakeUbuntuIso.writeTree(temp, FakeUbuntuIso.SERVER_2404);
        Files.delete(temp.resolve(missing));
        assertThat(UBUNTU.inspect(temp)).isEmpty();
    }

    @Test
    @DisplayName("Ubuntu 판별: desktop · arm64 ISO 는 empty")
    void ubuntuWrongFlavor() throws IOException {
        FakeUbuntuIso.writeTree(temp.resolve("d"), FakeUbuntuIso.DESKTOP_2404);
        FakeUbuntuIso.writeTree(temp.resolve("a"), FakeUbuntuIso.SERVER_2404_ARM);
        assertThat(UBUNTU.inspect(temp.resolve("d"))).isEmpty();
        assertThat(UBUNTU.inspect(temp.resolve("a"))).isEmpty();
    }

    @Test
    @DisplayName("서로의 매체는 판별하지 않는다: Rocky 트리는 Ubuntu 가 아니고 Ubuntu 트리는 RHEL 이 아니다")
    void crossFamily() throws IOException {
        FakeRockyTree.writeTree(Files.createDirectories(temp.resolve("rocky")), "Rocky Linux", "9.5");
        FakeUbuntuIso.writeTree(temp.resolve("ubuntu"), FakeUbuntuIso.SERVER_2404);
        assertThat(UBUNTU.inspect(temp.resolve("rocky"))).isEmpty();
        assertThat(RHEL.inspect(temp.resolve("ubuntu"))).isEmpty();
    }
}
