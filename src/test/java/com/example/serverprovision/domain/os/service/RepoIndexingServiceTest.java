package com.example.serverprovision.domain.os.service;

import com.example.serverprovision.domain.os.entity.OSMetadata;
import com.example.serverprovision.domain.os.entity.OSPackageRef;
import com.example.serverprovision.domain.os.entity.OSServiceRef;
import com.example.serverprovision.domain.os.model.enums.OSName;
import com.example.serverprovision.domain.os.repository.OSMetadataRepository;
import com.example.serverprovision.domain.os.repository.OSPackageRefRepository;
import com.example.serverprovision.domain.os.repository.OSServiceRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RepoIndexingService} 전방위 단위 테스트.
 *
 * <p>@TempDir 기반으로 repodata/primary.xml.gz, filelists.xml.gz, repomd.xml 을 실제 파일로
 * 작성한 뒤 StAX 파싱이 기대대로 동작하는지 검증한다. DB 경로는 Mockito 로 격리한다.</p>
 *
 * <p>커버 시나리오:
 * <ul>
 *   <li>primary.xml.gz 에서 rpm 타입 package 만 수집하고 다른 타입은 무시</li>
 *   <li>아키텍처별 동일 이름 중복 제거 (LinkedHashMap 키 중복 체크)</li>
 *   <li>filelists.xml.gz 에서 /systemd/system/*.service 만 추출, 그 외 경로 .service 파일 제외</li>
 *   <li>@-instance unit (getty@.service) 제외</li>
 *   <li>멀티 repo (BaseOS, AppStream) 병합 처리</li>
 *   <li>Packages/images/isolinux/EFI 등 스킵 디렉토리의 repodata 는 무시</li>
 *   <li>HTTP URL 은 UnsupportedOperationException</li>
 *   <li>repodata 없는 경로는 IllegalStateException</li>
 *   <li>OSMetadata 조회 실패는 IllegalArgumentException</li>
 * </ul>
 * </p>
 */
class RepoIndexingServiceTest {

    private OSMetadataRepository osMetadataRepository;
    private OSPackageRefRepository packageRefRepository;
    private OSServiceRefRepository serviceRefRepository;
    private IsoPreparationService isoPreparationService;

    private RepoIndexingService service;

    @BeforeEach
    void setUp() {
        osMetadataRepository = mock(OSMetadataRepository.class);
        packageRefRepository = mock(OSPackageRefRepository.class);
        serviceRefRepository = mock(OSServiceRefRepository.class);
        isoPreparationService = mock(IsoPreparationService.class);

        service = new RepoIndexingService(
                osMetadataRepository,
                packageRefRepository,
                serviceRefRepository,
                isoPreparationService);
    }

    /** 단일 repo 트리를 구성하고 repomd.xml, primary.xml.gz, filelists.xml.gz 를 작성한다. */
    private Path buildRepo(Path root, String repoSubdir,
                           String primaryXml, String filelistsXml) throws IOException {
        Path repoBase = repoSubdir == null || repoSubdir.isEmpty() ? root : root.resolve(repoSubdir);
        Path repodata = repoBase.resolve("repodata");
        Files.createDirectories(repodata);

        // primary.xml.gz / filelists.xml.gz 작성 (실제 .gz 압축)
        Path primaryGz = repodata.resolve("primary.xml.gz");
        Path filelistsGz = repodata.resolve("filelists.xml.gz");
        writeGzip(primaryGz, primaryXml);
        writeGzip(filelistsGz, filelistsXml);

        // repomd.xml — data type="primary" 와 type="filelists" location 포함
        String repomd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <repomd xmlns="http://linux.duke.edu/metadata/repo">
              <data type="primary">
                <location href="repodata/primary.xml.gz"/>
              </data>
              <data type="filelists">
                <location href="repodata/filelists.xml.gz"/>
              </data>
            </repomd>
            """;
        Files.writeString(repodata.resolve("repomd.xml"), repomd);
        return repoBase;
    }

    private void writeGzip(Path path, String content) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(path))) {
            out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private OSMetadata mockMeta(Long id) {
        OSMetadata meta = OSMetadata.builder()
                .id(id).osName(OSName.ROCKY_LINUX).osVersion("9.6")
                .isoMountPath("/mocked").isEnabled(true).build();
        when(osMetadataRepository.findById(id)).thenReturn(Optional.of(meta));
        return meta;
    }

    private void stubIsoPreparation(String effectivePath) {
        when(isoPreparationService.prepare(any()))
                .thenReturn(IsoPreparationService.PreparedIsoPath.passthrough(effectivePath));
    }

    // =========================================================================
    // 정상 경로 — primary.xml.gz 파싱 검증
    // =========================================================================

    @Nested
    @DisplayName("primary.xml.gz 패키지 파싱")
    class PrimaryParseTest {

        @Test
        @DisplayName("rpm 타입 package 만 수집하고 아키텍처별 중복 이름을 제거한다")
        void extractsRpmPackagesAndDedupesByName(@TempDir Path tmp) throws IOException {
            // vim 은 x86_64 + i686 두 번 나와도 한 번만 저장되어야 함.
            // non-rpm type 의 package 요소는 포함되지 않아야 함 (방어적 설계).
            String primary = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata xmlns="http://linux.duke.edu/metadata/common">
                  <package type="rpm">
                    <name>vim</name>
                    <arch>x86_64</arch>
                  </package>
                  <package type="rpm">
                    <name>vim</name>
                    <arch>i686</arch>
                  </package>
                  <package type="rpm">
                    <name>httpd</name>
                    <arch>x86_64</arch>
                  </package>
                  <package type="srpm">
                    <name>should-be-ignored</name>
                  </package>
                </metadata>
                """;
            String filelists = emptyFilelists();
            buildRepo(tmp, "", primary, filelists);

            mockMeta(1L);
            stubIsoPreparation(tmp.toString());

            RepoIndexingService.IndexingSummary summary =
                    service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP);

            assertThat(summary.packageCount()).isEqualTo(2);

            ArgumentCaptor<Iterable<OSPackageRef>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(packageRefRepository).saveAll(captor.capture());
            List<String> names = new ArrayList<>();
            captor.getValue().forEach(p -> names.add(p.getName()));
            assertThat(names).containsExactlyInAnyOrder("vim", "httpd");
        }
    }

    // =========================================================================
    // filelists.xml.gz 파싱 검증
    // =========================================================================

    @Nested
    @DisplayName("filelists.xml.gz 서비스 파싱")
    class FilelistsParseTest {

        @Test
        @DisplayName("/systemd/system/*.service 만 추출하고 타 경로 .service 파일은 제외")
        void extractsOnlySystemdServices(@TempDir Path tmp) throws IOException {
            String primary = emptyPrimary();
            // systemd 경로 3가지 + 비-systemd 경로 + 비-.service 확장자 혼합
            String filelists = """
                <?xml version="1.0" encoding="UTF-8"?>
                <filelists xmlns="http://linux.duke.edu/metadata/filelists">
                  <package name="httpd" arch="x86_64">
                    <file>/usr/lib/systemd/system/httpd.service</file>
                    <file>/usr/bin/httpd</file>
                  </package>
                  <package name="openssh-server" arch="x86_64">
                    <file>/lib/systemd/system/sshd.service</file>
                  </package>
                  <package name="custom" arch="x86_64">
                    <file>/etc/systemd/system/my-app.service</file>
                  </package>
                  <package name="non-systemd" arch="x86_64">
                    <file>/usr/share/somewhere/random.service</file>
                  </package>
                  <package name="not-service" arch="x86_64">
                    <file>/usr/lib/systemd/system/readme.txt</file>
                  </package>
                </filelists>
                """;
            buildRepo(tmp, "", primary, filelists);

            mockMeta(1L);
            stubIsoPreparation(tmp.toString());

            RepoIndexingService.IndexingSummary summary =
                    service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP);

            assertThat(summary.serviceCount()).isEqualTo(3);

            ArgumentCaptor<Iterable<OSServiceRef>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(serviceRefRepository).saveAll(captor.capture());
            List<String> names = new ArrayList<>();
            captor.getValue().forEach(s -> names.add(s.getName()));
            assertThat(names).containsExactlyInAnyOrder("httpd", "sshd", "my-app");
        }

        @Test
        @DisplayName("@-instance unit (getty@.service) 은 인덱스에 포함하지 않는다")
        void excludesInstanceUnits(@TempDir Path tmp) throws IOException {
            String primary = emptyPrimary();
            String filelists = """
                <?xml version="1.0" encoding="UTF-8"?>
                <filelists>
                  <package name="systemd" arch="x86_64">
                    <file>/usr/lib/systemd/system/getty@.service</file>
                    <file>/usr/lib/systemd/system/systemd-logind.service</file>
                  </package>
                </filelists>
                """;
            buildRepo(tmp, "", primary, filelists);

            mockMeta(1L);
            stubIsoPreparation(tmp.toString());

            RepoIndexingService.IndexingSummary summary =
                    service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP);

            assertThat(summary.serviceCount()).isEqualTo(1);

            ArgumentCaptor<Iterable<OSServiceRef>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(serviceRefRepository).saveAll(captor.capture());
            List<String> names = new ArrayList<>();
            captor.getValue().forEach(s -> names.add(s.getName()));
            assertThat(names).containsExactly("systemd-logind");
        }

        @Test
        @DisplayName("동일 이름 서비스가 여러 패키지에 등장해도 한 번만 저장된다")
        void dedupesServicesByName(@TempDir Path tmp) throws IOException {
            String primary = emptyPrimary();
            String filelists = """
                <?xml version="1.0" encoding="UTF-8"?>
                <filelists>
                  <package name="pkg-a" arch="x86_64">
                    <file>/usr/lib/systemd/system/httpd.service</file>
                  </package>
                  <package name="pkg-b" arch="i686">
                    <file>/usr/lib/systemd/system/httpd.service</file>
                  </package>
                </filelists>
                """;
            buildRepo(tmp, "", primary, filelists);
            mockMeta(1L);
            stubIsoPreparation(tmp.toString());

            RepoIndexingService.IndexingSummary summary =
                    service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP);

            assertThat(summary.serviceCount()).isEqualTo(1);
        }
    }

    // =========================================================================
    // 멀티 repo 탐색 검증
    // =========================================================================

    @Nested
    @DisplayName("멀티 repo 디렉토리 탐색")
    class MultiRepoTest {

        @Test
        @DisplayName("BaseOS + AppStream 두 repo 를 모두 파싱하여 병합한다")
        void mergesMultipleRepos(@TempDir Path tmp) throws IOException {
            String basePrimary = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <package type="rpm"><name>bash</name></package>
                  <package type="rpm"><name>coreutils</name></package>
                </metadata>
                """;
            String appPrimary = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <package type="rpm"><name>httpd</name></package>
                  <package type="rpm"><name>nginx</name></package>
                </metadata>
                """;
            buildRepo(tmp, "BaseOS", basePrimary, emptyFilelists());
            buildRepo(tmp, "AppStream", appPrimary, emptyFilelists());

            mockMeta(1L);
            stubIsoPreparation(tmp.toString());

            RepoIndexingService.IndexingSummary summary =
                    service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP);

            assertThat(summary.packageCount()).isEqualTo(4);
        }

        @Test
        @DisplayName("Packages/images/isolinux/EFI 아래의 repodata 는 스킵된다")
        void skipsPackagesAndIsoLinuxDirs(@TempDir Path tmp) throws IOException {
            // 정상 repo 1 개
            buildRepo(tmp, "BaseOS", """
                <?xml version="1.0"?>
                <metadata><package type="rpm"><name>bash</name></package></metadata>
                """, emptyFilelists());

            // 스킵되어야 할 경로 — "Packages" 디렉토리 하위에 repodata 가 있어도 무시되어야 함
            buildRepo(tmp, "Packages", """
                <?xml version="1.0"?>
                <metadata><package type="rpm"><name>should-not-appear</name></package></metadata>
                """, emptyFilelists());

            mockMeta(1L);
            stubIsoPreparation(tmp.toString());

            RepoIndexingService.IndexingSummary summary =
                    service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP);

            assertThat(summary.packageCount()).isEqualTo(1);

            ArgumentCaptor<Iterable<OSPackageRef>> captor = ArgumentCaptor.forClass(Iterable.class);
            verify(packageRefRepository).saveAll(captor.capture());
            List<String> names = new ArrayList<>();
            captor.getValue().forEach(p -> names.add(p.getName()));
            assertThat(names).containsExactly("bash");
        }
    }

    // =========================================================================
    // 오류 경로
    // =========================================================================

    @Nested
    @DisplayName("오류 케이스")
    class ErrorCasesTest {

        @Test
        @DisplayName("존재하지 않는 OSMetadata ID 는 IllegalArgumentException")
        void throwsWhenMetadataMissing() {
            when(osMetadataRepository.findById(anyLong())).thenReturn(Optional.empty());
            assertThatThrownBy(() ->
                    service.indexAndSave(999L, RepoIndexingService.ProgressReporter.NOOP))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("존재하지 않는 OS 메타데이터");
        }

        @Test
        @DisplayName("HTTP URL 기반 ISO 는 UnsupportedOperationException")
        void throwsOnHttpUrl() {
            mockMeta(1L);
            // passthrough 가 HTTP URL 을 그대로 반환하도록 설정
            when(isoPreparationService.prepare(any()))
                    .thenReturn(IsoPreparationService.PreparedIsoPath.passthrough(
                            "http://mirror.example.com/rocky9"));

            assertThatThrownBy(() ->
                    service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("HTTP URL");
        }

        @Test
        @DisplayName("repodata 디렉토리가 없는 경로는 IllegalStateException")
        void throwsWhenNoRepoData(@TempDir Path tmp) {
            // tmp 자체는 디렉토리지만 repodata 하위 폴더가 없음
            mockMeta(1L);
            stubIsoPreparation(tmp.toString());

            assertThatThrownBy(() ->
                    service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("repodata 디렉토리를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("디렉토리가 아닌 경로 — 파일 경로가 전달되면 IllegalStateException")
        void throwsWhenNotADirectory(@TempDir Path tmp) throws IOException {
            // 일반 파일을 effectivePath 로 지정
            Path file = tmp.resolve("not-a-dir.txt");
            Files.writeString(file, "just a file");

            mockMeta(1L);
            stubIsoPreparation(file.toString());

            assertThatThrownBy(() ->
                    service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // ProgressReporter 동작 검증
    // =========================================================================

    @Nested
    @DisplayName("진행률 리포트")
    class ProgressTest {

        @Test
        @DisplayName("시작(3) 부터 완료(100) 까지 progress 가 단조 증가한다")
        void reportsMonotonicallyIncreasingProgress(@TempDir Path tmp) throws IOException {
            buildRepo(tmp, "", emptyPrimary(), emptyFilelists());
            mockMeta(1L);
            stubIsoPreparation(tmp.toString());

            List<Integer> captured = new ArrayList<>();
            RepoIndexingService.ProgressReporter reporter = (stage, pct) -> captured.add(pct);

            service.indexAndSave(1L, reporter);

            // 첫 호출은 최소 3 이상, 마지막은 100 이어야 하며 감소하지 않는다.
            assertThat(captured).isNotEmpty();
            assertThat(captured.get(0)).isLessThanOrEqualTo(10);
            assertThat(captured.get(captured.size() - 1)).isEqualTo(100);
            for (int i = 1; i < captured.size(); i++) {
                assertThat(captured.get(i)).isGreaterThanOrEqualTo(captured.get(i - 1));
            }
        }
    }

    // =========================================================================
    // 재인덱싱 시 기존 엔트리 삭제 호출
    // =========================================================================

    @Test
    @DisplayName("indexAndSave 실행 시 기존 인덱스를 먼저 deleteAllByOsMetadataId 로 삭제한다")
    void deletesExistingIndexBeforeSave(@TempDir Path tmp) throws IOException {
        buildRepo(tmp, "", emptyPrimary(), emptyFilelists());
        mockMeta(1L);
        stubIsoPreparation(tmp.toString());

        service.indexAndSave(1L, RepoIndexingService.ProgressReporter.NOOP);

        verify(packageRefRepository).deleteAllByOsMetadataId(1L);
        verify(serviceRefRepository).deleteAllByOsMetadataId(1L);
    }

    // =========================================================================
    // 헬퍼
    // =========================================================================

    private String emptyPrimary() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <metadata xmlns="http://linux.duke.edu/metadata/common"></metadata>
            """;
    }

    private String emptyFilelists() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <filelists xmlns="http://linux.duke.edu/metadata/filelists"></filelists>
            """;
    }
}
