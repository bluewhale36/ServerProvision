package com.example.serverprovision.execution.engine.windows;

import com.example.serverprovision.management.os.enums.OSFamily;
import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.subprogram.entity.Subprogram;
import com.example.serverprovision.management.subprogram.entity.SubprogramVariant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 서빙 시점의 드라이버 선택(R15-2 D-2) — 의존 0 인 정적 진리표. 게스트의 보드와 정의서의 대상 OS 로 후보를 가르고,
 * 패키지마다 설치할 것 하나를 정한다: 변형이 없으면 트리 전체 INF(현행 · TREE), 변형이 있으면 버전 정확 일치 → 전 버전
 * 변형 → 없으면 제외. 목록이 가리키는 폴더는 게스트 {@code $OEM$} 에 있어야 하므로 매니페스트에 없는 패키지는 제외한다.
 * 결과는 게스트별 {@code spv-drivers.lst}(한 줄 = {@code mode|folder|entrypoint|arguments|reboot} · US-ASCII · 빈 필드는 {@code -})로 내려간다.
 */
public final class WindowsDriverSelection {

    /** 패키지의 설치 모드 — TREE 는 패키지 단위(트리 전체 INF), 나머지는 변형의 진입점 종류다. */
    public enum Mode {
        TREE, INF, MSI, EXE
    }

    public static final String SKIP_NO_VARIANT_FOR_VERSION = "해당 버전 변형 없음";
    public static final String SKIP_NOT_ASSEMBLED = "조립 미반영";
    public static final String SKIP_NON_ASCII = "비 ASCII 경로";

    /** 설치할 항목 하나 — {@code entrypoint} 는 Windows 경로 표기(역슬래시) · TREE 는 빈 문자열. */
    public record Entry(long id, String name, String folder, Mode mode, String entrypoint, String arguments,
                        boolean rebootRequired, String osVersion) {
        /** 빈 필드 자리표시자 — cmd 의 {@code for /f} 는 연속 구분자를 하나로 합쳐 빈 필드가 사라지므로 빈 진입점 · 인자는 이 값으로 채운다. */
        public static final String EMPTY_FIELD = "-";

        public String line() {
            return mode.name() + "|" + folder + "|" + orPlaceholder(entrypoint) + "|" + orPlaceholder(arguments) + "|"
                    + (rebootRequired ? "1" : "0");
        }

        private static String orPlaceholder(String v) {
            return v == null || v.isBlank() ? EMPTY_FIELD : v;
        }

        /** 카드 목록 한 줄 — "이름 · 트리 전체 INF" 또는 "이름 · MSI WDDM\\Win2025.msi · 2025(전 버전)". */
        public String label() {
            if (mode == Mode.TREE) {
                return name + " · 트리 전체 INF";
            }
            return name + " · " + mode.name() + " " + entrypoint + " · " + (osVersion == null ? "전 버전" : osVersion)
                    + (rebootRequired ? " · 재부팅" : "");
        }
    }

    public record Skipped(long id, String name, String reason) {
        public String label() {
            return name + " — " + reason;
        }
    }

    public record Selection(List<Entry> entries, List<Skipped> skipped) {
        public static final Selection EMPTY = new Selection(List.of(), List.of());

        public boolean anyReboot() {
            return entries.stream().anyMatch(Entry::rebootRequired);
        }

        public long countOf(Mode mode) {
            return entries.stream().filter(e -> e.mode() == mode).count();
        }

        /** 게스트가 받는 목록 본문 — 줄마다 한 항목 · CRLF · US-ASCII. 비어 있으면 빈 파일(SetupComplete 는 "선택 0" 으로 읽는다). */
        public String toListText() {
            StringBuilder sb = new StringBuilder();
            for (Entry e : entries) {
                sb.append(e.line()).append("\r\n");
            }
            return sb.toString();
        }

        /** 카드 문구 — "드라이버 N(변형 M · 트리 K) · 제외 J". */
        public String summary() {
            long variants = entries.size() - countOf(Mode.TREE);
            return "드라이버 " + entries.size() + "(변형 " + variants + " · 트리 " + countOf(Mode.TREE) + ")"
                    + (skipped.isEmpty() ? "" : " · 제외 " + skipped.size());
        }
    }

    private WindowsDriverSelection() {
    }

    /**
     * @param boardId          게스트 보드(null 이면 공용 패키지만 후보)
     * @param osTarget         정의서의 대상 OS(미상이면 Windows 계열 · OS 무관 패키지만 · 전 버전 변형만)
     * @param drivers          활성 · 미삭제 DRIVER 패키지 전부(보드 · OS 필터는 여기서)
     * @param assembledFolders 현재 {@code $OEM$} 매니페스트의 폴더명 — 없는 패키지는 게스트에 실물이 없으므로 제외
     */
    public static Selection select(Long boardId, WindowsInstallTarget.OsTarget osTarget, List<Subprogram> drivers,
                                   Set<String> assembledFolders) {
        List<Entry> entries = new ArrayList<>();
        List<Skipped> skipped = new ArrayList<>();
        List<Subprogram> ordered = drivers.stream()
                .filter(s -> s.isEnabled() && !s.isDeleted())
                .filter(s -> appliesToBoard(s, boardId))
                .filter(s -> appliesToOs(s, osTarget))
                .sorted(Comparator.comparing(Subprogram::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Subprogram::getId))
                .toList();
        for (Subprogram s : ordered) {
            String folder = WindowsOemPayloadAssembler.folderOf(s);
            if (assembledFolders == null || !assembledFolders.contains(folder)) {
                skipped.add(new Skipped(s.getId(), s.getName(), SKIP_NOT_ASSEMBLED));
                continue;
            }
            if (s.getVariants().isEmpty()) {
                entries.add(new Entry(s.getId(), s.getName(), folder, Mode.TREE, "", null, false, null));
                continue;
            }
            Optional<SubprogramVariant> variant = pick(s.getVariants(), osTarget);
            if (variant.isEmpty()) {
                skipped.add(new Skipped(s.getId(), s.getName(), SKIP_NO_VARIANT_FOR_VERSION));
                continue;
            }
            SubprogramVariant v = variant.get();
            String entrypoint = v.getEntrypointRelativePath().replace('/', '\\');
            if (!isAscii(entrypoint) || !isAscii(v.getArguments()) || containsPipe(entrypoint) || containsPipe(v.getArguments())) {
                skipped.add(new Skipped(s.getId(), s.getName(), SKIP_NON_ASCII));
                continue;
            }
            entries.add(new Entry(s.getId(), s.getName(), folder, Mode.valueOf(v.entrypointKind().name()), entrypoint,
                    v.getArguments(), v.isRebootRequired(), v.getOsVersion()));
        }
        return new Selection(List.copyOf(entries), List.copyOf(skipped));
    }

    static boolean appliesToBoard(Subprogram s, Long boardId) {
        return s.isCommonScope() || (boardId != null && boardId.equals(s.getBoardId()));
    }

    /** OS 무관(null) 패키지는 항상 후보. 대상 OS 를 알면 같은 OSName, 모르면 Windows 계열이면 후보(대상이 Windows 설치인 것은 요청 타입이 보증). */
    static boolean appliesToOs(Subprogram s, WindowsInstallTarget.OsTarget osTarget) {
        OSName os = s.getOsName();
        if (os == null) {
            return true;
        }
        if (osTarget != null && osTarget.known()) {
            return os == osTarget.osName();
        }
        return os.getFamily() == OSFamily.WINDOWS_BASED;
    }

    /** 버전 정확 일치(정규화 키) → 전 버전(null) 변형 → empty. 대상 버전을 모르면 전 버전 변형만. */
    static Optional<SubprogramVariant> pick(List<SubprogramVariant> variants, WindowsInstallTarget.OsTarget osTarget) {
        String key = osTarget == null ? null : SubprogramVariant.versionKeyOf(osTarget.osVersion());
        if (key != null) {
            Optional<SubprogramVariant> exact = variants.stream().filter(v -> key.equals(v.versionKey())).findFirst();
            if (exact.isPresent()) {
                return exact;
            }
        }
        return variants.stream().filter(SubprogramVariant::appliesToAllVersions).findFirst();
    }

    private static boolean isAscii(String s) {
        return s == null || s.chars().allMatch(c -> c >= 0x20 && c < 0x7f);
    }

    private static boolean containsPipe(String s) {
        return s != null && s.indexOf('|') >= 0;
    }
}
