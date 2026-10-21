package com.example.serverprovision.management.raidcard.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrivesPerSpanTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 3, 34, -2})
    @DisplayName("스팬당 디스크는 2~32 짝수만 — 홀수 · 범위 밖은 생성자가 거절")
    void rejectsOddOrOutOfRange(int value) {
        assertThatThrownBy(() -> new DrivesPerSpan(value)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("선택지 = 2 · 4 · … · 32(16개) · 기본 2 · storcli 토큰 · 스팬 수")
    void optionsTokenAndSpans() {
        assertThat(DrivesPerSpan.options()).hasSize(16)
                .first().isEqualTo(DrivesPerSpan.DEFAULT);
        assertThat(DrivesPerSpan.options().get(15).value()).isEqualTo(32);
        assertThat(new DrivesPerSpan(4).cliToken()).isEqualTo("pdperarray=4");
        assertThat(new DrivesPerSpan(2).spansOf(6)).isEqualTo(3);
        assertThat(new DrivesPerSpan(4).toDisplay()).isEqualTo("스팬당 4대");
    }

    @Test
    @DisplayName("JSON 은 숫자 하나로 오간다 — 정의서 저장본 · 동결 계획의 형태")
    void jsonIsBareNumber() {
        ObjectMapper mapper = JsonMapper.builder().build();
        assertThat(mapper.writeValueAsString(new DrivesPerSpan(6))).isEqualTo("6");
        assertThat(mapper.readValue("8", DrivesPerSpan.class)).isEqualTo(new DrivesPerSpan(8));
    }
}
