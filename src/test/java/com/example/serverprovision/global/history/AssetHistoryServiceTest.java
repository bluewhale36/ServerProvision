package com.example.serverprovision.global.history;

import com.example.serverprovision.global.history.entity.AssetVersion;
import com.example.serverprovision.global.history.exception.AssetVersionArchiveFailedException;
import com.example.serverprovision.global.history.exception.AssetVersionNotFoundException;
import com.example.serverprovision.global.history.repository.AssetVersionRepository;
import com.example.serverprovision.global.history.service.internal.AssetHistoryServiceImpl;
import com.example.serverprovision.global.security.FileSystemHardener;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * E1-I-2-b-1 CP4 — 버전 저장소 본체 검증(mock 리포지토리 + @TempDir 파일시스템). 파생 쿼리 문자열의 실행
 * 정합성과 엔티티↔스키마 매핑은 샌드박스(CP5, 실 MariaDB + DDL)로 확인한다(프로젝트에 @DataJpaTest 선례 부재).
 */
class AssetHistoryServiceTest {

    @TempDir
    Path historyRoot;
    @TempDir
    Path srcDir;

    private final AssetVersionRepository repository = mock(AssetVersionRepository.class);
    private AssetHistoryServiceImpl service;

    private static final AssetVersionKey KEY = new AssetVersionKey("DIAGNOSTIC", "modloop-lts");

    @BeforeEach
    void setUp() {
        service = newService(historyRoot, 3);
    }

    private AssetHistoryServiceImpl newService(Path root, int retention) {
        AssetHistoryProperties props = new AssetHistoryProperties();
        ReflectionTestUtils.setField(props, "root", root.toString());
        ReflectionTestUtils.setField(props, "retentionCount", retention);
        AssetHistorySettingsService settingsService = mock(AssetHistorySettingsService.class);
        given(settingsService.getRetentionCount()).willReturn(retention);   // prune 이 실효 보존 개수를 여기서 얻음
        FileSystemHardener hardener = new FileSystemHardener(mock(FileSystemSecurityProperties.class));
        return new AssetHistoryServiceImpl(repository, props, settingsService, hardener);
    }

    @Test
    @DisplayName("archive — 현재 파일을 이력 루트로 복사 + 행 저장(내용·해시·크기·키)")
    void archive_copiesAndRecords() throws IOException {
        Path src = src("modloop-lts", "MODLOOP-CONTENT-v1");
        given(repository.save(any(AssetVersion.class))).willAnswer(inv -> inv.getArgument(0));
        given(repository.countByCategoryAndName(anyString(), anyString())).willReturn(1L);  // prune no-op

        Optional<AssetVersion> result = service.archive(KEY, src);

        assertThat(result).isPresent();
        Path keyDir = historyRoot.resolve("DIAGNOSTIC").resolve("modloop-lts");
        try (Stream<Path> files = Files.list(keyDir)) {
            List<Path> archived = files.toList();
            assertThat(archived).hasSize(1);
            assertThat(Files.readString(archived.get(0))).isEqualTo("MODLOOP-CONTENT-v1");
        }
        ArgumentCaptor<AssetVersion> captor = ArgumentCaptor.forClass(AssetVersion.class);
        verify(repository).save(captor.capture());
        AssetVersion saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo("DIAGNOSTIC");
        assertThat(saved.getName()).isEqualTo("modloop-lts");
        assertThat(saved.getSizeBytes()).isEqualTo("MODLOOP-CONTENT-v1".getBytes().length);
        assertThat(saved.getSha256()).hasSize(64);
        assertThat(saved.getArchivedRelPath()).startsWith("DIAGNOSTIC/modloop-lts/");
    }

    @Test
    @DisplayName("archive — 현재 파일 부재(최초 생성 교체) → empty, 복사·저장 없음")
    void archive_absentFile_empty() {
        Optional<AssetVersion> result = service.archive(KEY, srcDir.resolve("does-not-exist"));

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("archive — copy IO 실패 → AssetVersionArchiveFailedException(저장 미수행)")
    void archive_ioFailure_throws() throws IOException {
        // 이력 루트를 '파일'로 지정 → <root>/category/name 디렉토리 생성이 실패한다.
        Path rootAsFile = Files.createFile(srcDir.resolve("root-is-a-file"));
        AssetHistoryServiceImpl bad = newService(rootAsFile, 3);
        Path src = src("modloop-lts", "x");

        assertThatThrownBy(() -> bad.archive(KEY, src))
                .isInstanceOf(AssetVersionArchiveFailedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("prune — 최근 N 초과분(오래된 것부터) 파일 + 행 삭제")
    void prune_keepsLastN() throws IOException {
        // retention=3, count=5 → over=2. 가장 오래된 2개를 삭제한다.
        Path keyDir = Files.createDirectories(historyRoot.resolve("DIAGNOSTIC").resolve("modloop-lts"));
        AssetVersion old1 = versionWithFile(keyDir, "old1");
        AssetVersion old2 = versionWithFile(keyDir, "old2");
        given(repository.countByCategoryAndName("DIAGNOSTIC", "modloop-lts")).willReturn(5L);
        given(repository.findByCategoryAndNameOrderByIdAsc(eq("DIAGNOSTIC"), eq("modloop-lts"),
                eq(PageRequest.of(0, 2)))).willReturn(new PageImpl<>(List.of(old1, old2)));

        service.prune(KEY);

        assertThat(historyRoot.resolve(old1.getArchivedRelPath())).doesNotExist();
        assertThat(historyRoot.resolve(old2.getArchivedRelPath())).doesNotExist();
        verify(repository).deleteAll(List.of(old1, old2));
    }

    @Test
    @DisplayName("prune — N 이내면 삭제 없음")
    void prune_withinLimit_noDelete() {
        given(repository.countByCategoryAndName("DIAGNOSTIC", "modloop-lts")).willReturn(2L);

        service.prune(KEY);

        verify(repository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("openVersion — 소유권 일치 시 경로 반환 / 불일치·부재 시 404")
    void openVersion_ownershipGuard() throws IOException {
        Path keyDir = Files.createDirectories(historyRoot.resolve("DIAGNOSTIC").resolve("modloop-lts"));
        AssetVersion v = versionWithFile(keyDir, "keep");
        ReflectionTestUtils.setField(v, "id", 7L);
        given(repository.findById(7L)).willReturn(Optional.of(v));
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThat(service.openVersion(KEY, 7L)).isEqualTo(historyRoot.resolve(v.getArchivedRelPath()));
        assertThatThrownBy(() -> service.openVersion(new AssetVersionKey("PXE", "dhcpd.conf"), 7L))
                .isInstanceOf(AssetVersionNotFoundException.class);   // 다른 키 소속 → 거절(forging 차단)
        assertThatThrownBy(() -> service.openVersion(KEY, 99L))
                .isInstanceOf(AssetVersionNotFoundException.class);   // 없는 id
    }

    @Test
    @DisplayName("remove — 소유권 일치 시 파일 + 행 삭제(승격용)")
    void remove_deletesFileAndRow() throws IOException {
        Path keyDir = Files.createDirectories(historyRoot.resolve("DIAGNOSTIC").resolve("modloop-lts"));
        AssetVersion v = versionWithFile(keyDir, "v");
        ReflectionTestUtils.setField(v, "id", 5L);
        given(repository.findById(5L)).willReturn(Optional.of(v));

        service.remove(KEY, 5L);

        assertThat(historyRoot.resolve(v.getArchivedRelPath())).doesNotExist();
        verify(repository).delete(v);
    }

    @Test
    @DisplayName("remove — 없는 id → no-op(멱등)")
    void remove_missing_noop() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        service.remove(KEY, 99L);

        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("remove — 다른 키 소속 → no-op(제거 안 함)")
    void remove_wrongKey_noop() throws IOException {
        Path keyDir = Files.createDirectories(historyRoot.resolve("DIAGNOSTIC").resolve("modloop-lts"));
        AssetVersion v = versionWithFile(keyDir, "v");
        ReflectionTestUtils.setField(v, "id", 5L);
        given(repository.findById(5L)).willReturn(Optional.of(v));

        service.remove(new AssetVersionKey("PXE", "dhcpd.conf"), 5L);

        verify(repository, never()).delete(any());
    }

    // ── 픽스처 ──────────────────────────────────────────────────────────────

    private Path src(String name, String content) throws IOException {
        return Files.writeString(srcDir.resolve(name), content);
    }

    private AssetVersion versionWithFile(Path keyDir, String label) throws IOException {
        Path f = Files.writeString(keyDir.resolve(label + ".bin"), label);
        String rel = historyRoot.relativize(f).toString();
        return AssetVersion.of(new AssetVersionKey("DIAGNOSTIC", "modloop-lts"), rel, "hash", 4L, Instant.now());
    }
}
