package com.example.serverprovision.execution.dto.request;

import com.example.serverprovision.global.web.list.vo.DateRange;
import com.example.serverprovision.execution.enums.GuestServerStatus;
import com.example.serverprovision.execution.enums.ServerListView;
import com.example.serverprovision.execution.enums.ServerSortField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class ServerListQueryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 11);


    private static ServerListQuery empty() {
        return new ServerListQuery(null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("아무 조건도 없으면 묶음 보기 · 등록 최신순 · 회수 제외 · 필터 없음")
    void defaults() {
        ServerListQuery q = empty();
        assertThat(q.view()).isEqualTo(ServerListView.GROUPED);
        assertThat(q.isTable()).isFalse();
        assertThat(q.sort()).isEqualTo(ServerSortField.CREATED_AT);
        assertThat(q.dir()).isEqualTo(Sort.Direction.DESC);
        assertThat(q.includeDecommissioned()).isFalse();
        assertThat(q.isFiltered(TODAY)).isFalse();
    }

    @Test
    @DisplayName("상태 집합에서 회수됨 · null 은 빠진다 — 회수 여부는 체크박스 한 축이 맡는다")
    void statusesDropDecommissionedAndNull() {
        ServerListQuery q = new ServerListQuery(null, null, null,
                new HashSet<>(Arrays.asList(null, GuestServerStatus.DECOMMISSIONED, GuestServerStatus.FAILED)),
                null, null, null, null, null, null);
        assertThat(q.statuses()).containsExactly(GuestServerStatus.FAILED);
    }

    @Test
    @DisplayName("공백 검색어는 조건이 아니고, 정렬 방향은 항목의 기본 방향 — 이름은 오름차순")
    void blankQueryAndFieldDefaultDirection() {
        ServerListQuery q = new ServerListQuery(ServerListView.TABLE, "  ", null, null, null, null, null, null, ServerSortField.NAME, null);
        assertThat(q.q()).isNull();
        assertThat(q.dir()).isEqualTo(Sort.Direction.ASC);
        assertThat(q.isTable()).isTrue();
        assertThat(q.isFiltered(TODAY)).isFalse();   // 보기 · 정렬은 조건이 아니다
    }

    @Test
    @DisplayName("기간 — 거꾸로인지 판정하고, 한쪽만 있어도 조건이다")
    void range() {
        LocalDate a = LocalDate.of(2026, 9, 1), b = LocalDate.of(2026, 9, 30);
        assertThat(new ServerListQuery(null, null, null, null, null, null, b, a, null, null).isRangeReversed()).isTrue();
        assertThat(new ServerListQuery(null, null, null, null, null, null, a, b, null, null).isRangeReversed()).isFalse();
        assertThat(new ServerListQuery(null, null, null, null, null, null, a, null, null, null).isFiltered(TODAY)).isTrue();
    }

    @Test
    @DisplayName("S8-4 — 비어 있는 기간 끝은 기본 범위(1 년 전 ~ 오늘)로 채워지고, 주어진 끝은 그대로다")
    void withDefaultRange_fillsOnlyEmptyEnds() {
        ServerListQuery filled = empty().withDefaultRange(TODAY);
        assertThat(filled.from()).isEqualTo(LocalDate.of(2025, 10, 11));
        assertThat(filled.to()).isEqualTo(TODAY);

        LocalDate a = LocalDate.of(2020, 1, 1);
        ServerListQuery half = new ServerListQuery(null, null, null, null, null, null, a, null, null, null).withDefaultRange(TODAY);
        assertThat(half.from()).isEqualTo(a);
        assertThat(half.to()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("S8-4 — 기본 범위와 같은 기간은 조건이 아니고, 한 끝이라도 다르면 조건이다")
    void defaultRangeIsNotAFilter() {
        LocalDate defaultFrom = DateRange.defaultFrom(TODAY);
        assertThat(new ServerListQuery(null, null, null, null, null, null, defaultFrom, TODAY, null, null).isFiltered(TODAY)).isFalse();
        assertThat(new ServerListQuery(null, null, null, null, null, null, defaultFrom, TODAY.minusDays(1), null, null).isFiltered(TODAY)).isTrue();
    }
}
