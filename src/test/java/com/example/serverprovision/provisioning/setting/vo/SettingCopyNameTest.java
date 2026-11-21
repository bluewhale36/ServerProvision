package com.example.serverprovision.provisioning.setting.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingCopyNameTest {

    @Test
    @DisplayName("첫 후보는 '원본 복사본', 2 번부터 '원본 복사본 n'")
    void candidates() {
        SettingCopyName name = new SettingCopyName("RHEL 9.4 기본");

        assertThat(name.candidate(1)).isEqualTo("RHEL 9.4 기본 복사본");
        assertThat(name.candidate(2)).isEqualTo("RHEL 9.4 기본 복사본 2");
        assertThat(name.candidate(10)).isEqualTo("RHEL 9.4 기본 복사본 10");
    }

    @Test
    @DisplayName("원본 이름의 앞뒤 공백은 지운다")
    void stripsSourceName() {
        assertThat(new SettingCopyName("  표준 세팅  ").candidate(1)).isEqualTo("표준 세팅 복사본");
    }

    @Test
    @DisplayName("128 자를 넘으면 원본 이름 쪽을 잘라 맞추고, 자른 끝의 공백은 지운다")
    void fitsMaxLength() {
        String longName = "a".repeat(123) + " bcd";   // 127 자

        String first = new SettingCopyName(longName).candidate(1);
        String second = new SettingCopyName(longName).candidate(2);

        assertThat(first).hasSize(SettingCopyName.MAX_LENGTH - 1).isEqualTo("a".repeat(123) + " 복사본");
        assertThat(second).endsWith(" 복사본 2").hasSizeLessThanOrEqualTo(SettingCopyName.MAX_LENGTH);
        assertThat(second).doesNotContain("  ");
    }

    @Test
    @DisplayName("자르는 자리가 서로게이트 쌍 가운데면 한 칸 앞에서 자른다(깨진 반쪽을 남기지 않음)")
    void doesNotSplitSurrogatePair() {
        String emoji = "\uD83D\uDE00";                       // 2 단위 문자 하나
        String name = "a".repeat(123) + emoji + "zz";         // 잘리는 자리(124)가 이모지 가운데

        String fitted = new SettingCopyName(name).candidate(1);

        assertThat(fitted).isEqualTo("a".repeat(123) + " 복사본");
        assertThat(fitted.chars().noneMatch(c -> Character.isSurrogate((char) c))).isTrue();
    }

    @Test
    @DisplayName("후보 번호는 1 이상")
    void rejectsNonPositive() {
        assertThatThrownBy(() -> new SettingCopyName("x").candidate(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
