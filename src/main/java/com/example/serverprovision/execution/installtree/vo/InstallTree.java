package com.example.serverprovision.execution.installtree.vo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 게시된 설치 작업 트리 하나. 서빙은 요청 경로를 {@link #resolve(String)} 로만 파일에 대응시킨다.
 * 커널 · initrd 의 위치는 계열마다 달라 {@code InstallMediaLayout} 이 가진다.
 *
 * @param root    트리 최상위(정규화 · 절대경로)
 * @param release 꺼낸 매체가 밝히는 배포판 · 버전
 * @param source  등록된 ISO 경로. 파일이면 Ubuntu 설치가 이 파일을 그대로 서빙하고, 디렉터리 등록이면 root 와 같다
 */
public record InstallTree(Path root, TreeRelease release, Path source) {

    /** 숨김 파일 중 설치기가 읽는 트리 설명서만 내준다. 디렉터리로 등록된 ISO 의 in-tree 마커 등은 거절된다. */
    private static final Set<String> SERVED_DOT_FILES = Set.of(".treeinfo", ".discinfo");

    public InstallTree {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(release, "release");
        Objects.requireNonNull(source, "source");
        root = root.toAbsolutePath().normalize();
        source = source.toAbsolutePath().normalize();
    }

    /**
     * 트리 상대경로를 실제 일반 파일로 해석한다. 절대경로 · {@code ..} · 숨김 조각(트리 설명서 제외) ·
     * 트리 밖을 가리키는 심볼릭 링크 · 없는 파일 · 디렉터리는 모두 empty.
     */
    public Optional<Path> resolve(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || relativePath.startsWith("/")
                || relativePath.contains("\\") || relativePath.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        String[] segments = relativePath.split("/");
        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return Optional.empty();
            }
            boolean topLevelDotFile = i == 0 && segments.length == 1 && SERVED_DOT_FILES.contains(segment);
            if (segment.startsWith(".") && !topLevelDotFile) {
                return Optional.empty();
            }
        }
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            return Optional.empty();
        }
        try {
            Path real = candidate.toRealPath();
            return real.startsWith(root.toRealPath()) ? Optional.of(real) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
