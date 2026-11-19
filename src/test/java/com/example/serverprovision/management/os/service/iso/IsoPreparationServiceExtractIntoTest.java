package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.execution.installtree.FakeRockyTree;
import com.example.serverprovision.management.os.exception.ExtractionFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** E4-1-b-1 — 실제 bsdtar 로 ISO 를 지정 디렉터리에 푼다(도구 · ISO 생성기가 없는 환경은 건너뜀). */
class IsoPreparationServiceExtractIntoTest {

    private final IsoPreparationService service = new IsoPreparationService();

    @Test
    @DisplayName("가짜 Rocky ISO 를 풀면 설치기가 읽는 파일이 그대로 나온다")
    void extractsIso(@TempDir Path temp) throws Exception {
        assumeTrue(service.extractionToolAvailable(), "bsdtar · 7z 없음");
        Path iso = FakeRockyTree.buildIso(temp, "Rocky Linux", "9.5").orElse(null);
        assumeTrue(iso != null, "ISO 생성기(hdiutil · xorriso) 없음");
        Path target = Files.createDirectories(temp.resolve("out"));

        service.extractInto(iso, target);

        assertThat(target.resolve(".treeinfo")).isRegularFile();
        assertThat(target.resolve("images/pxeboot/vmlinuz")).hasContent("kernel");
        assertThat(target.resolve("BaseOS/repodata/repomd.xml")).isRegularFile();
    }

    @Test
    @DisplayName("ISO 가 아닌 파일은 ExtractionFailedException")
    void rejectsGarbage(@TempDir Path temp) throws Exception {
        assumeTrue(service.extractionToolAvailable(), "bsdtar · 7z 없음");
        Path garbage = Files.writeString(temp.resolve("broken.iso"), "not an iso image");

        assertThatThrownBy(() -> service.extractInto(garbage, Files.createDirectories(temp.resolve("out"))))
                .isInstanceOf(ExtractionFailedException.class)
                .hasMessageContaining("압축을 풀지 못했습니다");
    }
}
