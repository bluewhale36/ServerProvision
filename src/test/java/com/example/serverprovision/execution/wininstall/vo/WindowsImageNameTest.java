package com.example.serverprovision.execution.wininstall.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E4-1-a-2 CP4 — 설치 이미지 이름 VO: 불변식 · JSON 평문 왕복. */
class WindowsImageNameTest {

    @Test
    @DisplayName("trim 후 보존 · 대소문자 구분 동등성")
    void normalizes() {
        assertThat(new WindowsImageName("  Windows Server 2025 SERVERSTANDARD ").value()).isEqualTo("Windows Server 2025 SERVERSTANDARD");
        assertThat(new WindowsImageName("A")).isNotEqualTo(new WindowsImageName("a"));
    }

    @Test
    @DisplayName("공백 · null · 255 초과 · 제어문자 → IllegalArgumentException")
    void rejectsInvalid() {
        assertThatThrownBy(() -> new WindowsImageName("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WindowsImageName(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WindowsImageName("x".repeat(256))).isInstanceOf(IllegalArgumentException.class);
        String withControl = "bad" + (char) 1 + "name";
        assertThatThrownBy(() -> new WindowsImageName(withControl)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("JSON 은 평문 문자열로 오간다 (@JsonValue / DELEGATING creator)")
    void jsonRoundTrip() {
        JsonMapper mapper = JsonMapper.builder().build();
        String json = mapper.writeValueAsString(new WindowsImageName("Windows Server 2025 SERVERSTANDARD"));
        assertThat(json).isEqualTo("\"Windows Server 2025 SERVERSTANDARD\"");
        assertThat(mapper.readValue(json, WindowsImageName.class).value()).isEqualTo("Windows Server 2025 SERVERSTANDARD");
    }
}
