package com.example.serverprovision.execution.installtree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 시험용 가짜 Rocky 설치 트리 · ISO. 트리는 설치기가 읽는 필수 파일만 담는다.
 * ISO 는 macOS {@code hdiutil} 또는 Linux {@code xorriso} 로 만들며, 둘 다 없으면 empty(시험은 assume 으로 건너뜀).
 * 파일 이름은 64 자 이하로 둔다 — hdiutil 은 Rock Ridge 를 쓰지 않아 긴 이름을 자른다(긴 이름 실측은 b-4 이관).
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
        Path iso = workDir.resolve("Rocky-" + version + "-fake.iso");
        List<List<String>> makers = List.of(
                List.of("hdiutil", "makehybrid", "-iso", "-joliet", "-o", iso.toString(), src.toString()),
                List.of("xorriso", "-as", "mkisofs", "-R", "-J", "-o", iso.toString(), src.toString())
        );
        for (List<String> command : makers) {
            if (!commandAvailable(command.get(0))) {
                continue;
            }
            Process proc = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            if (proc.waitFor(60, TimeUnit.SECONDS) && proc.exitValue() == 0 && Files.isRegularFile(iso)) {
                return Optional.of(iso);
            }
        }
        return Optional.empty();
    }

    public static boolean commandAvailable(String command) {
        try {
            return new ProcessBuilder("which", command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
