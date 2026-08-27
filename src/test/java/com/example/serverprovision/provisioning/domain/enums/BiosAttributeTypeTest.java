package com.example.serverprovision.provisioning.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 레지스트리 {@code Type} → 상수 매핑 계약. MD72-HB3 의 String 속성(MAPIDS)이 로드 실패(500)를 내던
 * 실기 결함(2026-08-27)의 회귀 방지.
 */
class BiosAttributeTypeTest {

    @Test
    @DisplayName("String 타입 — 로드는 통과하되 템플릿 배제(PASSWORD 와 같은 구조적 차단), 값은 문자열 그대로")
    void string_isLoadableButNotTemplatable() {
        BiosAttributeType type = BiosAttributeType.from("String");

        assertThat(type).isEqualTo(BiosAttributeType.STRING);
        assertThat(type.templatable()).isFalse();
        assertThat(type.coerce(null, "0x1A,0x2B").jsonValue()).isEqualTo("0x1A,0x2B");
    }

    @Test
    @DisplayName("미지원 타입은 명시 예외 — 파서가 로드 실패로 래핑한다")
    void unknown_throws() {
        assertThatThrownBy(() -> BiosAttributeType.from("Map"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
