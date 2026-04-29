package com.example.serverprovision.management.bios.service;

import com.example.serverprovision.global.marker.service.ProvisionMarkerService;
import com.example.serverprovision.global.security.EntrypointPolicyService;
import com.example.serverprovision.global.security.config.FileSystemSecurityProperties;
import com.example.serverprovision.management.common.filesystem.exception.BundleExtractionException;
import com.example.serverprovision.management.bios.exception.EntrypointAmbiguousException;
import com.example.serverprovision.management.bios.exception.EntrypointNotFoundException;
import com.example.serverprovision.management.common.filesystem.policy.BundleFilePolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 번들 트리의 진입점(= BIOS 업데이트의 "대상 파일") 결정. 규칙 상세는 클래스 상단 Javadoc 이전 CP2 기록 참조.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BundleEntrypointDetector {

    private final EntrypointPolicyService entrypointPolicyService;
    private final FileSystemSecurityProperties fileSystemSecurityProperties;

    public String detect(Path treeRoot, String override) {
        // 1) Override 우선 — S3 EntrypointPolicy 검증 (절대경로 / .. / 트리 밖 / null byte / 길이)
        if (override != null && !override.isBlank()) {
            String normalized = entrypointPolicyService.validateAndNormalize(treeRoot, override);
            if (normalized == null) {
                // EntrypointPolicy 가 빈 입력으로 처리한 경우 — auto-detect 로 fallthrough
            } else {
                Path candidate = treeRoot.resolve(normalized);
                if (!Files.isRegularFile(candidate)) {
                    throw new EntrypointNotFoundException(
                            "명시된 진입점 경로에 파일이 없습니다 : " + normalized);
                }
                return normalized;
            }
        }

        // 2) 트리 파일 1개면 그것 (marker / OS 잡파일 제외)
        List<Path> allFiles = listRegularFilesExcludingMarker(treeRoot);
        if (allFiles.size() == 1) {
            Path only = allFiles.get(0);
            return toRelative(treeRoot, only);
        }

        if (allFiles.isEmpty()) {
            throw new EntrypointNotFoundException("번들에 진입점으로 쓸 수 있는 파일이 없습니다.");
        }

        // 3~5) 루트(바로 아래) 수준의 .nsh 후보만 검사
        List<Path> rootNsh = listRootNshFiles(treeRoot);
        Path fNsh     = findByName(rootNsh, "f.nsh");
        Path flashNsh = findByName(rootNsh, "flash.nsh");

        if (fNsh != null)     return "f.nsh";
        if (flashNsh != null) return "flash.nsh";
        if (rootNsh.size() == 1) return toRelative(treeRoot, rootNsh.get(0));

        if (rootNsh.isEmpty()) {
            throw new EntrypointNotFoundException(
                    "트리 루트에서 f.nsh · flash.nsh · 단일 *.nsh 어느 것도 발견되지 않았습니다. 진입점을 명시 지정하세요.");
        }

        List<String> candidates = rootNsh.stream()
                .map(p -> toRelative(treeRoot, p))
                .sorted()
                .toList();
        throw new EntrypointAmbiguousException(candidates);
    }

    private List<Path> listRegularFilesExcludingMarker(Path treeRoot) {
        List<Path> result = new ArrayList<>();
        // S3.1 (A3) — maxDepth 명시. follow-links 옵션 부재로 symlink 미추적.
        try (Stream<Path> walker = Files.walk(treeRoot, fileSystemSecurityProperties.maxDepth())) {
            for (Path p : (Iterable<Path>) walker::iterator) {
                if (!Files.isRegularFile(p)) continue;
                if (p.getFileName().toString().equals(ProvisionMarkerService.MARKER_FILENAME)) continue;
                if (BundleFilePolicy.isIgnorable(p)) continue;
                result.add(p);
            }
        } catch (IOException e) {
            throw new BundleExtractionException("진입점 탐지 중 트리 스캔 실패 : " + treeRoot, e);
        }
        result.sort(Comparator.comparing(p -> p.getFileName().toString()));
        return result;
    }

    private List<Path> listRootNshFiles(Path treeRoot) {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> children = Files.list(treeRoot)) {
            children.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".nsh"))
                    .filter(p -> !p.getFileName().toString().equals(ProvisionMarkerService.MARKER_FILENAME))
                    .filter(p -> !BundleFilePolicy.isIgnorable(p))
                    .forEach(result::add);
        } catch (IOException e) {
            throw new BundleExtractionException("루트 .nsh 스캔 실패 : " + treeRoot, e);
        }
        return result;
    }

    private static Path findByName(List<Path> files, String name) {
        for (Path p : files) {
            if (p.getFileName().toString().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private static String toRelative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
