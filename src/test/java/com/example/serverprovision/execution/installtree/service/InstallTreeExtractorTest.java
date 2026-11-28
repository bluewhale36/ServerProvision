package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.FakeRockyTree;
import com.example.serverprovision.execution.installtree.config.InstallTreeProperties;
import com.example.serverprovision.execution.installtree.enums.InstallMediaLayout;
import com.example.serverprovision.execution.installtree.enums.TreeFailureReason;
import com.example.serverprovision.execution.installtree.vo.TreeAcquisition;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.management.os.exception.ExtractionFailedException;
import com.example.serverprovision.management.os.service.iso.IsoPreparationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 추출 갈래: 성공 · 공간 부족 · 계열 매체 아님 · 도구 실패. 실패는 staging 을 남기지 않는다. RHEL 은 전체, Ubuntu 는 지정 파일만. */
class InstallTreeExtractorTest {

    private static final InstallMediaLayout RHEL = InstallMediaLayout.RHEL_TREE;
    private static final InstallMediaLayout UBUNTU = InstallMediaLayout.UBUNTU_LIVE_SERVER;

    @TempDir
    Path temp;
    private Path root;
    private Path iso;
    private IsoPreparationService isoPreparationService;
    private InstallTreeExtractor extractor;

    @BeforeEach
    void setUp() throws IOException {
        root = temp.resolve("trees");
        iso = Files.writeString(temp.resolve("Rocky-9.5.iso"), "iso-bytes");
        isoPreparationService = mock(IsoPreparationService.class);
        InstallTreeStore store = new InstallTreeStore(new InstallTreeProperties(root.toString(), null));
        extractor = new InstallTreeExtractor(isoPreparationService, store);
    }

    @Test
    @DisplayName("성공: staging 에 풀고 확인한 뒤 iso-<id> 로 게시한다")
    void success() throws Exception {
        doAnswer(inv -> FakeRockyTree.writeTree(inv.getArgument(1), "Rocky Linux", "9.5"))
                .when(isoPreparationService).extractInto(any(), any());

        TreeAcquisition result = extractor.extract(12L, iso, RHEL).get();

        assertThat(result).isInstanceOfSatisfying(TreeAcquisition.Ready.class, ready -> {
            assertThat(ready.tree().root()).isEqualTo(root.resolve("iso-12"));
            assertThat(ready.tree().release()).isEqualTo(new TreeRelease("Rocky Linux", "9.5"));
            assertThat(ready.tree().source()).isEqualTo(iso.toAbsolutePath().normalize());
        });
        assertThat(stagingChildren()).isEmpty();
    }

    @Test
    @DisplayName("RHEL 설치 트리가 아니면 NOT_RHEL_TREE · staging 삭제 · 게시 없음")
    void notRhelTree() {
        doAnswer(inv -> Files.writeString(((Path) inv.getArgument(1)).resolve("README"), "x"))
                .when(isoPreparationService).extractInto(any(), any());

        assertThat(extractor.extractNow(12L, iso, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_RHEL_TREE));
        assertThat(root.resolve("iso-12")).doesNotExist();
        assertThat(stagingChildren()).isEmpty();
    }

    @Test
    @DisplayName("도구 실패는 EXTRACTION_FAILED · 반쯤 풀린 staging 삭제")
    void toolFailure() {
        doAnswer(inv -> {
            Files.writeString(((Path) inv.getArgument(1)).resolve("partial"), "x");
            throw new ExtractionFailedException("bsdtar 실패");
        }).when(isoPreparationService).extractInto(any(), any());

        assertThat(extractor.extractNow(12L, iso, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED));
        assertThat(stagingChildren()).isEmpty();
    }

    @Test
    @DisplayName("ISO 크기 × 1.2 가 남은 공간보다 크면 풀지 않고 NOT_ENOUGH_SPACE (경계: 정확히 1.2 배면 통과)")
    void notEnoughSpace() throws IOException {
        InstallTreeStore smallDisk = mock(InstallTreeStore.class);
        InstallTreeExtractor tight = new InstallTreeExtractor(isoPreparationService, smallDisk);
        Files.write(iso, new byte[1000]);
        when(smallDisk.usableSpace()).thenReturn(1199L);

        assertThat(tight.extractNow(12L, iso, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_ENOUGH_SPACE));
        verify(isoPreparationService, never()).extractInto(any(), any());
        verify(smallDisk, never()).newStagingDir();

        when(smallDisk.usableSpace()).thenReturn(1200L);
        when(smallDisk.newStagingDir()).thenReturn(Files.createDirectories(temp.resolve("st")));
        assertThat(tight.extractNow(12L, iso, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_RHEL_TREE));
        verify(isoPreparationService).extractInto(any(), any());
    }

    @Test
    @DisplayName("ISO 파일이 사라지면 EXTRACTION_FAILED")
    void isoVanished() throws IOException {
        Files.delete(iso);
        assertThat(extractor.extractNow(12L, iso, RHEL)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED));
    }

    @Test
    @DisplayName("예상 밖 예외는 future 를 예외로 끝낸다(매니저가 EXTRACTION_FAILED 로 닫음)")
    void unexpectedEscapes() {
        doThrow(new IllegalStateException("boom")).when(isoPreparationService).extractInto(any(), any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> extractor.extractNow(12L, iso, RHEL))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stagingChildren()).isEmpty();
    }

    @Test
    @DisplayName("Ubuntu 성공: 커널 · initrd · .disk/info 만 꺼내 게시하고, source 는 등록 ISO 다")
    void ubuntuSuccess() throws Exception {
        doAnswer(inv -> writeUbuntu(inv.getArgument(1), "Ubuntu-Server 24.04.1 LTS \"Noble Numbat\" - Release amd64 (20240827)"))
                .when(isoPreparationService).extractMembers(any(), any(), any());

        TreeAcquisition result = extractor.extract(31L, iso, UBUNTU).get();

        assertThat(result).isInstanceOfSatisfying(TreeAcquisition.Ready.class, ready -> {
            assertThat(ready.tree().root()).isEqualTo(root.resolve("iso-31"));
            assertThat(ready.tree().release()).isEqualTo(new TreeRelease("Ubuntu", "24.04.1"));
            assertThat(ready.tree().source()).isEqualTo(iso.toAbsolutePath().normalize());
        });
        verify(isoPreparationService).extractMembers(eq(iso), extractedTo(), eq(List.of("casper/vmlinuz", "casper/initrd", ".disk/info")));
        verify(isoPreparationService, never()).extractInto(any(), any());
        assertThat(stagingChildren()).isEmpty();
    }

    @Test
    @DisplayName("Ubuntu desktop ISO 는 NOT_UBUNTU_SERVER_ISO · staging 삭제 · 게시 없음")
    void ubuntuDesktopRejected() {
        doAnswer(inv -> writeUbuntu(inv.getArgument(1), "Ubuntu 24.04.1 LTS \"Noble Numbat\" - Release amd64 (20240827)"))
                .when(isoPreparationService).extractMembers(any(), any(), any());

        assertThat(extractor.extractNow(31L, iso, UBUNTU)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_UBUNTU_SERVER_ISO));
        assertThat(root.resolve("iso-31")).doesNotExist();
        assertThat(stagingChildren()).isEmpty();
    }

    @Test
    @DisplayName("Ubuntu 는 ISO 크기와 무관하게 512 MiB 만 있으면 꺼낸다")
    void ubuntuSpaceIsFixed() throws IOException {
        InstallTreeStore smallDisk = mock(InstallTreeStore.class);
        InstallTreeExtractor tight = new InstallTreeExtractor(isoPreparationService, smallDisk);
        long mib512 = 512L * 1024 * 1024;
        when(smallDisk.usableSpace()).thenReturn(mib512 - 1);
        assertThat(tight.extractNow(31L, iso, UBUNTU)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_ENOUGH_SPACE));
        verify(isoPreparationService, never()).extractMembers(any(), any(), any());

        when(smallDisk.usableSpace()).thenReturn(mib512);
        when(smallDisk.newStagingDir()).thenReturn(Files.createDirectories(temp.resolve("st-u")));
        assertThat(tight.extractNow(31L, iso, UBUNTU)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_UBUNTU_SERVER_ISO));
        verify(isoPreparationService).extractMembers(any(), any(), any());
    }

    private static Path writeUbuntu(Path dir, String diskInfo) throws IOException {
        Files.createDirectories(dir.resolve("casper"));
        Files.createDirectories(dir.resolve(".disk"));
        Files.writeString(dir.resolve("casper/vmlinuz"), "kernel");
        Files.writeString(dir.resolve("casper/initrd"), "initrd");
        Files.writeString(dir.resolve(".disk/info"), diskInfo + "\n");
        return dir;
    }

    private Path extractedTo() {
        return org.mockito.ArgumentMatchers.argThat(path -> path.startsWith(root.resolve(".staging")));
    }

    private java.util.List<Path> stagingChildren() {
        Path staging = root.resolve(".staging");
        if (!Files.isDirectory(staging)) {
            return java.util.List.of();
        }
        try (Stream<Path> s = Files.list(staging)) {
            return s.toList();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
