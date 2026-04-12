package com.example.serverprovision.domain.os.model.installation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KickstartContextTest {

    @Test
    @DisplayName("installSourceUrl이 null이면 IllegalArgumentException 발생")
    void throwsWhenInstallSourceUrlIsNull() {
        assertThatThrownBy(() -> new KickstartContext("host", "10.0.0.1", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("installSourceUrl");
    }

    @Test
    @DisplayName("installSourceUrl이 공백이면 IllegalArgumentException 발생")
    void throwsWhenInstallSourceUrlIsBlank() {
        assertThatThrownBy(() -> new KickstartContext("host", "10.0.0.1", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("installSourceUrl");
    }

    @Test
    @DisplayName("모든 필드가 유효하면 정상 생성")
    void createsSuccessfully_whenAllFieldsValid() {
        KickstartContext ctx = new KickstartContext("test-server", "10.0.0.1", "http://192.168.1.1/rocky9");

        assertThat(ctx.hostname()).isEqualTo("test-server");
        assertThat(ctx.assignedIp()).isEqualTo("10.0.0.1");
        assertThat(ctx.installSourceUrl()).isEqualTo("http://192.168.1.1/rocky9");
    }

    @Test
    @DisplayName("hostname이 null이어도 정상 생성")
    void hostnameCanBeNull() {
        KickstartContext ctx = new KickstartContext(null, "10.0.0.1", "http://192.168.1.1/rocky9");

        assertThat(ctx.hostname()).isNull();
        assertThat(ctx.installSourceUrl()).isEqualTo("http://192.168.1.1/rocky9");
    }
}
