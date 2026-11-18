package com.example.serverprovision.execution.installtree.service;

import com.example.serverprovision.execution.installtree.config.InstallTreeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 작업 트리 루트 위의 디렉터리 규칙과 파일 조작. {@code iso-<id>} = 게시된 트리, {@code .staging/<uuid>} = 추출 중
 * 또는 삭제 대기. 같은 파일 시스템 안의 rename 이라 게시 · 회수가 원자적이다 — 반쯤 풀린 트리가 서빙될 틈이 없다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstallTreeStore {

    static final String STAGING = ".staging";
    private static final Pattern TREE_DIR = Pattern.compile("iso-\\d+");

    private final InstallTreeProperties properties;

    public Path treeDir(long isoId) {
        return root().resolve("iso-" + isoId);
    }

    public Path newStagingDir() throws IOException {
        return Files.createDirectories(root().resolve(STAGING).resolve(UUID.randomUUID().toString()));
    }

    public long usableSpace() throws IOException {
        Files.createDirectories(root());
        return Files.getFileStore(root()).getUsableSpace();
    }

    /** 추출을 마친 staging 을 {@code iso-<id>} 로 rename. 남은 옛 트리가 있으면 먼저 회수한다. */
    public Path publish(Path staging, long isoId) throws IOException {
        Path target = treeDir(isoId);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            delete(retire(target));
        }
        ensureOwnerWritable(staging);
        return Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
    }

    /** 게시된 트리를 즉시 서빙 경로에서 빼(staging 아래로 rename) 삭제 대기 경로를 돌려준다. 삭제는 {@link #delete}. */
    public Path retire(Path tree) throws IOException {
        Path graveyard = Files.createDirectories(root().resolve(STAGING)).resolve("retired-" + UUID.randomUUID());
        if (!Files.isSymbolicLink(tree)) {
            ensureOwnerWritable(tree);
        }
        return Files.move(tree, graveyard, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * 트리 재귀 삭제. 심볼릭 링크는 따라가지 않고 링크만 지운다(HF6 {@code PurgeExecutorImpl.deleteRecursively} 선례).
     * ISO 에서 풀린 디렉터리는 읽기 전용(0555)인 경우가 많아, 들어가기 전에 소유자 쓰기 권한을 준다.
     *
     * @return 다 지웠으면(또는 원래 없으면) 참. 실패는 WARN 으로 남긴다
     */
    public boolean delete(Path target) {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return true;
        }
        try {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    ensureOwnerWritable(dir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            log.warn("[install-tree] 삭제 실패. path={}, cause={}: {}", target, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * 기동 정리 — 루트를 만들고, 직계 자식 중 이름 규칙({@code iso-<숫자>} · {@code .staging})에 맞는 것만 지운다.
     * 규칙 밖의 파일은 운영자 것일 수 있어 남긴다. 재기동 전 설치는 토큰이 사라져 어차피 이어질 수 없다(plan D-5).
     */
    public CleanupResult cleanOnStartup() throws IOException {
        Path root = Files.createDirectories(root());
        int removed = 0;
        int failed = 0;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
            for (Path child : children) {
                String name = child.getFileName().toString();
                if (!name.equals(STAGING) && !TREE_DIR.matcher(name).matches()) {
                    continue;
                }
                if (delete(child)) {
                    removed++;
                } else {
                    failed++;
                }
            }
        }
        return new CleanupResult(removed, failed);
    }

    /** 기동 정리 결과 — 지운 항목 수와 지우지 못한 항목 수. */
    public record CleanupResult(int removed, int failed) {
    }

    private Path root() {
        return properties.treeRootPath()
                .orElseThrow(() -> new IllegalStateException("provision.rhel-install.tree-root 미설정 상태에서 작업 트리 저장소를 쓸 수 없다"));
    }

    private static void ensureOwnerWritable(Path dir) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(dir, PosixFileAttributeView.class);
        if (view == null) {
            return;
        }
        Set<PosixFilePermission> perms = EnumSet.noneOf(PosixFilePermission.class);
        perms.addAll(view.readAttributes().permissions());
        if (perms.add(PosixFilePermission.OWNER_WRITE) | perms.add(PosixFilePermission.OWNER_EXECUTE)
                | perms.add(PosixFilePermission.OWNER_READ)) {
            view.setPermissions(perms);
        }
    }
}
