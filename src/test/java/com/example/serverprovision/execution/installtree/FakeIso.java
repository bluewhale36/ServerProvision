package com.example.serverprovision.execution.installtree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 시험용 디렉터리를 ISO 로 묶는다. macOS {@code hdiutil} 또는 Linux {@code xorriso} 를 쓰고, 둘 다 없으면 empty(시험은 assume 으로 건너뜀).
 * hdiutil 은 Rock Ridge 를 쓰지 않아 64 자가 넘는 이름을 자르므로 파일 이름을 짧게 둔다.
 */
public final class FakeIso {

    private FakeIso() {
    }

    public static Optional<Path> pack(Path src, Path iso) throws IOException, InterruptedException {
        List<List<String>> makers = List.of(
                List.of("hdiutil", "makehybrid", "-iso", "-joliet", "-o", iso.toString(), src.toString()),
                List.of("xorriso", "-as", "mkisofs", "-R", "-J", "-o", iso.toString(), src.toString())
        );
        for (List<String> command : makers) {
            if (!commandAvailable(command.get(0))) {
                continue;
            }
            Files.deleteIfExists(iso);
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
