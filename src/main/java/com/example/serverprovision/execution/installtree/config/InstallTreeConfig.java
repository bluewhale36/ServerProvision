package com.example.serverprovision.execution.installtree.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 작업 트리 루트가 다른 파일 루트와 같거나 서로 포함하면 기동을 막는다. 기동 정리가 트리 루트의 직계 자식을
 * 지우므로, 겹치면 자원 · 자산 · 휴지통을 지우는 사고가 된다({@code PxeBootProperties} 의 fail-fast 선례).
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InstallTreeProperties.class)
public class InstallTreeConfig {

    private final InstallTreeProperties properties;
    private final Map<String, String> otherRoots;

    public InstallTreeConfig(
            InstallTreeProperties properties,
            @Value("${provision.path.allowed-roots:}") String allowedRoots,
            @Value("${pxe.assets.root:}") String pxeAssetsRoot,
            @Value("${trash.root:}") String trashRoot,
            @Value("${orphan.quarantine.root:}") String quarantineRoot,
            @Value("${provision.asset.history.root:}") String assetHistoryRoot,
            @Value("${provision.windows-install.source-root:}") String windowsSourceRoot
    ) {
        this.properties = properties;
        this.otherRoots = Map.of(
                "provision.path.allowed-roots", allowedRoots,
                "pxe.assets.root", pxeAssetsRoot,
                "trash.root", trashRoot,
                "orphan.quarantine.root", quarantineRoot,
                "provision.asset.history.root", assetHistoryRoot,
                "provision.windows-install.source-root", windowsSourceRoot
        );
    }

    @PostConstruct
    void validate() {
        if (properties.treeRootPath().isEmpty()) {
            log.info("[install-tree] provision.install-tree.root 미설정 — 설치 작업 트리 꺼짐(RHEL · Ubuntu 설치 불가)");
            return;
        }
        Path treeRoot = properties.treeRootPath().get();
        for (Map.Entry<String, String> entry : otherRoots.entrySet()) {
            for (Path other : split(entry.getValue())) {
                if (treeRoot.startsWith(other) || other.startsWith(treeRoot)) {
                    throw new IllegalStateException("provision.install-tree.root(" + treeRoot + ") 가 "
                            + entry.getKey() + "(" + other + ") 와 같거나 서로 포함한다. 전용 디렉터리를 지정해야 한다 (INSTALL_TREE_ROOT).");
                }
            }
        }
        log.info("[install-tree] 작업 트리 루트={} · 유예={}", treeRoot, properties.releaseGrace());
    }

    private static List<Path> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Path> paths = new ArrayList<>();
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(Predicate.not(String::isEmpty))
                .forEach(s -> paths.add(Path.of(s).toAbsolutePath().normalize()));
        return paths;
    }
}
