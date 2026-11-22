package com.example.serverprovision.execution.engine.rhel.kickstart;

import com.example.serverprovision.execution.vo.GuestToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirstBootReportTest {

    private static final UUID SYSTEM = UUID.fromString("4c4c4544-0042-3510-8051-b4c04f4e3332");
    private static final UUID GUEST = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final GuestToken TOKEN = new GuestToken("0123456789abcdef0123456789abcdef");

    @Test
    @DisplayName("기준 주소에서 보고 주소와 서버 상세 주소를 만든다")
    void of_buildsUrls() {
        FirstBootReport report = FirstBootReport.of("http://10.0.0.7:8080", TOKEN, SYSTEM, "RE2108", GUEST);
        assertThat(report.reportUrl()).isEqualTo("http://10.0.0.7:8080/api/pxe/v1/agent/rhel/complete");
        assertThat(report.detailUrl()).isEqualTo("http://10.0.0.7:8080/provisioning/server/" + GUEST);
        assertThat(report.consoleSerial()).isEqualTo("RE2108");
    }

    @Test
    @DisplayName("시리얼은 콘솔이 그릴 수 있는 글자만 남기고 64 자에서 자르며 비면 '-'")
    void consoleSerial() {
        assertThat(FirstBootReport.consoleSerialOf(" To Be Filled By O.E.M. ")).isEqualTo("To_Be_Filled_By_O.E.M.");
        assertThat(FirstBootReport.consoleSerialOf("시리얼'#\"\\1")).isEqualTo("_______1");
        assertThat(FirstBootReport.consoleSerialOf("A".repeat(80))).hasSize(64);
        assertThat(FirstBootReport.consoleSerialOf(null)).isEqualTo("-");
        assertThat(FirstBootReport.consoleSerialOf("  ")).isEqualTo("-");
    }

    @Test
    @DisplayName("env 줄을 깨는 값(공백 · 따옴표)은 프로그램 오류다")
    void unsafeValue_rejected() {
        assertThatThrownBy(() -> FirstBootReport.of("http://bad host", TOKEN, SYSTEM, null, GUEST))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new FirstBootReport("http://x/r", new GuestToken("ab'cd"), SYSTEM, "-", "http://x/d"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("toString 에 토큰이 없다")
    void toString_masksToken() {
        assertThat(FirstBootReport.of("http://x", TOKEN, SYSTEM, null, GUEST).toString())
                .doesNotContain(TOKEN.value()).contains("guestToken=***");
    }
}
