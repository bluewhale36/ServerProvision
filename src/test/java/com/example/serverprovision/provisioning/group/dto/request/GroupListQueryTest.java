package com.example.serverprovision.provisioning.group.dto.request;

import com.example.serverprovision.provisioning.group.enums.GroupSortField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class GroupListQueryTest {

    @Test
    @DisplayName("아무 조건도 없으면 만든 시각 최신순 · 필터 없음")
    void defaults() {
        GroupListQuery q = new GroupListQuery(" ", null, null, null);

        assertThat(q.q()).isNull();
        assertThat(q.sort()).isEqualTo(GroupSortField.CREATED_AT);
        assertThat(q.dir()).isEqualTo(Sort.Direction.DESC);
        assertThat(q.isFiltered()).isFalse();
    }

    @Test
    @DisplayName("정렬 항목 ↔ 엔티티 속성 · 기본 방향 전수 — 멤버 수는 @Formula 속성")
    void sortFieldMapping() {
        assertThat(Arrays.stream(GroupSortField.values()).map(f -> f.property() + ":" + f.defaultDirection()))
                .containsExactly("createdAt:DESC", "name:ASC", "memberCount:DESC");
        assertThat(new GroupListQuery(null, false, GroupSortField.NAME, null).isFiltered()).isTrue();
    }
}
