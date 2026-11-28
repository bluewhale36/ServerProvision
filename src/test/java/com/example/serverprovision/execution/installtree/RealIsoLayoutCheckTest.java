package com.example.serverprovision.execution.installtree;

import com.example.serverprovision.execution.installtree.enums.InstallMediaLayout;
import com.example.serverprovision.execution.installtree.vo.TreeRelease;
import com.example.serverprovision.management.os.service.iso.IsoPreparationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 ISO 로 Ubuntu 매체 모양을 확인한다(CP5 A7, B3). 내려받은 ISO 는 저장소에 두지 않으므로 평소에는 건너뛰고,
 * 하네스가 {@code INSTALLTREE_REAL_ISOS} 에 경로를 쉼표로 이어 줄 때만 돈다. 이름에 live-server 가 든 ISO 는 판별되어야 하고,
 * 그 밖의 ISO(RHEL DVD 등)는 지정 파일이 없어 판별되지 않아야 한다.
 */
@EnabledIfEnvironmentVariable(named = "INSTALLTREE_REAL_ISOS", matches = ".+")
class RealIsoLayoutCheckTest {

    private final IsoPreparationService iso = new IsoPreparationService();

    @Test
    @DisplayName("A7 실제 ISO: live-server 는 Ubuntu 로 판별되고 커널 · initrd · .disk/info 만 나온다, 다른 ISO 는 판별되지 않는다")
    void realIsos(@TempDir Path temp) throws IOException {
        List<Path> isos = Arrays.stream(System.getenv("INSTALLTREE_REAL_ISOS").split(","))
                .map(String::strip).filter(s -> !s.isEmpty()).map(Path::of).toList();
        for (Path file : isos) {
            Path staging = Files.createDirectories(temp.resolve(file.getFileName() + "-out"));
            long started = System.nanoTime();
            InstallMediaLayout.UBUNTU_LIVE_SERVER.extract(iso, file, staging);
            Optional<TreeRelease> release = InstallMediaLayout.UBUNTU_LIVE_SERVER.inspect(staging);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            evidence(file, staging, release, elapsedMs);

            boolean liveServer = file.getFileName().toString().contains("live-server");
            assertThat(release.isPresent()).as(file.getFileName().toString()).isEqualTo(liveServer);
        }
    }

    private static void evidence(Path file, Path staging, Optional<TreeRelease> release, long elapsedMs) throws IOException {
        StringBuilder out = new StringBuilder();
        out.append("ISO: ").append(file.getFileName()).append(" (").append(Files.size(file)).append(" bytes)\n");
        out.append("  판별: ").append(release.map(TreeRelease::label).orElse("판별 안 됨(NOT_UBUNTU_SERVER_ISO)")).append('\n');
        out.append("  소요: ").append(elapsedMs).append(" ms\n");
        Path info = staging.resolve(".disk/info");
        if (Files.isRegularFile(info)) {
            out.append("  .disk/info 첫 줄: ").append(Files.readAllLines(info, StandardCharsets.UTF_8).get(0)).append('\n');
        }
        try (Stream<Path> walk = Files.walk(staging)) {
            for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                out.append("  꺼낸 파일: ").append(staging.relativize(p)).append(" (").append(Files.size(p)).append(" bytes)\n");
            }
        }
        System.out.print(out);
        String dir = System.getenv("INSTALLTREE_EVIDENCE_DIR");
        if (dir != null && !dir.isBlank()) {
            Files.writeString(Files.createDirectories(Path.of(dir)).resolve("A7.txt"), out.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }
}
