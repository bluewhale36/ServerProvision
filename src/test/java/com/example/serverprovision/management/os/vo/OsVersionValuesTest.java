package com.example.serverprovision.management.os.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** S23-1 — 버전 VO 두 가지: OS 메이저 버전과 Windows 빌드 번호. 읽기 규칙과 생성 가드. */
class OsVersionValuesTest {

    @Test
    @DisplayName("OsMajorVersion.parse: 첫 '.' 앞 숫자만 읽고, 숫자가 아니거나 0 이면 empty")
    void majorParse() {
        assertThat(OsMajorVersion.parse("9.6")).contains(OsMajorVersion.of(9));
        assertThat(OsMajorVersion.parse(" 10 ")).contains(OsMajorVersion.of(10));
        assertThat(OsMajorVersion.parse("10-beta")).isEmpty();
        assertThat(OsMajorVersion.parse("0.1")).isEmpty();
        assertThat(OsMajorVersion.parse("")).isEmpty();
        assertThat(OsMajorVersion.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("OsMajorVersion: leads 는 메이저로 시작하는 버전, isExactly 는 번호 그대로")
    void majorMatching() {
        OsMajorVersion nine = OsMajorVersion.of(9);
        assertThat(nine.leads("9.6")).isTrue();
        assertThat(nine.leads("10.0")).isFalse();
        assertThat(nine.isExactly("9")).isTrue();
        assertThat(nine.isExactly("9.6")).isFalse();
        assertThat(nine.isExactly(null)).isFalse();
        assertThat(nine).hasToString("9");
    }

    @Test
    @DisplayName("WindowsBuildNumber.parse: 세 번째 숫자를 읽고, 마디가 모자라거나 숫자가 아니면 empty")
    void buildParse() {
        assertThat(WindowsBuildNumber.parse("10.0.26100.1742")).contains(WindowsBuildNumber.of(26100));
        assertThat(WindowsBuildNumber.parse("10.0.20348")).contains(WindowsBuildNumber.of(20348));
        assertThat(WindowsBuildNumber.parse("10.0")).isEmpty();
        assertThat(WindowsBuildNumber.parse("10.0.x.1")).isEmpty();
        assertThat(WindowsBuildNumber.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("생성 가드: 0 이하 값은 만들 수 없다")
    void guards() {
        assertThatThrownBy(() -> OsMajorVersion.of(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WindowsBuildNumber.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
