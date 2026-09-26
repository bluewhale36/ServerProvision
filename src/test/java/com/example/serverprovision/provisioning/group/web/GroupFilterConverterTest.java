package com.example.serverprovision.provisioning.group.web;

import com.example.serverprovision.provisioning.group.vo.GroupFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupFilterConverterTest {

    private final GroupFilterConverter converter = new GroupFilterConverter();

    @Test
    @DisplayName("빈 값은 조건 없음 · none 은 무소속 · 숫자는 그 그룹 — 셀렉트 표기와 왕복한다")
    void converts() {
        assertThat(converter.convert("")).isEqualTo(GroupFilter.ANY);
        assertThat(converter.convert("none")).isEqualTo(GroupFilter.UNGROUPED);
        assertThat(converter.convert(" 7 ")).isEqualTo(GroupFilter.of(7L));
        assertThat(GroupFilter.of(7L).param()).isEqualTo("7");
        assertThat(GroupFilter.UNGROUPED.param()).isEqualTo("none");
        assertThat(GroupFilter.ANY.isSet()).isFalse();
    }

    @Test
    @DisplayName("그 밖의 값은 변환 실패 — 바인딩 400 으로 이어진다")
    void rejectsOthers() {
        assertThatThrownBy(() -> converter.convert("abc")).isInstanceOf(NumberFormatException.class);
    }
}
