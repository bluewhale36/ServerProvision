package com.example.serverprovision.provisioning.group.dto.request;

import com.example.serverprovision.provisioning.group.enums.GroupSortField;
import com.example.serverprovision.global.web.list.vo.DateRange;
import com.example.serverprovision.global.web.list.vo.DateRangeQuery;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 서버 그룹 목록의 조회 조건(S8-1) — 조회 띠(GET 폼)의 필드가 그대로 바인딩된다.
 * "전체" 칩의 빈 문자열은 {@code null} 이 되어 술어가 빠지고, 정렬 방향이 없으면 항목의 기본 방향을 쓴다.
 */
public record GroupListQuery(
        String q,
        Boolean hasStandardDefinition,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        GroupSortField sort,
        Sort.Direction dir
) implements DateRangeQuery {

    public static final GroupSortField DEFAULT_SORT = GroupSortField.CREATED_AT;

    public GroupListQuery {
        q = (q == null || q.isBlank()) ? null : q.trim();
        sort = sort == null ? DEFAULT_SORT : sort;
        dir = dir == null ? sort.defaultDirection() : dir;
    }

    /** 비어 있는 생성 일시 기간 끝을 기본 범위(1 년 전 ~ 오늘)로 채운 조건(S8-5) — 조회와 화면은 이 값을 쓴다. */
    public GroupListQuery withDefaultRange(LocalDate today) {
        DateRange r = range().withDefault(today);
        return new GroupListQuery(q, hasStandardDefinition, r.from(), r.to(), sort, dir);
    }

    /**
     * 조건이 하나라도 걸려 있는가 — 빈 결과의 안내 갈래와 초기화 링크 노출이 이것을 본다.
     * 기본 범위와 같은 기간은 조건이 아니다(S8-5).
     */
    public boolean isFiltered(LocalDate today) {
        return q != null || hasStandardDefinition != null || !range().isDefault(today);
    }
}
