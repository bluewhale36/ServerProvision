package com.example.serverprovision.management.os.service.iso;

import com.example.serverprovision.execution.installtree.FakeRockyTree;
import com.example.serverprovision.execution.installtree.FakeUbuntuIso;
import com.example.serverprovision.management.os.exception.ExtractionFailedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** 실제 bsdtar 로 ISO 에서 지정 파일만 꺼낸다(도구 · ISO 생성기가 없는 환경은 건너뜀). */
class IsoPreparationServiceExtractMembersTest {

    private static final List<String> MEMBERS = List.of("casper/vmlinuz", "casper/initrd", ".disk/info");
    private final IsoPreparationService service = new IsoPreparationService();

    @Test
    @DisplayName("지정한 셋만 꺼내고 squashfs 는 건드리지 않는다")
    void extractsOnlyMembers(@TempDir Path temp) throws Exception {
        assumeTrue(service.extractionToolAvailable(), "bsdtar · 7z 없음");
        Path iso = FakeUbuntuIso.buildIso(temp, "2404", FakeUbuntuIso.SERVER_2404).orElse(null);
        assumeTrue(iso != null, "ISO 생성기(hdiutil · xorriso) 없음");
        Path target = Files.createDirectories(temp.resolve("out"));

        service.extractMembers(iso, target, MEMBERS);

        assertThat(target.resolve("casper/vmlinuz")).hasContent("kernel");
        assertThat(target.resolve("casper/initrd")).hasContent("initrd");
        assertThat(target.resolve(".disk/info")).isRegularFile();
        assertThat(target.resolve("casper/ubuntu-server-minimal.squashfs")).doesNotExist();
    }

    @Test
    @DisplayName("ISO 에 없는 이름이 섞이면 예외 없이 있는 것만 꺼낸다(판별이 계열 아님으로 닫는다)")
    void missingMembersAreSkipped(@TempDir Path temp) throws Exception {
        assumeTrue(service.extractionToolAvailable(), "bsdtar · 7z 없음");
        Path iso = FakeRockyTree.buildIso(temp, "Rocky Linux", "9.5").orElse(null);
        assumeTrue(iso != null, "ISO 생성기(hdiutil · xorriso) 없음");
        Path target = Files.createDirectories(temp.resolve("out"));

        service.extractMembers(iso, target, List.of("images/pxeboot/vmlinuz", "casper/initrd"));

        assertThat(target.resolve("images/pxeboot/vmlinuz")).hasContent("kernel");
        assertThat(target.resolve("casper/initrd")).doesNotExist();
    }

    @Test
    @DisplayName("ISO 가 아닌 파일이면 아무것도 꺼내지 못하고 예외 없이 돌아온다")
    void notAnIso(@TempDir Path temp) throws Exception {
        assumeTrue(service.extractionToolAvailable(), "bsdtar · 7z 없음");
        Path bogus = Files.writeString(temp.resolve("bogus.iso"), "not an iso");
        Path target = Files.createDirectories(temp.resolve("out"));

        service.extractMembers(bogus, target, MEMBERS);

        assertThat(target.resolve("casper/vmlinuz")).doesNotExist();
    }

    @Test
    @DisplayName("ISO 가 없으면 꺼낸 것이 없어 예외 없이 돌아온다. 지정 파일이 모두 있는 상태에서 도구가 실패하면 예외다(빈 목록으로 재현)")
    void vanishedIso(@TempDir Path temp) throws Exception {
        assumeTrue(service.extractionToolAvailable(), "bsdtar · 7z 없음");
        Path target = Files.createDirectories(temp.resolve("out"));
        service.extractMembers(temp.resolve("gone.iso"), target, MEMBERS);
        assertThat(target.resolve("casper/vmlinuz")).doesNotExist();

        assertThatThrownBy(() -> service.extractMembers(temp.resolve("gone.iso"), target, List.of()))
                .isInstanceOf(ExtractionFailedException.class);
    }
}
