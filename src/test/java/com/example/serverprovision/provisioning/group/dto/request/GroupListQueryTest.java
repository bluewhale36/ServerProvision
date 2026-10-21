package com.example.serverprovision.provisioning.group.dto.request;

import com.example.serverprovision.support.FixedClockTestConfig;
import com.example.serverprovision.provisioning.group.enums.GroupSortField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class GroupListQueryTest {

    @Test
    @DisplayName("아무 조건도 없으면 생성 일시 최신순 · 필터 없음")
    void defaults() {
        GroupListQuery q = new GroupListQuery(" ", null, null, null, null, null);

        assertThat(q.q()).isNull();
        assertThat(q.sort()).isEqualTo(GroupSortField.CREATED_AT);
        assertThat(q.dir()).isEqualTo(Sort.Direction.DESC);
        assertThat(q.isFiltered(FixedClockTestConfig.TODAY)).isFalse();
    }

    @Test
    @DisplayName("정렬 항목 ↔ 엔티티 속성 · 기본 방향 전수 — 멤버 수는 @Formula 속성")
    void sortFieldMapping() {
        assertThat(Arrays.stream(GroupSortField.values()).map(f -> f.property() + ":" + f.defaultDirection()))
                .containsExactly("createdAt:DESC", "name:ASC", "memberCount:DESC");
        assertThat(new GroupListQuery(null, false, null, null, GroupSortField.NAME, null).isFiltered(FixedClockTestConfig.TODAY)).isTrue();
    }

    @Test
    @DisplayName("S8-5 — 생성 일시 기본 범위로 채운 조건은 다른 축을 그대로 두고, 한 끝이라도 다르면 조건이다")
    void withDefaultRange_keepsOtherAxes() {
        GroupListQuery q = new GroupListQuery("8월", false, null, null, null, null).withDefaultRange(FixedClockTestConfig.TODAY);
        assertThat(q.q()).isEqualTo("8월");
        assertThat(q.hasStandardDefinition()).isFalse();
        assertThat(q.from()).isEqualTo(java.time.LocalDate.of(2025, 10, 11));
        assertThat(new GroupListQuery(null, null, java.time.LocalDate.of(2025, 1, 1), null, null, null)
                .isFiltered(FixedClockTestConfig.TODAY)).isTrue();
    }

}
