package com.example.serverprovision.management.os.vo;

import com.example.serverprovision.management.os.enums.OSName;
import com.example.serverprovision.management.os.enums.RhelRelease;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RHEL 계열 설치 ISO 루트의 트리 설명서({@code .treeinfo})가 밝히는 배포판 · 버전 · 설치기 본체 위치.
 * ISO 등록 뒤 추출 작업의 버전 확인(E4-1-b-2 D-20)과 설치 작업 트리 판정(E4-1-b-1)이 함께 읽는다 —
 * 관리 영역이 실행 영역을 참조하지 않게 해석을 여기 둔다.
 *
 * @param name    {@code [release] name}, 옛 형식은 {@code [general] family}
 * @param version {@code [release] version}, 옛 형식은 {@code [general] version}
 * @param stage2  {@code [stage2] mainimage}, 없으면 {@link #DEFAULT_STAGE2}
 */
public record TreeInfo(String name, String version, String stage2) {

    public static final String FILE_NAME = ".treeinfo";
    public static final String DEFAULT_STAGE2 = "images/install.img";

    /** 루트의 트리 설명서를 읽는다. 파일이 없거나 배포판 · 버전이 비어 있으면 empty. 읽기 실패는 예외. */
    public static Optional<TreeInfo> read(Path root) {
        Path file = root.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return parse(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Optional<TreeInfo> parse(List<String> lines) {
        Map<String, Map<String, String>> ini = parseIni(lines);
        Map<String, String> release = ini.getOrDefault("release", Map.of());
        Map<String, String> general = ini.getOrDefault("general", Map.of());
        String name = firstNonBlank(release.get("name"), general.get("family"));
        String version = firstNonBlank(release.get("version"), general.get("version"));
        if (name == null || version == null) {
            return Optional.empty();
        }
        String stage2 = firstNonBlank(ini.getOrDefault("stage2", Map.of()).get("mainimage"), DEFAULT_STAGE2);
        return Optional.of(new TreeInfo(name, version, stage2));
    }

    /** 화면 이름 — "Rocky Linux 9.5". */
    public String label() {
        return name + " " + version;
    }

    /** 등록한 OS(이름 · 버전)와 같은 배포판 · 같은 주 버전인가. 버전 형식을 못 읽으면 다르다고 본다. */
    public boolean matches(OSName osName, String osVersion) {
        if (!name.equalsIgnoreCase(osName.getDisplayName())) {
            return false;
        }
        Optional<Integer> treeMajor = RhelRelease.majorOf(version);
        return treeMajor.isPresent() && treeMajor.equals(RhelRelease.majorOf(osVersion));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    private static Map<String, Map<String, String>> parseIni(List<String> lines) {
        Map<String, Map<String, String>> sections = new HashMap<>();
        Map<String, String> current = null;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                current = sections.computeIfAbsent(line.substring(1, line.length() - 1).trim(), k -> new HashMap<>());
                continue;
            }
            int eq = line.indexOf('=');
            if (current != null && eq > 0) {
                current.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return sections;
    }
}
