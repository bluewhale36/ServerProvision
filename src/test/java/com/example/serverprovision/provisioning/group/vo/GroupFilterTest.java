package com.example.serverprovision.provisioning.group.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GroupFilterTest {

    @Test
    @DisplayName("S8-6 — 반복 파라미터를 하나로 합친다: 무소속 · 그룹 id 를 모으고 빈 값(ANY) · null 은 더하지 않는다")
    void merge() {
        GroupFilter merged = GroupFilter.merge(Arrays.asList(GroupFilter.ANY, GroupFilter.UNGROUPED, GroupFilter.of(7L), null, GroupFilter.of(8L)));
        assertThat(merged.ungrouped()).isTrue();
        assertThat(merged.groupIds()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(GroupFilter.merge(null)).isEqualTo(GroupFilter.ANY);
        assertThat(GroupFilter.merge(List.of())).isEqualTo(GroupFilter.ANY);
        assertThat(GroupFilter.merge(List.of(GroupFilter.ANY)).isSet()).isFalse();
    }

    @Test
    @DisplayName("S8-6 — 실재하는 그룹만 남기고 무소속 여부는 그대로 · 남은 것이 없으면 조건이 아니다")
    void retainGroups() {
        GroupFilter f = new GroupFilter(false, Set.of(7L, 99L));
        assertThat(f.retainGroups(List.of(7L)).groupIds()).containsExactly(7L);
        assertThat(f.retainGroups(List.of()).isSet()).isFalse();
        assertThat(new GroupFilter(true, Set.of(99L)).retainGroups(List.of()).isSet()).isTrue();
    }
}
