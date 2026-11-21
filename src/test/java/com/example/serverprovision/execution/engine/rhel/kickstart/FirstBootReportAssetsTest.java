package com.example.serverprovision.execution.engine.rhel.kickstart;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirstBootReportAssetsTest {

    @Test
    @DisplayName("스크립트와 유닛을 읽는다. 유닛은 부팅을 붙잡지 않는 simple 형이다")
    void loads() {
        assertThat(FirstBootReportAssets.script()).startsWith("#!/bin/sh\n").contains("remove_all");
        assertThat(FirstBootReportAssets.unit())
                .contains("Type=simple", "ConditionPathExists=/etc/spv/report.env", "After=network-online.target",
                        "WantedBy=multi-user.target")
                .doesNotContain("Type=oneshot");
    }

    @Test
    @DisplayName("heredoc 을 끊는 줄 · ASCII 밖의 글자가 있으면 프로그램 오류다")
    void check_rejects() {
        assertThatThrownBy(() -> FirstBootReportAssets.check("x", "echo a\nSPV_EOF\n")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> FirstBootReportAssets.check("x", "echo a\n  %end")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> FirstBootReportAssets.check("x", "echo 보고")).isInstanceOf(IllegalStateException.class);
        assertThat(FirstBootReportAssets.check("x", "echo '%{http_code}'")).isEqualTo("echo '%{http_code}'");
    }

    @Test
    @DisplayName("스크립트는 sh 문법 검사를 통과한다(sh 가 없으면 건너뜀)")
    void script_parses(@TempDir Path dir) throws IOException, InterruptedException {
        Assumptions.assumeTrue(Files.isExecutable(Path.of("/bin/sh")), "/bin/sh 없음");
        Path file = Files.writeString(dir.resolve("spv-report"), FirstBootReportAssets.script());
        Process p = new ProcessBuilder("/bin/sh", "-n", file.toString()).redirectErrorStream(true).start();
        assertThat(p.waitFor(10, TimeUnit.SECONDS)).isTrue();
        assertThat(p.exitValue()).as(new String(p.getInputStream().readAllBytes())).isZero();
    }
}
