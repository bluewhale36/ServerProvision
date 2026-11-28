package com.example.serverprovision.execution.installtree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 시험용 가짜 Ubuntu 설치 ISO. casper 의 커널 · initrd, {@code .disk/info}, 꺼내지 않아야 할 squashfs 하나를 담는다.
 * 실제 live-server ISO 와 같은 배치이고, {@code .disk/info} 첫 줄만 인자로 바꿔 desktop · arm64 를 흉내 낸다.
 */
public final class FakeUbuntuIso {

    public static final String SERVER_2404 = "Ubuntu-Server 24.04.1 LTS \"Noble Numbat\" - Release amd64 (20240827)";
    public static final String SERVER_2204 = "Ubuntu-Server 22.04.4 LTS \"Jammy Jellyfish\" - Release amd64 (20240216)";
    public static final String DESKTOP_2404 = "Ubuntu 24.04.1 LTS \"Noble Numbat\" - Release amd64 (20240827)";
    public static final String SERVER_2404_ARM = "Ubuntu-Server 24.04.1 LTS \"Noble Numbat\" - Release arm64 (20240827)";

    private FakeUbuntuIso() {
    }

    public static Path writeTree(Path dir, String diskInfo) throws IOException {
        Files.createDirectories(dir.resolve("casper"));
        Files.createDirectories(dir.resolve(".disk"));
        Files.writeString(dir.resolve("casper/vmlinuz"), "kernel");
        Files.writeString(dir.resolve("casper/initrd"), "initrd");
        Files.writeString(dir.resolve("casper/ubuntu-server-minimal.squashfs"), "squashfs-not-extracted");
        Files.writeString(dir.resolve(".disk/info"), diskInfo + "\n");
        return dir;
    }

    /** @param name ISO 파일 이름에 붙일 짧은 구별자(예 "2404") */
    public static Optional<Path> buildIso(Path workDir, String name, String diskInfo) throws IOException, InterruptedException {
        Path src = writeTree(Files.createDirectories(workDir.resolve("ubuntu-src-" + name)), diskInfo);
        return FakeIso.pack(src, workDir.resolve("ubuntu-" + name + "-fake.iso"));
    }
}
