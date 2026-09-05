package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.execution.asset.exception.WindowsOemAssemblyRejectedException;
import com.example.serverprovision.execution.wininstall.config.WindowsInstallProperties;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import com.example.serverprovision.management.subprogram.repository.SubprogramRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

/**
 * E4-1-a-4 CP4 — $OEM$ 조립기(D-1 · D-2 · OQ-3). 실제 디렉토리(@TempDir)에 조립해 배치 · 제외 · 스왑 · 매니페스트 · 상태 대조를 본다.
 * 자원 저장소만 mock — 트리는 진짜 파일이다(INF 재귀 탐색이 판별 근거이므로).
 */
@ExtendWith(MockitoExtension.class)
class WindowsOemPayloadAssemblerTest {

    @TempDir Path root;
    @TempDir Path trees;

    @Mock SubprogramRepository subprogramRepository;

    private WindowsOemPayloadAssembler assembler;
    private Path oem;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(root.resolve("sources"));
        oem = root.resolve("sources").resolve("$OEM$");
        assembler = new WindowsOemPayloadAssembler(props(root), subprogramRepository, new ObjectMapper());
    }

    private static WindowsInstallProperties props(Path root) {
        return new WindowsInstallProperties(root == null ? "" : root.toString(), null, null, null, null, null);
    }

    /** 트리 하나 — INF 유무 · 하위 폴더 · 무결성 마커(.provision.json, 등록 시 앱이 두는 파일) 포함. */
    private Path tree(String name, boolean withInf) throws IOException {
        Path dir = trees.resolve(name);
        Files.createDirectories(dir.resolve("x64"));
        Files.writeString(dir.resolve("readme.txt"), "fake");
        Files.writeString(dir.resolve("x64").resolve(withInf ? name + ".inf" : name + ".sys"), "[Version]");
        Files.writeString(dir.resolve(".provision.json"), "{\"resourceId\":1,\"signature\":\"hmac\"}");
        return dir;
    }

    private static Subprogram driver(long id, String name, Path tree, String hash, boolean enabled) {
        Subprogram s = Subprogram.builder().id(id).kind(SubprogramKind.DRIVER).name(name).version("1.0")
                .treeRootPath(tree.toString()).manifestHash(hash).fileCount(2).totalBytes(20L)
                .ownEnabled(enabled).ownDeprecated(false).isDeleted(false).build();
        s.recomputeEffective();
        return s;
    }

    private static List<String> names(Path dir) throws IOException {
        try (Stream<Path> list = Files.list(dir)) {
            return list.map(p -> p.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    @DisplayName("조립 — INF 트리 2 는 Drivers/<id>_<슬러그> 로 복사(하위 폴더 포함), INF 없는 DRIVER 는 제외, 스크립트 2 + 매니페스트 · .tmp 정리")
    void sync_assemblesAndExcludes() throws IOException {
        given(subprogramRepository.findAllByKindAndIsDeletedFalse(SubprogramKind.DRIVER)).willReturn(List.of(
                driver(1, "Intel Chipset (Rev A)", tree("chipset", true), "h1", true),
                driver(2, "qat", tree("qat", true), "h2", true),
                driver(3, "lan-tool", tree("lan-tool", false), "h3", true),
                driver(4, "disabled-nvme", tree("nvme", true), "h4", false)));

        WindowsOemPayloadAssembler.OemAssemblyResult result = assembler.sync();

        assertThat(result.assembled()).isEqualTo(2);
        assertThat(result.excluded()).isEqualTo(1);                       // 비활성은 대상이 아니라 제외 목록에도 없다
        assertThat(result.totalBytes()).isGreaterThan(0);
        Path drivers = oem.resolve("$1").resolve("SPV").resolve("Drivers");
        assertThat(names(drivers)).containsExactly("1_intel-chipset-rev-a", "2_qat");
        assertThat(drivers.resolve("1_intel-chipset-rev-a").resolve("x64").resolve("chipset.inf")).isRegularFile();
        // CP5 F-1 — 마커는 게스트로 나가지 않고, fileCount · bytes 는 복사한 파일 기준 하나다(readme + inf = 2 · 4 + 9 bytes)
        assertThat(drivers.resolve("1_intel-chipset-rev-a").resolve(".provision.json")).doesNotExist();
        assertThat(result.totalBytes()).isEqualTo(2L * (4 + 9));
        assertThat(oem.resolve("$$").resolve("Setup").resolve("Scripts").resolve("SetupComplete.cmd")).isRegularFile();
        assertThat(Files.readString(oem.resolve("$$").resolve("Setup").resolve("Scripts").resolve("SetupComplete.cmd")))
                .contains("\r\n").contains("pnputil /add-driver");
        assertThat(oem.resolve("$1").resolve("SPV").resolve("spv-report.ps1")).isRegularFile();
        assertThat(Files.readString(oem.resolve("$1").resolve("SPV").resolve("spv-report.ps1"))).doesNotContain("\r\n")
                .contains("/api/pxe/v1/agent/windows/complete");
        assertThat(names(oem)).containsExactly("$$", "$1", WindowsOemManifest.FILE_NAME);   // .tmp-* · .old-* 없음
        WindowsOemManifest manifest = assembler.readManifest().orElseThrow();
        assertThat(manifest.entries()).extracting(WindowsOemManifest.Entry::id).containsExactly(1L, 2L);
        assertThat(manifest.entries()).allSatisfy(e -> {
            assertThat(e.fileCount()).isEqualTo(2);
            assertThat(e.bytes()).isEqualTo(13L);
        });
        assertThat(manifest.excluded()).singleElement().satisfies(x -> {
            assertThat(x.name()).isEqualTo("lan-tool");
            assertThat(x.reason()).isEqualTo(WindowsOemPayloadAssembler.INF_MISSING);
        });
        assertThat(manifest.scriptsHash()).isEqualTo(WindowsOemTemplates.scriptsHash());
    }

    @Test
    @DisplayName("재조립 — 옛 페이로드가 새 것으로 통째로 바뀐다(제거된 자원 폴더가 남지 않는다) · 트리 부재는 TREE_MISSING")
    void sync_replacesPrevious() throws IOException {
        Subprogram chipset = driver(1, "chipset", tree("chipset", true), "h1", true);
        Subprogram qat = driver(2, "qat", tree("qat", true), "h2", true);
        given(subprogramRepository.findAllByKindAndIsDeletedFalse(SubprogramKind.DRIVER)).willReturn(List.of(chipset, qat));
        assembler.sync();
        Path drivers = oem.resolve("$1").resolve("SPV").resolve("Drivers");
        assertThat(names(drivers)).containsExactly("1_chipset", "2_qat");

        given(subprogramRepository.findAllByKindAndIsDeletedFalse(SubprogramKind.DRIVER)).willReturn(List.of(
                qat, driver(5, "ghost", trees.resolve("never-made"), "h5", true)));
        WindowsOemPayloadAssembler.OemAssemblyResult second = assembler.sync();

        assertThat(second.assembled()).isEqualTo(1);
        assertThat(second.excluded()).isEqualTo(1);
        assertThat(names(drivers)).containsExactly("2_qat");
        assertThat(names(oem)).containsExactly("$$", "$1", WindowsOemManifest.FILE_NAME);
        assertThat(assembler.readManifest().orElseThrow().excluded()).singleElement()
                .extracting(WindowsOemManifest.Excluded::reason).isEqualTo(WindowsOemPayloadAssembler.TREE_MISSING);
    }

    @Test
    @DisplayName("status — 미조립 → 조립 뒤 CURRENT(n종 · 제외 k) → 자원 교체(해시) 뒤 STALE + 변경 목록")
    void status_threeStates() throws IOException {
        Path chipsetTree = tree("chipset", true);
        given(subprogramRepository.findAllByKindAndIsDeletedFalse(SubprogramKind.DRIVER)).willReturn(List.of(
                driver(1, "chipset", chipsetTree, "h1", true), driver(3, "lan-tool", tree("lan-tool", false), "h3", true)));

        WindowsOemPayloadAssembler.OemPayloadStatus before = assembler.status();
        assertThat(before.state()).isEqualTo(OemPayloadState.NOT_ASSEMBLED);
        assertThat(before.excludedCount()).isEqualTo(1);

        assembler.sync();
        WindowsOemPayloadAssembler.OemPayloadStatus current = assembler.status();
        assertThat(current.state()).isEqualTo(OemPayloadState.CURRENT);
        assertThat(current.driverCount()).isEqualTo(1);
        assertThat(current.excludedCount()).isEqualTo(1);
        assertThat(current.assembledAt()).isNotNull();

        given(subprogramRepository.findAllByKindAndIsDeletedFalse(SubprogramKind.DRIVER)).willReturn(List.of(
                driver(1, "chipset", chipsetTree, "h1-v2", true)));
        WindowsOemPayloadAssembler.OemPayloadStatus stale = assembler.status();
        assertThat(stale.state()).isEqualTo(OemPayloadState.STALE);
        assertThat(stale.changes()).containsExactly("chipset 교체");
    }

    @Test
    @DisplayName("blockReason — 소스 미설정 · sources 없음 · $OEM$ 쓰기 불가는 사유, 정상은 empty · sync 는 같은 사유로 409")
    void blockReason_and409() throws IOException {
        assertThat(new WindowsOemPayloadAssembler(props(null), subprogramRepository, new ObjectMapper()).blockReason())
                .contains("설치 소스 미설정 — 환경변수 WINDOWS_INSTALL_SOURCE_ROOT");
        Path bare = Files.createTempDirectory("spv-bare");
        assertThat(new WindowsOemPayloadAssembler(props(bare), subprogramRepository, new ObjectMapper()).blockReason())
                .hasValueSatisfying(r -> assertThat(r).contains("sources 디렉토리가 없습니다"));
        assertThat(assembler.blockReason()).isEmpty();

        Files.createDirectories(oem);
        oem.toFile().setWritable(false, false);
        try {
            assertThat(assembler.blockReason()).hasValueSatisfying(r -> assertThat(r).contains("쓰기 권한"));
            lenient().when(subprogramRepository.findAllByKindAndIsDeletedFalse(SubprogramKind.DRIVER)).thenReturn(List.of());
            assertThatThrownBy(() -> assembler.sync()).isInstanceOf(WindowsOemAssemblyRejectedException.class)
                    .hasMessageContaining("쓰기 권한");
        } finally {
            oem.toFile().setWritable(true, false);
        }
    }

    @Test
    @DisplayName("slug — 소문자 · 영숫자 외는 하이픈 · 40자 상한 · 한글만이면 driver")
    void slug() {
        assertThat(WindowsOemPayloadAssembler.slug("Intel(R) Chipset Device Software 10.1")).isEqualTo("intel-r-chipset-device-software-10-1");
        assertThat(WindowsOemPayloadAssembler.slug("메가레이드 드라이버")).isEqualTo("driver");
        assertThat(WindowsOemPayloadAssembler.slug("a".repeat(60))).hasSize(40);
        assertThat(WindowsOemPayloadAssembler.slug(null)).isEqualTo("driver");
    }

    @Test
    @DisplayName("hasInf — 하위 폴더의 .INF(대문자)도 잡는다 · 없으면 false")
    void hasInf() throws IOException {
        Path dir = trees.resolve("deep");
        Files.createDirectories(dir.resolve("a").resolve("b"));
        Files.writeString(dir.resolve("a").resolve("b").resolve("NET.INF"), "x");
        assertThat(WindowsOemPayloadAssembler.hasInf(dir)).isTrue();
        assertThat(WindowsOemPayloadAssembler.hasInf(tree("none", false))).isFalse();
    }

    // ── HF11-3 — 기존 항목 소유권 · 잔존 정리 ──────────────────────────────────

    @Test
    @DisplayName("HF11-3 blockReason — $OEM$ 안의 기존 $$ · $1 이 쓰기 불가면 사유(항목 이름 · chown 안내) · sync 409 · 정상은 empty")
    void blockReason_existingChildNotWritable() throws IOException {
        Path dollar1 = oem.resolve("$1");
        Files.createDirectories(oem.resolve("$$")); Files.createDirectories(dollar1);
        assertThat(assembler.blockReason()).isEmpty();
        dollar1.toFile().setWritable(false, false);
        try {
            assertThat(assembler.blockReason()).hasValueSatisfying(r -> assertThat(r).contains("기존 항목($1)").contains("chown -R provisioning:spvadmin"));
            lenient().when(subprogramRepository.findAllByKindAndIsDeletedFalse(SubprogramKind.DRIVER)).thenReturn(List.of());
            assertThatThrownBy(() -> assembler.sync()).isInstanceOf(WindowsOemAssemblyRejectedException.class).hasMessageContaining("기존 항목");
        } finally {
            dollar1.toFile().setWritable(true, false);
        }
        assertThat(assembler.blockReason()).isEmpty();
    }

    @Test
    @DisplayName("HF11-3 sync — 이전 실패가 남긴 .tmp-* · .old-* 를 조립 시작 시 치운다 · 매니페스트 등 다른 항목은 건드리지 않는다")
    void sync_sweepsLeftovers() throws IOException {
        Files.createDirectories(oem.resolve(".tmp-20260904-000000").resolve("$1"));
        Files.createDirectories(oem.resolve(".old-20260904-080637"));
        Files.createDirectories(oem.resolve("keep-me"));
        given(subprogramRepository.findAllByKindAndIsDeletedFalse(SubprogramKind.DRIVER)).willReturn(List.of(
                driver(1, "chipset", tree("chipset", true), "h1", true)));

        assembler.sync();

        assertThat(names(oem)).containsExactly("$$", "$1", "keep-me", WindowsOemManifest.FILE_NAME);
    }

    @Test
    @DisplayName("HF11-3 sweepLeftovers — 잔존만 지우고 정규 항목은 남긴다")
    void sweepLeftovers_only() throws IOException {
        Files.createDirectories(oem.resolve(".old-x")); Files.createDirectories(oem.resolve(".tmp-y")); Files.createDirectories(oem.resolve("$$"));
        Files.writeString(oem.resolve(WindowsOemManifest.FILE_NAME), "{}");
        WindowsOemPayloadAssembler.sweepLeftovers(oem);
        assertThat(names(oem)).containsExactly("$$", WindowsOemManifest.FILE_NAME);
    }
}
