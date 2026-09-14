package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.management.board.entity.BoardModel;
import com.example.serverprovision.management.os.enums.OSFamily;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.entity.SubprogramVariant;
import com.example.serverprovision.management.subprogram.enums.SubprogramKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R15-2 D-2 — 서빙 시점 드라이버 선택 진리표. 보드 · OS 두 축으로 후보를 가르고, 패키지마다 TREE(변형 없음) 또는
 * 버전 정확 일치 → 전 버전 변형 → 제외 순으로 하나를 정한다. 매니페스트에 없는 패키지 · 비 ASCII 경로는 제외 사유가 붙는다.
 */
class WindowsDriverSelectionTest {

    private static final long BOARD = 7L;
    private static final WindowsInstallTarget.OsTarget WIN2025 = new WindowsInstallTarget.OsTarget(OSName.WINDOWS_SERVER, "2025");
    private static final OSName NON_WINDOWS = Arrays.stream(OSName.values())
            .filter(o -> o.getFamily() != OSFamily.WINDOWS_BASED).findFirst().orElseThrow();

    private static Subprogram driver(long id, String name, Long boardId, OSName osName, boolean enabled) {
        Subprogram s = Subprogram.builder().id(id).kind(SubprogramKind.DRIVER).name(name).version("1.0")
                .treeRootPath("/srv/sub/" + id).osName(osName)
                .boardModel(boardId == null ? null : BoardModel.builder().id(boardId).build())
                .ownEnabled(enabled).ownDeprecated(false).isDeleted(false).build();
        s.recomputeEffective();
        return s;
    }

    private static Subprogram withVariants(Subprogram s, SubprogramVariant... variants) {
        s.getVariants().addAll(List.of(variants));
        return s;
    }

    private static SubprogramVariant variant(Subprogram s, String osVersion, String entrypoint, String args, boolean reboot) {
        return new SubprogramVariant(s, osVersion, entrypoint, args, reboot, 1);
    }

    /** 매니페스트 = 주어진 패키지 전부가 조립돼 있다. */
    private static Set<String> assembled(Subprogram... all) {
        Set<String> folders = new HashSet<>();
        for (Subprogram s : all) folders.add(WindowsOemPayloadAssembler.folderOf(s));
        return folders;
    }

    @Test
    @DisplayName("보드 축 — 같은 보드 · 공용은 후보, 다른 보드는 후보 아님(제외 목록에도 없다) · 보드 미상이면 공용만")
    void boardAxis() {
        Subprogram mine = driver(1, "A", BOARD, null, true);
        Subprogram common = driver(2, "B", null, null, true);
        Subprogram other = driver(3, "C", 8L, null, true);
        List<Subprogram> all = List.of(mine, common, other);

        WindowsDriverSelection.Selection sel = WindowsDriverSelection.select(BOARD, WIN2025, all, assembled(mine, common, other));
        assertThat(sel.entries()).extracting(WindowsDriverSelection.Entry::id).containsExactly(1L, 2L);
        assertThat(sel.skipped()).isEmpty();

        WindowsDriverSelection.Selection noBoard = WindowsDriverSelection.select(null, WIN2025, all, assembled(mine, common, other));
        assertThat(noBoard.entries()).extracting(WindowsDriverSelection.Entry::id).containsExactly(2L);
    }

    @Test
    @DisplayName("OS 축 — OS 무관은 항상 후보 · 대상을 알면 같은 OSName 만 · 대상 미상이면 Windows 계열이면 후보")
    void osAxis() {
        Subprogram any = driver(1, "A", null, null, true);
        Subprogram win = driver(2, "B", null, OSName.WINDOWS_SERVER, true);
        Subprogram linux = driver(3, "C", null, NON_WINDOWS, true);
        List<Subprogram> all = List.of(any, win, linux);

        assertThat(WindowsDriverSelection.select(null, WIN2025, all, assembled(any, win, linux)).entries())
                .extracting(WindowsDriverSelection.Entry::id).containsExactly(1L, 2L);
        assertThat(WindowsDriverSelection.select(null, WindowsInstallTarget.OsTarget.UNKNOWN, all, assembled(any, win, linux)).entries())
                .extracting(WindowsDriverSelection.Entry::id).containsExactly(1L, 2L);
        assertThat(WindowsDriverSelection.appliesToOs(linux, WindowsInstallTarget.OsTarget.UNKNOWN)).isFalse();
    }

    @Test
    @DisplayName("변형 없음 = TREE(폴더 전체 INF · 현행) — 진입점 빈 문자열 · 줄 'TREE|folder|-|-|0'(빈 필드는 - · for /f 구분자 합침 대비)")
    void noVariants_isTree() {
        Subprogram s = driver(4, "Chipset", BOARD, OSName.WINDOWS_SERVER, true);
        WindowsDriverSelection.Selection sel = WindowsDriverSelection.select(BOARD, WIN2025, List.of(s), assembled(s));

        assertThat(sel.entries()).hasSize(1);
        WindowsDriverSelection.Entry e = sel.entries().get(0);
        assertThat(e.mode()).isEqualTo(WindowsDriverSelection.Mode.TREE);
        assertThat(e.line()).isEqualTo("TREE|" + WindowsOemPayloadAssembler.folderOf(s) + "|-|-|0");
        assertThat(e.label()).isEqualTo("Chipset · 트리 전체 INF");
    }

    @Test
    @DisplayName("변형 — 정확 일치(정규화 키 · ' 2025 ' 도 2025) 가 전 버전보다 앞선다 · 진입점은 역슬래시 표기 · 종류는 확장자")
    void variants_exactBeatsWildcard() {
        Subprogram s = driver(5, "ASPEED", BOARD, OSName.WINDOWS_SERVER, true);
        withVariants(s,
                variant(s, null, "WDDM Installer/Generic.msi", null, false),
                variant(s, " 2025 ", "WDDM Installer/Win2025.msi", "/l*v C:\\SPV\\aspeed.log", true));
        WindowsDriverSelection.Selection sel = WindowsDriverSelection.select(BOARD, WIN2025, List.of(s), assembled(s));

        WindowsDriverSelection.Entry e = sel.entries().get(0);
        assertThat(e.mode()).isEqualTo(WindowsDriverSelection.Mode.MSI);
        assertThat(e.entrypoint()).isEqualTo("WDDM Installer\\Win2025.msi");
        assertThat(e.line()).isEqualTo("MSI|" + WindowsOemPayloadAssembler.folderOf(s) + "|WDDM Installer\\Win2025.msi|/l*v C:\\SPV\\aspeed.log|1");
        assertThat(e.osVersion()).isEqualTo("2025");   // 엔티티가 공백을 정규화한다
        assertThat(sel.anyReboot()).isTrue();
    }

    @Test
    @DisplayName("변형 — 정확 일치가 없으면 전 버전 변형 · 그것도 없으면 제외 '해당 버전 변형 없음' · 대상 버전 미상이면 전 버전만")
    void variants_wildcardFallback_orSkip() {
        Subprogram wild = driver(6, "LAN", BOARD, OSName.WINDOWS_SERVER, true);
        withVariants(wild, variant(wild, "2022", "x/2022.inf", null, false), variant(wild, null, "x/any.inf", null, false));
        Subprogram strict = driver(7, "NVMe", BOARD, OSName.WINDOWS_SERVER, true);
        withVariants(strict, variant(strict, "2022", "n/2022.exe", "/s", false));

        WindowsDriverSelection.Selection sel = WindowsDriverSelection.select(BOARD, WIN2025, List.of(wild, strict), assembled(wild, strict));
        assertThat(sel.entries()).hasSize(1);
        assertThat(sel.entries().get(0).entrypoint()).isEqualTo("x\\any.inf");
        assertThat(sel.entries().get(0).mode()).isEqualTo(WindowsDriverSelection.Mode.INF);
        assertThat(sel.entries().get(0).line()).isEqualTo("INF|" + WindowsOemPayloadAssembler.folderOf(wild) + "|x\\any.inf|-|0");
        assertThat(sel.skipped()).singleElement().satisfies(sk -> {
            assertThat(sk.id()).isEqualTo(7L);
            assertThat(sk.reason()).isEqualTo(WindowsDriverSelection.SKIP_NO_VARIANT_FOR_VERSION);
            assertThat(sk.label()).isEqualTo("NVMe — 해당 버전 변형 없음");
        });

        WindowsDriverSelection.Selection unknown = WindowsDriverSelection.select(BOARD,
                new WindowsInstallTarget.OsTarget(OSName.WINDOWS_SERVER, null), List.of(wild), assembled(wild));
        assertThat(unknown.entries().get(0).entrypoint()).isEqualTo("x\\any.inf");
    }

    @Test
    @DisplayName("제외 — 매니페스트에 없는 패키지 '조립 미반영' · 비 ASCII 진입점 · 인자의 파이프 '비 ASCII 경로'")
    void skips_notAssembled_nonAscii_pipe() {
        Subprogram missing = driver(8, "Audio", BOARD, null, true);
        Subprogram hangul = driver(9, "Video", BOARD, null, true);
        withVariants(hangul, variant(hangul, null, "설치/setup.exe", null, false));
        Subprogram piped = driver(10, "Tool", BOARD, null, true);
        withVariants(piped, variant(piped, null, "t/setup.exe", "/a|b", false));

        WindowsDriverSelection.Selection sel = WindowsDriverSelection.select(BOARD, WIN2025, List.of(missing, hangul, piped),
                assembled(hangul, piped));
        assertThat(sel.entries()).isEmpty();
        assertThat(sel.skipped()).extracting(WindowsDriverSelection.Skipped::reason).containsExactly(
                WindowsDriverSelection.SKIP_NOT_ASSEMBLED, WindowsDriverSelection.SKIP_NON_ASCII, WindowsDriverSelection.SKIP_NON_ASCII);
        assertThat(sel.summary()).isEqualTo("드라이버 0(변형 0 · 트리 0) · 제외 3");
    }

    @Test
    @DisplayName("비활성 · 삭제 패키지는 후보가 아니다(제외 목록에도 없다) · 순서는 이름(대소문자 무시) → id · 목록은 CRLF")
    void disabledExcluded_orderAndListText() {
        Subprogram off = driver(11, "aaa", BOARD, null, false);
        Subprogram b = driver(12, "b-pkg", BOARD, null, true);
        Subprogram a2 = driver(14, "A-pkg", BOARD, null, true);
        Subprogram a1 = driver(13, "a-pkg", BOARD, null, true);

        WindowsDriverSelection.Selection sel = WindowsDriverSelection.select(BOARD, WIN2025, List.of(off, b, a2, a1), assembled(off, b, a2, a1));
        assertThat(sel.entries()).extracting(WindowsDriverSelection.Entry::id).containsExactly(13L, 14L, 12L);
        assertThat(sel.skipped()).isEmpty();
        assertThat(sel.toListText()).isEqualTo(
                "TREE|" + WindowsOemPayloadAssembler.folderOf(a1) + "|-|-|0\r\n"
                + "TREE|" + WindowsOemPayloadAssembler.folderOf(a2) + "|-|-|0\r\n"
                + "TREE|" + WindowsOemPayloadAssembler.folderOf(b) + "|-|-|0\r\n");
        assertThat(sel.summary()).isEqualTo("드라이버 3(변형 0 · 트리 3)");
        assertThat(WindowsDriverSelection.Selection.EMPTY.toListText()).isEmpty();
    }
}
