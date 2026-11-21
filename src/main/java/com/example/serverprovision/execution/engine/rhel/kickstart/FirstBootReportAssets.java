package com.example.serverprovision.execution.engine.rhel.kickstart;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 첫 부팅 보고 유닛의 파일 원문(E4-1-b-5). 셸 스크립트와 systemd 유닛을 classpath 자원으로 두어 셸 문법 그대로 읽고 검사한다.
 * kickstart 의 heredoc 에 그대로 실리므로 구분자 줄, {@code %end} 줄, ASCII 밖의 글자가 있으면 프로그램 오류다.
 */
final class FirstBootReportAssets {

    static final String HEREDOC_END = "SPV_EOF";

    private static final String SCRIPT = load("kickstart/spv-report.sh");
    private static final String UNIT = load("kickstart/spv-report.service");

    private FirstBootReportAssets() {
    }

    static String script() {
        return SCRIPT;
    }

    static String unit() {
        return UNIT;
    }

    private static String load(String path) {
        try (InputStream in = FirstBootReportAssets.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("classpath 자원이 없습니다: " + path);
            }
            return check(path, new String(in.readAllBytes(), StandardCharsets.UTF_8).stripTrailing());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** heredoc 에 실을 수 있는 원문인지 본다. */
    static String check(String path, String text) {
        for (String line : text.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.equals(HEREDOC_END) || trimmed.equals("%end")) {
                throw new IllegalStateException(path + " 에 heredoc 을 끊는 줄이 있습니다: " + trimmed);
            }
        }
        if (!text.chars().allMatch(c -> c == '\n' || c == '\t' || (c >= 0x20 && c < 0x7f))) {
            throw new IllegalStateException(path + " 에 ASCII 밖의 글자가 있습니다.");
        }
        return text;
    }
}
