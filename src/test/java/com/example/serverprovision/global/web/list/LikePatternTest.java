package com.example.serverprovision.global.web.list;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LikePatternTest {

    @Test
    @DisplayName("와일드카드 · 이스케이프 문자는 글자 그대로 찾도록 이스케이프하고 소문자로 내린다")
    void escapesWildcards() {
        assertThat(LikePattern.contains("A_b%C\\d")).isEqualTo("%a\\_b\\%c\\\\d%");
    }
}
