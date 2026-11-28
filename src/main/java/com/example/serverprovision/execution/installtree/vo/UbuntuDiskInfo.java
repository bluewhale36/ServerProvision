package com.example.serverprovision.execution.installtree.vo;

import com.example.serverprovision.management.os.enums.OSName;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ubuntu ISO 의 {@code .disk/info} 첫 줄이 밝히는 버전과 아키텍처. live-server ISO 는
 * {@code Ubuntu-Server 24.04.1 LTS "Noble Numbat" - Release amd64 (20240827)} 꼴이다.
 * 배포판 이름은 OS 카탈로그와 같은 "Ubuntu" 로 담는다. 그래서 버전 대조는 RHEL 과 같은 규칙({@code TreeInfo.matches})이 맡는다.
 *
 * @param version 점 버전("24.04.1", 점 버전이 없는 첫 배포는 "22.04")
 * @param arch    아키텍처 이름("amd64", "arm64" 등)
 */
public record UbuntuDiskInfo(String version, String arch) {

    public static final String FILE = ".disk/info";

    /** desktop ISO 는 "Ubuntu 24.04 …" 로 시작해 맞지 않는다. 서버 설치기는 live-server ISO 에만 있다. */
    private static final Pattern SERVER = Pattern.compile(
            "^Ubuntu-Server (\\d+\\.\\d+(?:\\.\\d+)?)\\b.*\\b(amd64|arm64|ppc64el|s390x|riscv64)\\b");
    private static final String INSTALLABLE_ARCH = "amd64";

    /** 루트의 {@code .disk/info} 첫 줄을 읽는다. 파일이 없거나 live-server 줄이 아니면 empty. */
    public static Optional<UbuntuDiskInfo> read(Path root) {
        Path file = root.resolve(FILE);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return parse(reader.readLine());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static Optional<UbuntuDiskInfo> parse(String firstLine) {
        if (firstLine == null) {
            return Optional.empty();
        }
        Matcher matcher = SERVER.matcher(firstLine.strip());
        return matcher.find() ? Optional.of(new UbuntuDiskInfo(matcher.group(1), matcher.group(2))) : Optional.empty();
    }

    /** 우리 서버에 설치할 수 있는 아키텍처인가. 프로비저닝 대상은 x86_64 서버뿐이다. */
    public boolean installable() {
        return INSTALLABLE_ARCH.equals(arch);
    }

    public TreeRelease release() {
        return new TreeRelease(OSName.UBUNTU.getDisplayName(), version);
    }
}
