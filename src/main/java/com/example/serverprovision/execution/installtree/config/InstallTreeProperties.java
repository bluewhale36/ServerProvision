package com.example.serverprovision.execution.installtree.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * 설치 작업 트리 설정({@code provision.install-tree.*}). RHEL 과 Ubuntu 설치가 함께 쓴다. 트리 루트가 비면 기능이 꺼지고,
 * 확보 요청은 {@code NOT_CONFIGURED} 로 실패한다. 다른 루트와의 겹침 검사는 {@link InstallTreeConfig} 가 기동 때 한다.
 *
 * @param root         작업 트리 루트(운영 {@code /var/lib/serverprovision/served/install-trees}). 비면 꺼짐
 * @param releaseGrace 그 ISO 의 마지막 설치가 끝난 뒤 트리를 지우기까지의 유예(곧바로 재시도하는 서버가 다시 풀지 않게)
 */
@ConfigurationProperties(prefix = "provision.install-tree")
public record InstallTreeProperties(String root, Duration releaseGrace) {

    public static final Duration DEFAULT_RELEASE_GRACE = Duration.ofMinutes(10);

    public InstallTreeProperties {
        if (releaseGrace == null) {
            releaseGrace = DEFAULT_RELEASE_GRACE;
        }
        if (releaseGrace.isNegative()) {
            throw new IllegalStateException("provision.install-tree.release-grace 는 음수일 수 없다 : " + releaseGrace);
        }
        if (root != null && !root.isBlank()) {
            validateRoot(root.trim());
        }
    }

    public boolean configured() {
        return root != null && !root.isBlank();
    }

    public Optional<Path> treeRootPath() {
        return configured() ? Optional.of(Path.of(root.trim()).normalize()) : Optional.empty();
    }

    private static void validateRoot(String raw) {
        Path path;
        try {
            path = Path.of(raw);
        } catch (InvalidPathException e) {
            throw new IllegalStateException("provision.install-tree.root 경로가 올바르지 않다 (INSTALL_TREE_ROOT) : " + raw, e);
        }
        if (!path.isAbsolute()) {
            throw new IllegalStateException("provision.install-tree.root 는 절대경로여야 한다 (INSTALL_TREE_ROOT) : " + raw);
        }
        Path normalized = path.normalize();
        if (normalized.getNameCount() == 0) {
            throw new IllegalStateException("provision.install-tree.root 에 root('/') 는 쓸 수 없다 (INSTALL_TREE_ROOT)");
        }
        if (!normalized.toString().equals(raw.replaceAll("/+$", ""))) {
            throw new IllegalStateException("provision.install-tree.root 는 정규화된 경로여야 한다('..' 금지) (INSTALL_TREE_ROOT) : " + raw);
        }
    }
}
