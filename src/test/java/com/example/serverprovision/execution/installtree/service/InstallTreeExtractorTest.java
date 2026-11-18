package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.FakeRockyTree;
import com.example.serverprovision.execution.installtree.config.InstallTreeProperties;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 추출 갈래: 성공 · 공간 부족 · RHEL 트리 아님 · 도구 실패. 실패는 staging 을 남기지 않는다. */
class InstallTreeExtractorTest {

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
        extractor = new InstallTreeExtractor(isoPreparationService, new InstallTreeInspector(), store);
    }

    @Test
    @DisplayName("성공: staging 에 풀고 확인한 뒤 iso-<id> 로 게시한다")
    void success() throws Exception {
        doAnswer(inv -> FakeRockyTree.writeTree(inv.getArgument(1), "Rocky Linux", "9.5"))
                .when(isoPreparationService).extractInto(any(), any());

        TreeAcquisition result = extractor.extract(12L, iso).get();

        assertThat(result).isInstanceOfSatisfying(TreeAcquisition.Ready.class, ready -> {
            assertThat(ready.tree().root()).isEqualTo(root.resolve("iso-12"));
            assertThat(ready.tree().release()).isEqualTo(new TreeRelease("Rocky Linux", "9.5"));
        });
        assertThat(stagingChildren()).isEmpty();
    }

    @Test
    @DisplayName("RHEL 설치 트리가 아니면 NOT_RHEL_TREE · staging 삭제 · 게시 없음")
    void notRhelTree() {
        doAnswer(inv -> Files.writeString(((Path) inv.getArgument(1)).resolve("README"), "x"))
                .when(isoPreparationService).extractInto(any(), any());

        assertThat(extractor.extractNow(12L, iso)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_RHEL_TREE));
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

        assertThat(extractor.extractNow(12L, iso)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED));
        assertThat(stagingChildren()).isEmpty();
    }

    @Test
    @DisplayName("ISO 크기 × 1.2 가 남은 공간보다 크면 풀지 않고 NOT_ENOUGH_SPACE (경계: 정확히 1.2 배면 통과)")
    void notEnoughSpace() throws IOException {
        InstallTreeStore smallDisk = mock(InstallTreeStore.class);
        InstallTreeExtractor tight = new InstallTreeExtractor(isoPreparationService, new InstallTreeInspector(), smallDisk);
        Files.write(iso, new byte[1000]);
        when(smallDisk.usableSpace()).thenReturn(1199L);

        assertThat(tight.extractNow(12L, iso)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_ENOUGH_SPACE));
        verify(isoPreparationService, never()).extractInto(any(), any());
        verify(smallDisk, never()).newStagingDir();

        when(smallDisk.usableSpace()).thenReturn(1200L);
        when(smallDisk.newStagingDir()).thenReturn(Files.createDirectories(temp.resolve("st")));
        assertThat(tight.extractNow(12L, iso)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.NOT_RHEL_TREE));
        verify(isoPreparationService).extractInto(any(), any());
    }

    @Test
    @DisplayName("ISO 파일이 사라지면 EXTRACTION_FAILED")
    void isoVanished() throws IOException {
        Files.delete(iso);
        assertThat(extractor.extractNow(12L, iso)).isEqualTo(TreeAcquisition.failed(TreeFailureReason.EXTRACTION_FAILED));
    }

    @Test
    @DisplayName("예상 밖 예외는 future 를 예외로 끝낸다(매니저가 EXTRACTION_FAILED 로 닫음)")
    void unexpectedEscapes() {
        doThrow(new IllegalStateException("boom")).when(isoPreparationService).extractInto(any(), any());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> extractor.extractNow(12L, iso))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stagingChildren()).isEmpty();
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
