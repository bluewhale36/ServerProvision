package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.vo.InstallTree;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 풀린 디렉터리가 네트워크 설치에 쓸 수 있는 RHEL 계열 트리인지 확인하고 배포판 · 버전을 읽는다.
 * 필수: 트리 설명서 · 설치 커널 · 초기 램디스크 · 설치기 본체 · BaseOS 저장소 색인. 하나라도 없으면 empty.
 */
@Slf4j
@Component
public class InstallTreeInspector {

    static final String TREEINFO = ".treeinfo";
    static final String DEFAULT_STAGE2 = "images/install.img";
    static final String BASEOS_REPOMD = "BaseOS/repodata/repomd.xml";

    public Optional<TreeRelease> inspect(Path treeRoot) {
        Path treeinfo = treeRoot.resolve(TREEINFO);
        if (!Files.isRegularFile(treeinfo)) {
            log.info("[install-tree] 트리 설명서 없음 — RHEL 설치 트리 아님. root={}", treeRoot);
            return Optional.empty();
        }
        Map<String, Map<String, String>> ini;
        try {
            ini = parseIni(Files.readAllLines(treeinfo, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("[install-tree] 트리 설명서를 읽지 못함. root={}, msg={}", treeRoot, e.getMessage());
            return Optional.empty();
        }
        String stage2 = ini.getOrDefault("stage2", Map.of()).getOrDefault("mainimage", DEFAULT_STAGE2);
        for (String required : List.of(InstallTree.KERNEL, InstallTree.INITRD, stage2, BASEOS_REPOMD)) {
            if (!Files.isRegularFile(treeRoot.resolve(required))) {
                log.info("[install-tree] 필수 파일 없음 — RHEL 설치 트리 아님. root={}, missing={}", treeRoot, required);
                return Optional.empty();
            }
        }
        return release(ini);
    }

    /** [release] name · version 이 정본, 옛 형식은 [general] family · version. */
    private static Optional<TreeRelease> release(Map<String, Map<String, String>> ini) {
        Map<String, String> release = ini.getOrDefault("release", Map.of());
        Map<String, String> general = ini.getOrDefault("general", Map.of());
        String name = firstNonBlank(release.get("name"), general.get("family"));
        String version = firstNonBlank(release.get("version"), general.get("version"));
        if (name == null || version == null) {
            return Optional.empty();
        }
        return Optional.of(new TreeRelease(name, version));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    static Map<String, Map<String, String>> parseIni(List<String> lines) {
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
