package com.example.serverprovision.execution.installtree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 시험용 가짜 Rocky 설치 트리 · ISO. 트리는 설치기가 읽는 필수 파일만 담는다.
 * ISO 로 묶는 일은 {@link FakeIso} 가 한다(도구가 없으면 empty).
 */
public final class FakeRockyTree {

    private FakeRockyTree() {
    }

    public static Path writeTree(Path dir, String name, String version) throws IOException {
        Files.createDirectories(dir.resolve("images/pxeboot"));
        Files.createDirectories(dir.resolve("BaseOS/repodata"));
        Files.createDirectories(dir.resolve("BaseOS/Packages/b"));
        Files.createDirectories(dir.resolve("AppStream/repodata"));
        Files.writeString(dir.resolve(".treeinfo"), """
                [general]
                family = %1$s
                version = %2$s
                [release]
                name = %1$s
                version = %2$s
                [stage2]
                mainimage = images/install.img
                """.formatted(name, version));
        Files.writeString(dir.resolve(".discinfo"), "fake\n");
        Files.writeString(dir.resolve("images/pxeboot/vmlinuz"), "kernel");
        Files.writeString(dir.resolve("images/pxeboot/initrd.img"), "initrd");
        Files.writeString(dir.resolve("images/install.img"), "stage2");
        Files.writeString(dir.resolve("BaseOS/repodata/repomd.xml"), "<repomd/>");
        Files.writeString(dir.resolve("AppStream/repodata/repomd.xml"), "<repomd/>");
        Files.writeString(dir.resolve("BaseOS/Packages/b/bash-5.1.8-9.el9.x86_64.rpm"), "rpm");
        return dir;
    }

    public static Optional<Path> buildIso(Path workDir, String name, String version) throws IOException, InterruptedException {
        Path src = writeTree(Files.createDirectories(workDir.resolve("iso-src-" + version)), name, version);
        return FakeIso.pack(src, workDir.resolve("Rocky-" + version + "-fake.iso"));
    }

    public static boolean commandAvailable(String command) {
        return FakeIso.commandAvailable(command);
    }
}
